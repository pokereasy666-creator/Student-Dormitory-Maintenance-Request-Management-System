"""
读取配置。

优先级：环境变量 > 项目根目录的 config.ini > 默认值。
config.ini 不存在时会自动从 config.example.ini 复制一份（config.ini 不提交到 Git，避免泄露密码）。
"""
import configparser
import os
import re
import secrets
import shutil
from datetime import timedelta
from pathlib import Path
from urllib.parse import quote_plus

# 项目根目录（dorm_repair 包的上一级）
BASE_DIR = Path(__file__).resolve().parent.parent

# 单个请求最大 6MB：图片限制 5MB，留 1MB 给表单其他字段
MAX_CONTENT_LENGTH = 6 * 1024 * 1024


def default_config(base_dir: Path = BASE_DIR) -> dict:
    """与配置文件无关的固定配置。"""
    return {
        "MAX_CONTENT_LENGTH": MAX_CONTENT_LENGTH,
        "UPLOAD_DIR": str(base_dir / "uploads"),
        "SERVER_PORT": 5000,
        # 会话 cookie：JS 不可读取；其他网站发起的 POST 不携带（配合 CSRF token 双重防护）
        "SESSION_COOKIE_HTTPONLY": True,
        "SESSION_COOKIE_SAMESITE": "Lax",
        "PERMANENT_SESSION_LIFETIME": timedelta(hours=2),  # 2 小时无操作需重新登录
        # CSRF token 随会话有效（默认 1 小时过期，表单页面停留太久会提交失败）
        "WTF_CSRF_TIME_LIMIT": None,
        # 使用 WTForms 自带的中文校验提示（不依赖 Flask-Babel）
        "WTF_I18N_ENABLED": False,
        # 连接池：使用前检测连接是否可用，避免 MySQL 断开空闲连接后报错
        "SQLALCHEMY_ENGINE_OPTIONS": {"pool_pre_ping": True, "pool_recycle": 3600},
        "SQLALCHEMY_TRACK_MODIFICATIONS": False,
    }


def load_config(base_dir: Path = BASE_DIR) -> dict:
    """读取 config.ini 和环境变量，返回 Flask 配置字典。"""
    ini_path = base_dir / "config.ini"
    example_path = base_dir / "config.example.ini"
    created = False
    if not ini_path.exists() and example_path.exists():
        shutil.copyfile(example_path, ini_path)
        created = True

    # interpolation=None：密码中可以包含 % 等特殊字符
    parser = configparser.ConfigParser(interpolation=None)
    # utf-8-sig：兼容 Windows 记事本保存时加上的 BOM
    parser.read(ini_path, encoding="utf-8-sig")

    def get(section: str, key: str, env: str, default: str) -> str:
        value = os.environ.get(env)
        if value:
            return value
        return parser.get(section, key, fallback=default).strip()

    db_host = get("database", "host", "DB_HOST", "localhost")
    db_port = get("database", "port", "DB_PORT", "3306")
    db_user = get("database", "user", "DB_USER", "root")
    db_password = get("database", "password", "DB_PASSWORD", "root")
    db_name = get("database", "name", "DB_NAME", "dorm_repair_py")
    server_port = get("server", "port", "SERVER_PORT", "5000")
    upload_dir = get("server", "upload_dir", "UPLOAD_DIR", "uploads")

    if not re.fullmatch(r"[A-Za-z0-9_]+", db_name):
        raise ValueError(f"config.ini 中的数据库名 {db_name!r} 不合法：只能包含字母、数字和下划线")
    if not db_port.isdigit() or not server_port.isdigit():
        raise ValueError("config.ini 中的端口号必须是数字")

    # 用户名、密码中可能有 @ # : 等特殊字符，必须进行 URL 编码
    database_url = os.environ.get("DATABASE_URL") or (
        f"mysql+pymysql://{quote_plus(db_user)}:{quote_plus(db_password)}"
        f"@{db_host}:{db_port}/{db_name}?charset=utf8mb4"
    )

    config = default_config(base_dir)
    config.update({
        "SQLALCHEMY_DATABASE_URI": database_url,
        "SECRET_KEY": load_secret_key(base_dir),
        "SERVER_PORT": int(server_port),
        # 相对路径相对于项目根目录；也可以写绝对路径
        "UPLOAD_DIR": str((base_dir / upload_dir).resolve()),
        "CONFIG_FILE": str(ini_path),
        "CONFIG_CREATED": created,
    })
    return config


def load_secret_key(base_dir: Path = BASE_DIR) -> str:
    """
    SECRET_KEY 用于给 session cookie 签名，防止被篡改。
    首次运行时随机生成并保存到 instance/secret_key，之后一直使用同一个（重启后登录状态不丢失）。
    """
    env_key = os.environ.get("SECRET_KEY")
    if env_key:
        return env_key
    key_file = base_dir / "instance" / "secret_key"
    if key_file.exists():
        key = key_file.read_text(encoding="utf-8").strip()
        if key:
            return key
    key_file.parent.mkdir(parents=True, exist_ok=True)
    key = secrets.token_hex(32)
    key_file.write_text(key, encoding="utf-8")
    return key
