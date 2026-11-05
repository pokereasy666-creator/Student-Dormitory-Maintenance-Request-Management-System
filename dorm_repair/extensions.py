"""
Flask 扩展对象。单独放在一个模块里，避免循环导入：
models.py 等模块从这里导入 db，create_app() 中再把它们绑定到应用上。
"""
from flask_sqlalchemy import SQLAlchemy
from flask_wtf import CSRFProtect

# 数据库（SQLAlchemy ORM）
db = SQLAlchemy()

# CSRF 防护：所有 POST 表单必须带上 csrf_token，防止其他网站伪造请求
csrf = CSRFProtect()
