"""报修图片访问：/uploads/20261105/xxx.jpg。需要登录（由 security.py 检查），未登录不能看图片。"""
from flask import Blueprint, current_app, send_from_directory

bp = Blueprint("files", __name__)


@bp.get("/uploads/<path:filename>")
def uploaded_file(filename):
    # send_from_directory 会拒绝 ../ 之类越出上传目录的路径
    return send_from_directory(current_app.config["UPLOAD_DIR"], filename)
