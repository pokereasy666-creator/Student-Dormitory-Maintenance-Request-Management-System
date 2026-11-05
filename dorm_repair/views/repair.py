"""
维修人员端：待接单列表、接单、完成维修、我的维修记录。
所有 /repair/... 请求已由 security.py 保证是维修人员登录。
"""
from flask import Blueprint, flash, redirect, render_template, request, url_for

from .. import security
from ..constants import OrderStatus, parse_enum
from ..errors import BusinessError
from ..forms import CompleteForm, first_error
from ..services import order_service

bp = Blueprint("repair", __name__, url_prefix="/repair")


@bp.get("/pending")
def pending():
    """待接单池。"""
    return render_template("repair/pending.html", orders=order_service.list_pending())


@bp.post("/orders/<int:order_id>/accept")
def accept(order_id):
    """接单：成功后进入工单详情，失败（已被别人接走）回到待接单列表。"""
    try:
        order_service.accept(order_id, security.current_user().id)
    except BusinessError as e:
        flash(e.message, "error")
        return redirect(url_for("repair.pending"))
    flash("接单成功，请尽快上门维修", "success")
    return redirect(url_for("repair.order_detail", order_id=order_id))


@bp.post("/orders/<int:order_id>/complete")
def complete(order_id):
    """维修完成：填写维修备注并标记已完成。"""
    form = CompleteForm()
    if not form.validate_on_submit():
        flash(first_error(form), "error")
    else:
        try:
            order_service.complete(order_id, security.current_user().id, form.repair_note.data)
            flash("工单已标记为完成", "success")
        except BusinessError as e:
            flash(e.message, "error")
    return redirect(url_for("repair.order_detail", order_id=order_id))


@bp.get("/mine")
def mine():
    """我的维修记录（维修中 + 已完成）；status 为空表示全部。"""
    status = parse_enum(OrderStatus, request.args.get("status"))
    orders = order_service.list_for_repairer(security.current_user().id, status)
    return render_template("repair/mine.html", orders=orders, current_status=status)


@bp.get("/orders/<int:order_id>")
def order_detail(order_id):
    """工单详情：待接单的可看（决定是否接），自己接的可看，别人的不可看。"""
    order = order_service.get_for_repairer(order_id, security.current_user().id)
    return render_template("repair/order_detail.html", order=order, complete_form=CompleteForm())
