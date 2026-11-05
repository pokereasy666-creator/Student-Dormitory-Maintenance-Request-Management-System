"""
数据库初始化：每次启动时执行，可重复执行，不会覆盖已有数据。
  1. MySQL 中数据库不存在则自动创建（utf8mb4）；
  2. 数据表不存在则自动创建；
  3. 预置维修人员账号（不开放注册）和一个演示学生账号，已存在则跳过。
连接失败时把英文错误翻译成中文原因，方便排查。
"""
import logging

from sqlalchemy.engine import make_url
from sqlalchemy.exc import IntegrityError
from werkzeug.security import generate_password_hash

from .constants import Role
from .extensions import db
from .models import User

log = logging.getLogger(__name__)

# 预置账号：(账号, 姓名, 角色, 手机号, 楼栋, 宿舍号)，初始密码均为 123456
# 新增维修人员：在这里加一行，重启后生效
SEED_USERS = [
    ("W001", "张师傅", Role.REPAIRER, "13800000001", None, None),
    ("W002", "李师傅", Role.REPAIRER, "13800000002", None, None),
    ("W003", "王师傅", Role.REPAIRER, "13800000003", None, None),
    # 演示用学生账号，答辩时可直接登录；不需要可删除此行
    ("2023001", "演示学生", Role.STUDENT, "13900000001", "1号楼", "101"),
]
SEED_PASSWORD = "123456"


class DatabaseInitError(Exception):
    """数据库初始化失败，message 为中文原因。"""


def init_database(app) -> None:
    url = make_url(app.config["SQLALCHEMY_DATABASE_URI"])
    try:
        if url.get_backend_name() == "mysql":
            _create_mysql_database(url)
        db.create_all()  # 只创建不存在的表
        _seed_users()
    except DatabaseInitError:
        raise
    except Exception as e:  # noqa: BLE001 —— 任何数据库错误都转换成中文提示
        db.session.rollback()
        raise DatabaseInitError(describe_db_error(e, url)) from e


def _create_mysql_database(url) -> None:
    """连接 MySQL 服务器（不指定数据库），执行 CREATE DATABASE IF NOT EXISTS。"""
    import pymysql

    name = url.database
    params = dict(host=url.host or "localhost", port=url.port or 3306, user=url.username,
                  password=url.password or "", charset="utf8mb4", connect_timeout=5)
    conn = pymysql.connect(**params)
    try:
        with conn.cursor() as cur:
            cur.execute(f"CREATE DATABASE IF NOT EXISTS `{name}` "
                        "DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    except pymysql.err.OperationalError as e:
        # 没有建库权限（1044）：如果数据库已经由管理员建好，可以继续使用
        if e.args and e.args[0] in (1044, 1142):
            pymysql.connect(database=name, **params).close()
        else:
            raise
    finally:
        conn.close()


def _seed_users() -> None:
    for username, real_name, role, phone, building, room_no in SEED_USERS:
        if db.session.query(User.id).filter_by(username=username).first():
            continue
        db.session.add(User(username=username, password=generate_password_hash(SEED_PASSWORD),
                            real_name=real_name, role=role, phone=phone,
                            building=building, room_no=room_no))
    try:
        db.session.commit()
    except IntegrityError:
        # 两个进程同时初始化（如开发模式自动重启）时可能重复插入，忽略即可
        db.session.rollback()


def describe_db_error(error: Exception, url) -> str:
    """把数据库异常翻译成中文原因和解决办法。"""
    orig = getattr(error, "orig", None) or error
    code = orig.args[0] if getattr(orig, "args", None) and isinstance(orig.args[0], int) else None
    text = str(orig)
    host = f"{url.host or 'localhost'}:{url.port or 3306}"
    if code in (1045, 1698):  # 1698：该账号不允许用密码登录（如 Linux 上的 root）
        return (f"MySQL 用户名或密码错误（用户 {url.username}）。"
                "请修改 config.ini 中 [database] 的 user 和 password。")
    if code in (2002, 2003, 2006, 2013):
        return (f"无法连接 MySQL（{host}）。请确认 MySQL 服务已启动，"
                "并检查 config.ini 中 [database] 的 host 和 port。")
    if code in (1044, 1049, 1142):
        return (f"当前 MySQL 账号没有创建数据库 {url.database} 的权限。请改用 root 账号，"
                f"或先手动执行：CREATE DATABASE {url.database} DEFAULT CHARSET utf8mb4;")
    if "cryptography" in text:
        return ("缺少 cryptography 组件（MySQL 8 默认的登录认证方式需要它）。"
                "请重新运行 setup.bat 安装依赖。")
    return f"数据库初始化失败：{text}"
