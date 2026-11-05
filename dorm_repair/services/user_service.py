"""
用户相关业务：注册、登录、修改个人信息、修改密码。

格式校验（非空、长度、手机号格式、两次密码一致）由 forms.py 完成，
这里处理需要查数据库的规则。
"""
from datetime import datetime

from sqlalchemy.exc import IntegrityError
from werkzeug.security import check_password_hash, generate_password_hash

from ..constants import BUILDINGS, Role
from ..errors import BusinessError
from ..extensions import db
from ..models import User
from ..security import LoginUser


def register(username: str, real_name: str, password: str, building: str, room_no: str, phone: str) -> None:
    """学生注册。规则：学号不能重复；角色固定为学生。"""
    username = username.strip()
    if building not in BUILDINGS:
        raise BusinessError("请选择正确的楼栋")
    if db.session.query(User.id).filter_by(username=username).first():
        raise BusinessError("该学号已注册，请直接登录")
    user = User(
        username=username,
        # 密码加盐哈希后保存（单向，不可还原），数据库泄露也得不到明文
        password=generate_password_hash(password),
        real_name=real_name.strip(),
        # 角色写死为学生：维修人员账号只能由系统预置，伪造 role 参数也无效
        role=Role.STUDENT,
        phone=phone,
        building=building,
        room_no=room_no.strip(),
    )
    db.session.add(user)
    try:
        db.session.commit()
    except IntegrityError:
        # 极端情况下两人同时用同一学号注册，由数据库唯一索引兜底
        db.session.rollback()
        raise BusinessError("该学号已注册，请直接登录")


def authenticate(username: str, password: str) -> LoginUser:
    """登录校验。账号不存在和密码错误返回同一提示，避免被用来探测哪些账号存在。"""
    user = db.session.query(User).filter_by(username=username.strip()).first()
    if user is None or not check_password_hash(user.password, password):
        raise BusinessError("账号或密码错误")
    return LoginUser(id=user.id, username=user.username, real_name=user.real_name, role=user.role.name)


def get_user(user_id: int) -> User:
    user = db.session.get(User, user_id)
    if user is None:
        raise BusinessError("用户不存在")
    return user


def update_profile(user_id: int, real_name: str, phone: str, building: str, room_no: str) -> User:
    """修改个人信息。学生必须填写楼栋和宿舍号；维修人员没有宿舍信息，忽略这两项。"""
    user = get_user(user_id)
    user.real_name = real_name.strip()
    user.phone = phone
    if user.role == Role.STUDENT:
        if building not in BUILDINGS:
            raise BusinessError("请选择正确的楼栋")
        if not room_no or not room_no.strip():
            raise BusinessError("请输入宿舍号")
        user.building = building
        user.room_no = room_no.strip()
    user.updated_at = datetime.now()
    db.session.commit()
    return user


def change_password(user_id: int, old_password: str, new_password: str) -> None:
    """修改密码：必须先验证原密码，新密码不能与原密码相同。"""
    user = get_user(user_id)
    if not check_password_hash(user.password, old_password):
        raise BusinessError("原密码错误")
    if check_password_hash(user.password, new_password):
        raise BusinessError("新密码不能与原密码相同")
    user.password = generate_password_hash(new_password)
    user.updated_at = datetime.now()
    db.session.commit()
