"""
学生端：首页公告、提交报修、我的报修、工单评价。
所有 /student/... 请求已由 security.py 保证是学生本人登录。
"""
from flask import Blueprint, flash, redirect, render_template, request, url_for

from .. import security
from ..constants import OrderStatus, RepairCategory, parse_enum
from ..errors import BusinessError
from ..forms import OrderForm, RateForm, first_error
from ..services import order_service, user_service

bp = Blueprint("student", __name__, url_prefix="/student")


@bp.get("/home")
def home():
    """学生首页：公告（写死在 templates/student/home.html 中）+ 我的工单统计。"""
    counts = order_service.count_for_student(security.current_user().id)
    return render_template("student/home.html", counts=counts)


@bp.get("/orders/new")
def new_order():
    """报修表单：楼栋、宿舍号默认带出学生注册时填写的信息，可以修改。"""
    user = user_service.get_user(security.current_user().id)
    form = OrderForm(data={"building": user.building, "room_no": user.room_no})
    return render_template("student/order_form.html", form=form)


@bp.post("/orders")
def submit_order():
    """提交报修（multipart 表单，image 为可选图片）。"""
    form = OrderForm()
    if not form.validate_on_submit():
        return render_template("student/order_form.html", form=form)
    try:
        order_id = order_service.submit(
            security.current_user().id, form.building.data, form.room_no.data,
            RepairCategory[form.category.data], form.description.data, request.files.get("image"))
    except BusinessError as e:
        return render_template("student/order_form.html", form=form, error=e.message)
    flash(f"报修已提交（工单号 #{order_id}），请等待维修人员接单", "success")
    return redirect(url_for("student.order_detail", order_id=order_id))


@bp.get("/orders")
def my_orders():
    """我的报修列表；status 参数为空或不合法时显示全部。"""
    status = parse_enum(OrderStatus, request.args.get("status"))
    orders = order_service.list_for_student(security.current_user().id, status)
    return render_template("student/orders.html", orders=orders, current_status=status)


@bp.get("/orders/<int:order_id>")
def order_detail(order_id):
    """工单详情（进度、维修人员、维修备注、评价）；不是自己的工单会抛 BusinessError 显示错误页。"""
    order = order_service.get_for_student(order_id, security.current_user().id)
    return render_template("student/order_detail.html", order=order, rate_form=RateForm())


@bp.post("/orders/<int:order_id>/rate")
def rate(order_id):
    """提交评价：星级 + 简短文字。"""
    form = RateForm()
    if not form.validate_on_submit():
        flash(first_error(form), "error")
    else:
        try:
            order_service.rate(order_id, security.current_user().id, form.rating.data, form.evaluation.data)
            flash("评价成功，感谢您的反馈", "success")
        except BusinessError as e:
            flash(e.message, "error")
    return redirect(url_for("student.order_detail", order_id=order_id))
