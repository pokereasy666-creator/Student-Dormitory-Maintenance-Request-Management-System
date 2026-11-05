"""
学生宿舍报修管理系统（Flask 版）—— 应用工厂。

create_app() 负责：加载配置 → 初始化数据库和 CSRF 扩展 → 注册登录/权限检查、
异常处理、页面公共变量、各功能蓝图 → 初始化数据库（建库、建表、预置账号）。
"""
import logging
import os

from flask import Flask, request

from .config import BASE_DIR, default_config, load_config
from .constants import BUILDINGS, OrderStatus, RepairCategory
from .db_init import init_database
from .errors import init_errors
from .extensions import csrf, db
from .security import current_user, init_security

access_log = logging.getLogger("dorm_repair.access")


def create_app(test_config: dict | None = None) -> Flask:
    """
    :param test_config: 测试时传入的配置（不读取 config.ini）；正常运行时为 None。
    """
    app = Flask(__name__, instance_path=str(BASE_DIR / "instance"))
    if test_config is None:
        app.config.update(load_config())
    else:
        app.config.update(default_config())
        app.config.update(test_config)

    db.init_app(app)
    csrf.init_app(app)
    init_security(app)
    init_errors(app)
    _register_template_helpers(app)
    _register_blueprints(app)
    _register_access_log(app)

    os.makedirs(app.config["UPLOAD_DIR"], exist_ok=True)
    with app.app_context():
        init_database(app)
    return app


def _register_blueprints(app: Flask) -> None:
    from .views import auth, files, profile, repair, student
    app.register_blueprint(auth.bp)       # 登录、注册、退出
    app.register_blueprint(profile.bp)    # 个人信息、修改密码
    app.register_blueprint(student.bp)    # 学生端 /student/...
    app.register_blueprint(repair.bp)     # 维修人员端 /repair/...
    app.register_blueprint(files.bp)      # 报修图片 /uploads/...


def _register_template_helpers(app: Flask) -> None:
    @app.context_processor
    def inject_globals():
        # 所有页面都能直接使用的变量：当前用户、楼栋 / 报修类型 / 工单状态列表
        return {
            "current_user": current_user(),
            "buildings": BUILDINGS,
            "categories": list(RepairCategory),
            "statuses": list(OrderStatus),
        }

    @app.template_filter("dt")
    def format_datetime(value, fmt="%Y-%m-%d %H:%M"):
        """日期格式化过滤器：{{ order.created_at | dt }}"""
        return value.strftime(fmt) if value else ""


def _register_access_log(app: Flask) -> None:
    @app.after_request
    def log_request(response):
        # 记录访问日志（静态文件除外），方便调试和查看系统使用情况
        if not request.path.startswith("/static/"):
            access_log.info("%s %s %s %s", request.remote_addr, request.method,
                            request.full_path.rstrip("?"), response.status_code)
        return response
