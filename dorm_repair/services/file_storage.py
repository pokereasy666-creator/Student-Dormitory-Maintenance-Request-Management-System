"""
报修图片的本地存储。

图片保存在 config.ini 中 upload_dir 指定的目录下（默认项目根目录的 uploads），
按日期分子目录、文件名用 UUID，数据库只保存相对路径（如 20261105/xxx.jpg）。
页面通过 /uploads/相对路径 访问（需要登录，见 views/files.py）。
"""
import os
import uuid
from datetime import date
from pathlib import Path

from flask import current_app

from ..errors import BusinessError

ALLOWED_EXTENSIONS = {"jpg", "jpeg", "png", "gif", "webp"}
MAX_IMAGE_SIZE = 5 * 1024 * 1024  # 5MB
PNG_HEADER = b"\x89PNG\r\n\x1a\n"


def upload_root() -> Path:
    return Path(current_app.config["UPLOAD_DIR"]).resolve()


def store(file) -> str | None:
    """
    保存上传的图片。
    :param file: werkzeug 的 FileStorage 对象，可以为空（图片是可选项）
    :return: 相对路径；没有上传文件时返回 None
    """
    if file is None or not file.filename:
        return None
    ext = os.path.splitext(file.filename)[1].lower().lstrip(".")
    if ext not in ALLOWED_EXTENSIONS:
        raise BusinessError("仅支持 jpg / png / gif / webp 格式的图片")

    stream = file.stream
    stream.seek(0, os.SEEK_END)
    size = stream.tell()
    stream.seek(0)
    if size > MAX_IMAGE_SIZE:
        raise BusinessError("图片大小不能超过 5MB")
    # 扩展名可以随便改，所以再检查文件头（魔数），确认内容真的是图片
    head = stream.read(12)
    stream.seek(0)
    if not _looks_like_image(head):
        raise BusinessError("文件内容不是有效的图片")

    date_dir = date.today().strftime("%Y%m%d")
    file_name = f"{uuid.uuid4().hex}.{ext}"
    target_dir = upload_root() / date_dir
    target_dir.mkdir(parents=True, exist_ok=True)
    file.save(target_dir / file_name)
    return f"{date_dir}/{file_name}"


def delete(relative_path: str | None) -> None:
    """删除图片（工单保存失败时清理已上传的文件）。只允许删除上传目录内的文件。"""
    if not relative_path:
        return
    root = upload_root()
    target = (root / relative_path).resolve()
    if root in target.parents and target.is_file():
        target.unlink()


def _looks_like_image(head: bytes) -> bool:
    """根据文件头判断是否为 JPEG / PNG / GIF / WEBP 图片。"""
    if len(head) < 12:
        return False
    return (head[:3] == b"\xff\xd8\xff"
            or head[:8] == PNG_HEADER
            or head[:6] in (b"GIF87a", b"GIF89a")
            or (head[:4] == b"RIFF" and head[8:12] == b"WEBP"))
