"""
登录校验 + 角色权限隔离（相当于 Java 版的 AuthInterceptor 拦截器）。

在每个请求处理之前执行：
  1. 未登录：重定向到登录页（登录、注册页面和静态文件除外）；
  2. /student/... 只允许学生访问，/repair/... 只允许维修人员访问，否则返回 403。
因为在服务端检查，即使手动输入网址或伪造 POST 请求也无法越权，而不是仅仅"隐藏菜单"。
"""
from dataclasses import asdict, dataclass
from typing import Optional

from flask import redirect, request, session, url_for

from .constants import Role
from .errors import ForbiddenError

# 不需要登录就能访问的接口（endpoint = 蓝图名.函数名）
PUBLIC_ENDPOINTS = {"auth.login", "auth.register", "static"}

# 当前登录用户在 session 中的键名
SESSION_KEY = "user"


@dataclass
class LoginUser:
    """保存在 session 中的当前登录用户信息（不包含密码）。"""
    id: int
    username: str
    real_name: str
    role: str  # Role 的成员名：STUDENT / REPAIRER

    @property
    def is_student(self) -> bool:
        return self.role == Role.STUDENT.name

    @property
    def is_repairer(self) -> bool:
        return self.role == Role.REPAIRER.name

    @property
    def role_label(self) -> str:
        return Role[self.role].label


def current_user() -> Optional[LoginUser]:
    """取当前登录用户；未登录返回 None。"""
    data = session.get(SESSION_KEY)
    return LoginUser(**data) if data else None


def login(user: LoginUser) -> None:
    """登录：清空旧 session 后写入用户信息，有效期 2 小时（见 config.py）。"""
    session.clear()
    session[SESSION_KEY] = asdict(user)
    session.permanent = True


def logout() -> None:
    session.clear()


def init_security(app):
    @app.before_request
    def check_login_and_role():
        # endpoint 为 None 表示地址不存在，交给 404 处理
        if request.endpoint is None or request.endpoint in PUBLIC_ENDPOINTS:
            return None
        user = current_user()
        if user is None:
            return redirect(url_for("auth.login"))
        if request.path.startswith("/student/") and not user.is_student:
            raise ForbiddenError("该功能仅限学生使用")
        if request.path.startswith("/repair/") and not user.is_repairer:
            raise ForbiddenError("该功能仅限维修人员使用")
        return None
