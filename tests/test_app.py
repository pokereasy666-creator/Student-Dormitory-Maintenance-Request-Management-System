"""
集成测试：启动完整应用 + 数据库，通过测试客户端模拟浏览器请求，
覆盖完整业务闭环、角色隔离、越权访问、重复接单 / 重复评价、CSRF、上传校验等关键规则。

各测试共用同一个数据库，所以每个测试使用不同的学号，互不干扰。
"""
import io
import re
import threading

from dorm_repair.constants import OrderStatus, Role
from dorm_repair.errors import BusinessError
from dorm_repair.extensions import db
from dorm_repair.models import RepairOrder, User
from dorm_repair.services import order_service

REPAIRER_PWD = "123456"
# 一个最小的"PNG 文件"：8 字节 PNG 文件头 + 若干字节，足以通过文件头校验
PNG_BYTES = b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR"
ORDER_FIELDS = {"building": "1号楼", "room_no": "101", "category": "WATER_ELECTRIC"}


# ============================ 辅助函数 ============================

def register(browser, student_no, password="abc123", **extra):
    b = browser()
    data = {"username": student_no, "real_name": f"学生{student_no}", "password": password,
            "confirm_password": password, "building": "1号楼", "room_no": "101", "phone": "13900001111"}
    data.update(extra)
    return b.post("/register", data)


def login(browser, username, password):
    b = browser()
    resp = b.post("/login", {"username": username, "password": password})
    assert resp.status_code == 302 and resp.headers["Location"] == "/", resp.get_data(as_text=True)
    return b


def new_student(browser, student_no, password="abc123"):
    assert register(browser, student_no, password).headers["Location"] == "/login"
    return login(browser, student_no, password)


def submit_order(b, description, image=None):
    data = dict(ORDER_FIELDS, description=description)
    if image is not None:
        data["image"] = image
    resp = b.post("/student/orders", data, content_type="multipart/form-data")
    assert resp.status_code == 302, resp.get_data(as_text=True)
    location = resp.headers["Location"]
    assert location.startswith("/student/orders/")
    return int(location.rsplit("/", 1)[1])


def text(resp):
    return resp.get_data(as_text=True)


def get_order(app, order_id):
    with app.app_context():
        order = db.session.get(RepairOrder, order_id)
        db.session.expunge(order)
        return order


def user_id(app, username):
    with app.app_context():
        return db.session.query(User.id).filter_by(username=username).scalar()


# ============================ 测试用例 ============================

def test_full_workflow(app, browser):
    stu = new_student(browser, "S1001")
    assert "报修须知" in text(stu.get("/student/home"))

    # 1. 学生提交报修（带图片）
    image = (io.BytesIO(PNG_BYTES), "leak.png")
    order_id = submit_order(stu, "卫生间水龙头一直滴水", image)
    order = get_order(app, order_id)
    assert order.status == OrderStatus.PENDING and order.image_path
    assert "待接单" in text(stu.get(f"/student/orders/{order_id}"))
    # 图片：登录后可访问，未登录被拦截
    assert stu.get(f"/uploads/{order.image_path}").status_code == 200
    assert browser().get(f"/uploads/{order.image_path}").headers["Location"] == "/login"

    # 2. 维修人员在待接单池中看到该工单
    w1 = login(browser, "W001", REPAIRER_PWD)
    w2 = login(browser, "W002", REPAIRER_PWD)
    assert f"#{order_id}" in text(w1.get("/repair/pending"))

    # 未完成时学生不能评价
    assert "维修完成后才能评价" in text(stu.post(f"/student/orders/{order_id}/rate", {"rating": "5"}, follow=True))

    # 3. W001 接单成功；W002 再接同一单失败
    resp = w1.post(f"/repair/orders/{order_id}/accept")
    assert resp.headers["Location"] == f"/repair/orders/{order_id}"
    assert "接单成功" in text(w1.get(resp.headers["Location"]))
    assert "已被其他维修人员接单" in text(w2.post(f"/repair/orders/{order_id}/accept", follow=True))

    # 已被 W001 接走的单，W002 看不到详情，也不能完成
    assert w2.get(f"/repair/orders/{order_id}").status_code == 400
    assert "只能完成自己接单" in text(w2.post(f"/repair/orders/{order_id}/complete", {"repair_note": "x"}, follow=True))

    # 4. W001 完成维修：备注必填
    assert "请填写维修备注" in text(w1.post(f"/repair/orders/{order_id}/complete", {"repair_note": " "}, follow=True))
    assert "已标记为完成" in text(w1.post(f"/repair/orders/{order_id}/complete", {"repair_note": "更换阀芯"}, follow=True))

    # 5. 学生评价，且只能评价一次
    resp = stu.post(f"/student/orders/{order_id}/rate", {"rating": "5", "evaluation": "师傅很快就修好了"}, follow=True)
    assert "评价成功" in text(resp)
    assert "不能重复评价" in text(stu.post(f"/student/orders/{order_id}/rate", {"rating": "1"}, follow=True))

    done = get_order(app, order_id)
    assert done.status == OrderStatus.COMPLETED
    assert done.repairer_id == user_id(app, "W001")
    assert done.repair_note == "更换阀芯"
    assert done.rating == 5 and done.evaluation == "师傅很快就修好了"

    # 维修人员历史记录、学生按状态筛选
    assert f"#{order_id}" in text(w1.get("/repair/mine?status=COMPLETED"))
    assert f"#{order_id}" in text(stu.get("/student/orders?status=COMPLETED"))
    assert f"#{order_id}" not in text(stu.get("/student/orders?status=PENDING"))
    # 不合法的状态参数按"全部"处理
    assert f"#{order_id}" in text(stu.get("/student/orders?status=XYZ"))


