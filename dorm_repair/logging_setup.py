"""
日志配置：写入 logs/app.log（单个文件最大 10MB，保留 5 个历史文件），可选同时输出到控制台。

Windows 下 start.bat 启动时关闭控制台输出（由脚本把日志文件内容显示到窗口），
避免点击黑窗口进入"选择"状态时，程序写控制台被卡住导致网站无响应。
"""
import logging
import sys
from logging.handlers import RotatingFileHandler
from pathlib import Path


def setup_logging(log_dir: Path, console: bool = True, show_sql: bool = False) -> None:
    log_dir.mkdir(parents=True, exist_ok=True)
    formatter = logging.Formatter("%(asctime)s %(levelname)-7s [%(name)s] %(message)s", "%Y-%m-%d %H:%M:%S")

    root = logging.getLogger()
    root.setLevel(logging.INFO)
    root.handlers.clear()

    file_handler = RotatingFileHandler(log_dir / "app.log", maxBytes=10 * 1024 * 1024,
                                       backupCount=5, encoding="utf-8")
    file_handler.setFormatter(formatter)
    root.addHandler(file_handler)

    if console:
        console_handler = logging.StreamHandler(sys.stdout)
        console_handler.setFormatter(formatter)
        root.addHandler(console_handler)

    # 开发模式下打印执行的 SQL，方便调试
    logging.getLogger("sqlalchemy.engine").setLevel(logging.INFO if show_sql else logging.WARNING)
