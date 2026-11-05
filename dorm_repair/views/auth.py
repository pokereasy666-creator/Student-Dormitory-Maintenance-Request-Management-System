"""登录、注册、退出，以及登录后按角色跳转首页。"""
from flask import Blueprint, flash, redirect, render_template, url_for

from .. import security
from ..errors import BusinessError
from ..forms import LoginForm, RegisterForm
from ..services import user_service

bp = Blueprint("auth", __name__)


@bp.get("/")
def home():
    """根路径：学生进入学生首页，维修人员进入待接单列表（未登录会被 security 送去登录页）。"""
    user = security.current_user()
    return redirect(url_for("student.home") if user.is_student else url_for("repair.pending"))


@bp.route("/login", methods=["GET", "POST"])
def login():
    if security.current_user() is not None:
        return redirect(url_for("auth.home"))
    form = LoginForm()
    error = None
    if form.validate_on_submit():
        try:
            security.login(user_service.authenticate(form.username.data, form.password.data))
            return redirect(url_for("auth.home"))
        except BusinessError as e:
            error = e.message
    return render_template("login.html", form=form, error=error)


@bp.route("/register", methods=["GET", "POST"])
def register():
    """学生注册；成功后跳到登录页。"""
    form = RegisterForm()
    error = None
    if form.validate_on_submit():
        try:
            user_service.register(form.username.data, form.real_name.data, form.password.data,
                                  form.building.data, form.room_no.data, form.phone.data)
            flash("注册成功，请使用学号登录", "success")
            return redirect(url_for("auth.login"))
        except BusinessError as e:
            error = e.message
    return render_template("register.html", form=form, error=error)


@bp.post("/logout")
def logout():
    """退出登录。用 POST 而不是 GET，防止被别的网站用一个图片链接让用户"被退出"。"""
    security.logout()
    flash("已退出登录", "success")
    return redirect(url_for("auth.login"))
