"""
异常定义与全局异常处理：把异常转换成友好的中文页面，避免显示英文错误或堆栈。
"""
import logging

from flask import flash, redirect, render_template, url_for
from flask_wtf.csrf import CSRFError
from werkzeug.exceptions import MethodNotAllowed, NotFound, RequestEntityTooLarge

log = logging.getLogger(__name__)


class BusinessError(Exception):
    """业务异常：用户操作不合法（如账号已存在、工单已被接单）。message 直接展示给用户。"""

    def __init__(self, message: str):
        super().__init__(message)
        self.message = message


class ForbiddenError(Exception):
    """越权访问：已登录但角色不符（如学生访问维修人员页面），返回 403 页面。"""

    def __init__(self, message: str):
        super().__init__(message)
        self.message = message


def init_errors(app):
    """注册全局异常处理函数。"""

    @app.errorhandler(ForbiddenError)
    def handle_forbidden(e):
        return render_template("403.html", message=e.message), 403

    @app.errorhandler(BusinessError)
    def handle_business(e):
        # 未在视图函数中处理的业务异常（如查看不属于自己的工单）
        return render_template("error.html", title="操作失败", message=e.message), 400

    @app.errorhandler(RequestEntityTooLarge)
    def handle_too_large(e):
        # 请求体超过 MAX_CONTENT_LENGTH：只有提交报修会上传文件，带提示跳回报修表单
        flash("图片大小不能超过 5MB", "error")
        return redirect(url_for("student.new_order"))

    @app.errorhandler(CSRFError)
    def handle_csrf(e):
        return render_template("error.html", title="请求无效",
                               message="页面已过期或请求来源不合法，请返回并刷新页面后重试"), 400

    @app.errorhandler(NotFound)
    def handle_404(e):
        return render_template("error.html", title="页面不存在", message="您访问的页面不存在"), 404

    @app.errorhandler(MethodNotAllowed)
    def handle_405(e):
        return render_template("error.html", title="请求方式错误", message="请通过页面上的按钮操作"), 405

    @app.errorhandler(500)
    def handle_500(e):
        log.exception("服务器内部错误")
        return render_template("error.html", title="服务器错误", message="系统出现错误，请稍后重试"), 500
