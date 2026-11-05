"""
测试公共配置。

默认使用临时目录中的 SQLite 数据库，运行 pytest 不需要安装 MySQL；
设置环境变量 TEST_DATABASE_URL 可以改为在真实 MySQL 上运行同一套测试，例如：
    TEST_DATABASE_URL="mysql+pymysql://user:pass@127.0.0.1:3306/dorm_repair_pytest?charset=utf8mb4" pytest
CSRF 防护在测试中保持开启：每次 POST 都从页面中取出 csrf_token 一起提交，确保页面上的表单都带了 token。
"""
import os
import re

import pytest

from dorm_repair import create_app
from dorm_repair.extensions import db

CSRF_RE = re.compile(r'name="csrf_token"[^>]*value="([^"]+)"')


@pytest.fixture(scope="session")
def app(tmp_path_factory):
    tmp = tmp_path_factory.mktemp("dorm")
    db_url = os.environ.get("TEST_DATABASE_URL") or f"sqlite:///{tmp / 'test.db'}"
    app = create_app({
        "TESTING": True,
        "SECRET_KEY": "test-secret",
        "SQLALCHEMY_DATABASE_URI": db_url,
        "UPLOAD_DIR": str(tmp / "uploads"),
    })
    yield app
    if db_url.startswith("mysql"):
        with app.app_context():
            db.drop_all()


class Browser:
    """模拟一个浏览器（独立的 cookie），自动处理 CSRF token。"""

    def __init__(self, app):
        self.client = app.test_client()

    def get(self, url, **kwargs):
        return self.client.get(url, **kwargs)

    def token(self):
        # 登录前从登录页取 token，登录后从修改密码页取（两个页面都有表单）
        page = self.client.get("/login")
        if page.status_code == 302:
            page = self.client.get("/profile/password")
        match = CSRF_RE.search(page.get_data(as_text=True))
        assert match, "页面中没有找到 csrf_token"
        return match.group(1)

    def post(self, url, data=None, follow=False, csrf=True, **kwargs):
        data = dict(data or {})
        if csrf:
            data["csrf_token"] = self.token()
        return self.client.post(url, data=data, follow_redirects=follow, **kwargs)


@pytest.fixture
def browser(app):
    """创建新的匿名浏览器。"""
    return lambda: Browser(app)
