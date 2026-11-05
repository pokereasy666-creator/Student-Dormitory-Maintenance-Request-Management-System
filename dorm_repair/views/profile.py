"""个人中心：修改个人信息、修改密码（学生和维修人员共用）。"""
from flask import Blueprint, flash, redirect, render_template, request, url_for

from .. import security
from ..errors import BusinessError
from ..forms import PasswordForm, ProfileForm
from ..services import user_service

bp = Blueprint("profile", __name__, url_prefix="/profile")


@bp.route("", methods=["GET", "POST"])
def profile():
    login_user = security.current_user()
    user = user_service.get_user(login_user.id)
    # GET 时用数据库中的值填充表单；POST 时使用提交的值
    form = ProfileForm(obj=user if request.method == "GET" else None)
    error = None
    if form.validate_on_submit():
        try:
            updated = user_service.update_profile(user.id, form.real_name.data, form.phone.data,
                                                  form.building.data, form.room_no.data)
            # 同步 session 中的姓名，导航栏立即显示新名字
            login_user.real_name = updated.real_name
            security.login(login_user)
            flash("个人信息已保存", "success")
            return redirect(url_for("profile.profile"))
        except BusinessError as e:
            error = e.message
    return render_template("profile.html", form=form, user=user, error=error)


@bp.route("/password", methods=["GET", "POST"])
def password():
    """修改密码成功后让当前登录失效，要求用新密码重新登录。"""
    form = PasswordForm()
    error = None
    if form.validate_on_submit():
        try:
            user_service.change_password(security.current_user().id,
                                         form.old_password.data, form.new_password.data)
            security.logout()
            flash("密码修改成功，请使用新密码重新登录", "success")
            return redirect(url_for("auth.login"))
        except BusinessError as e:
            error = e.message
    return render_template("password.html", form=form, error=error)