def test_role_isolation(app, browser):
    anon = browser()
    for url in ("/student/home", "/repair/pending", "/profile", "/"):
        assert anon.get(url).headers["Location"] == "/login"

    stu = new_student(browser, "S2001")
    w1 = login(browser, "W001", REPAIRER_PWD)

    # 学生不能访问维修人员功能（看不到工单池、不能接单）
    assert stu.get("/repair/pending").status_code == 403
    assert stu.post("/repair/orders/1/accept").status_code == 403
    # 维修人员不能提交报修
    assert w1.get("/student/orders/new").status_code == 403
    resp = w1.post("/student/orders", dict(ORDER_FIELDS, description="伪造报修"), content_type="multipart/form-data")
    assert resp.status_code == 403

    # 登录后访问根路径，按角色进入各自首页
    assert stu.get("/").headers["Location"] == "/student/home"
    assert w1.get("/").headers["Location"] == "/repair/pending"


def test_student_cannot_view_or_rate_others_order(app, browser):
    a = new_student(browser, "S3001")
    b = new_student(browser, "S3002")
    order_id = submit_order(a, "A 同学的私密描述")

    resp = b.get(f"/student/orders/{order_id}")
    assert resp.status_code == 400 and "A 同学的私密描述" not in text(resp)
    assert f"#{order_id}" not in text(b.get("/student/orders"))
    assert "无权查看" in text(b.post(f"/student/orders/{order_id}/rate", {"rating": "1"}, follow=True))


def test_register_validation_and_role_cannot_be_forged(app, browser):
    # 伪造 role=REPAIRER 参数：注册出来仍然是学生；密码以哈希形式保存
    assert register(browser, "S4001", role="REPAIRER").headers["Location"] == "/login"
    with app.app_context():
        user = db.session.query(User).filter_by(username="S4001").one()
        assert user.role == Role.STUDENT
        assert user.password != "abc123" and user.password.startswith("scrypt:")

    assert "该学号已注册" in text(register(browser, "S4001"))
    assert "请输入 11 位手机号" in text(register(browser, "S4002", phone="12345"))
    assert "两次输入的密码不一致" in text(register(browser, "S4003", confirm_password="abc999"))
    assert "请选择正确的楼栋" in text(register(browser, "S4004", building="99号楼"))
    assert "学号为 4~20 位字母或数字" in text(register(browser, "S#1"))
    with app.app_context():
        assert db.session.query(User).filter(User.username.in_(["S4002", "S4003", "S4004"])).count() == 0


def test_login_wrong_password(browser):
    assert "账号或密码错误" in text(browser().post("/login", {"username": "W001", "password": "wrong"}))
    assert "账号或密码错误" in text(browser().post("/login", {"username": "nobody", "password": "whatever"}))


def test_upload_validation(app, browser):
    stu = new_student(browser, "S5001")
    sid = user_id(app, "S5001")

    def post_with(image):
        data = dict(ORDER_FIELDS, description="窗户关不上", image=image)
        return stu.post("/student/orders", data, content_type="multipart/form-data")

    # 扩展名是 .jpg，但内容其实是文本
    assert "文件内容不是有效的图片" in text(post_with((io.BytesIO(b"<script>alert(1)</script>"), "fake.jpg")))
    # 不允许的扩展名
    assert "仅支持" in text(post_with((io.BytesIO(PNG_BYTES), "a.exe")))
    # 超过 5MB 但未超过请求上限 6MB：由业务层拒绝
    assert "图片大小不能超过 5MB" in text(post_with((io.BytesIO(PNG_BYTES + b"0" * (5 * 1024 * 1024)), "big.png")))
    # 超过请求上限 6MB：返回报修表单并提示
    resp = post_with((io.BytesIO(PNG_BYTES + b"0" * (7 * 1024 * 1024)), "huge.png"))
    assert resp.headers["Location"] == "/student/orders/new"
    assert "图片大小不能超过 5MB" in text(stu.get("/student/orders/new"))

    with app.app_context():
        assert db.session.query(RepairOrder).filter_by(student_id=sid).count() == 0


