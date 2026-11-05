"""
学生宿舍报修管理系统（Python 版）—— 启动入口。

用法：
    python run.py              正式运行：使用 waitress 服务器（Windows 上推荐，start.bat 也是这样启动的）
    python run.py --dev        开发调试：Flask 调试服务器，修改代码后自动重启，控制台打印 SQL
    python run.py --init-db    只初始化数据库（建库、建表、预置账号）后退出，用于检查数据库配置

启动后浏览器访问 http://localhost:5000 （端口见 config.ini）。
"""
import argparse
import logging
import sys

from dorm_repair.config import BASE_DIR

log = logging.getLogger("dorm_repair")


def main() -> int:
    parser = argparse.ArgumentParser(description="学生宿舍报修管理系统")
    parser.add_argument("--dev", action="store_true", help="开发模式（调试服务器 + 自动重启 + 打印 SQL）")
    parser.add_argument("--init-db", action="store_true", help="只初始化数据库后退出")
    parser.add_argument("--host", help="监听地址，默认 0.0.0.0（开发模式默认 127.0.0.1）")
    parser.add_argument("--port", type=int, help="端口，默认读取 config.ini")
    parser.add_argument("--no-console-log", action="store_true", help="日志只写入 logs/app.log，不输出到控制台")
    args = parser.parse_args()

    from dorm_repair.logging_setup import setup_logging
    setup_logging(BASE_DIR / "logs", console=not args.no_console_log, show_sql=args.dev)

    from dorm_repair import create_app
    from dorm_repair.db_init import DatabaseInitError
    try:
        app = create_app()
    except (DatabaseInitError, ValueError) as e:
        return fail(f"启动失败：{e}", args, code=2)

    if app.config.get("CONFIG_CREATED"):
        log.warning("已根据 config.example.ini 生成配置文件 %s ，如数据库密码不是 root 请修改后重新启动",
                    app.config["CONFIG_FILE"])
    log.info("数据库连接正常，数据表和预置账号已就绪")
    if args.init_db:
        return 0

    port = args.port or app.config["SERVER_PORT"]
    if args.dev:
        # 调试服务器只监听本机：调试模式下网页报错页面可以执行代码，不能暴露到局域网
        app.run(host=args.host or "127.0.0.1", port=port, debug=True)
        return 0

    from waitress import create_server
    try:
        server = create_server(app, host=args.host or "0.0.0.0", port=port, threads=8)
    except OSError as e:
        return fail(f"启动失败：端口 {port} 已被占用（{e}）。请关闭占用端口的程序，"
                    "或修改 config.ini 中 [server] 的 port。", args, code=3)
    log.info("系统已启动：http://localhost:%s （按 Ctrl+C 停止）", port)
    try:
        server.run()
    except KeyboardInterrupt:
        pass
    finally:
        server.close()
        log.info("系统已停止")
    return 0


def fail(message: str, args, code: int) -> int:
    log.error(message)
    if args.no_console_log:
        # 控制台日志关闭时，错误原因同时写到标准错误，方便启动脚本显示
        print(message, file=sys.stderr)
    return code


if __name__ == "__main__":
    sys.exit(main())