def test_submit_order_validation(browser):
    stu = new_student(browser, "S5101")
    resp = stu.post("/student/orders", {"building": "1号楼", "room_no": "101",
                                        "category": "NOT_A_CATEGORY", "description": ""},
                    content_type="multipart/form-data")
    body = text(resp)
    assert resp.status_code == 200
    assert "请选择正确的报修类型" in body and "请填写故障描述" in body


def test_change_password(browser):
    stu = new_student(browser, "S6001")
    data = {"old_password": "wrong1", "new_password": "new123", "confirm_password": "new123"}
    assert "原密码错误" in text(stu.post("/profile/password", data))

    data["old_password"] = "abc123"
    resp = stu.post("/profile/password", data, follow=True)
    assert "请使用新密码重新登录" in text(resp)
    # 修改后当前登录失效
    assert stu.get("/student/home").headers["Location"] == "/login"
    assert "账号或密码错误" in text(browser().post("/login", {"username": "S6001", "password": "abc123"}))
    login(browser, "S6001", "new123")


def test_update_profile(app, browser):
    stu = new_student(browser, "S7001")
    resp = stu.post("/profile", {"real_name": "新名字", "phone": "13700000000", "building": "3号楼", "room_no": "303"})
    assert resp.headers["Location"] == "/profile"
    with app.app_context():
        user = db.session.query(User).filter_by(username="S7001").one()
        assert (user.real_name, user.building, user.room_no) == ("新名字", "3号楼", "303")
    # 导航栏显示新名字；报修表单默认带出新的宿舍
    assert "新名字" in text(stu.get("/student/home"))
    assert 'value="303"' in text(stu.get("/student/orders/new"))


def test_logout(browser):
    w1 = login(browser, "W001", REPAIRER_PWD)
    assert w1.post("/logout").headers["Location"] == "/login"
    assert w1.get("/repair/pending").headers["Location"] == "/login"


def test_csrf_protection(browser):
    """不带 csrf_token 的 POST 被拒绝（防止其他网站伪造请求）。"""
    w1 = login(browser, "W001", REPAIRER_PWD)
    assert w1.post("/logout", csrf=False).status_code == 400
    assert browser().post("/login", {"username": "W001", "password": REPAIRER_PWD}, csrf=False).status_code == 400


def test_every_post_form_has_csrf_token(app, browser):
    """检查各页面：每个 POST 表单都带有 csrf_token 隐藏字段。"""
    stu = new_student(browser, "S8101")
    order_id = submit_order(stu, "检查表单")
    w1 = login(browser, "W001", REPAIRER_PWD)
    pages = [(browser(), "/login"), (browser(), "/register"), (stu, "/student/home"), (stu, "/student/orders/new"),
             (stu, "/profile"), (stu, "/profile/password"), (w1, "/repair/pending"), (w1, f"/repair/orders/{order_id}")]
    for client, url in pages:
        body = text(client.get(url))
        forms = re.findall(r"<form[^>]*method=\"post\"[^>]*>.*?</form>", body, flags=re.S)
        assert forms, url
        for form in forms:
            assert 'name="csrf_token"' in form, f"{url} 中有表单缺少 csrf_token"


def test_concurrent_accept_only_one_succeeds(app, browser):
    """12 个线程同时接同一单，只有 1 个成功：验证带状态条件的 UPDATE。"""
    stu = new_student(browser, "S8001")
    order_id = submit_order(stu, "并发测试")
    repairer_ids = [user_id(app, u) for u in ("W001", "W002", "W003")]

    start = threading.Event()
    results = []
    lock = threading.Lock()

    def worker(rid):
        start.wait()
        with app.app_context():
            try:
                order_service.accept(order_id, rid)
                outcome = "ok"
            except BusinessError:
                outcome = "rejected"
        with lock:
            results.append(outcome)

    threads = [threading.Thread(target=worker, args=(repairer_ids[i % 3],)) for i in range(12)]
    for t in threads:
        t.start()
    start.set()
    for t in threads:
        t.join(timeout=30)

    assert results.count("ok") == 1 and results.count("rejected") == 11
    assert get_order(app, order_id).status == OrderStatus.IN_PROGRESS
