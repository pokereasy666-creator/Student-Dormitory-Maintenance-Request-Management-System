"""
数据库模型（ORM）：Python 类 ↔ 数据库表。相当于 Java 版的 entity。

表名、字段名与 Java 版保持一致（sys_user / repair_order），枚举字段保存成员名字符串。
"""
from datetime import datetime

from .constants import OrderStatus, RepairCategory, Role
from .extensions import db

# MySQL 表选项：InnoDB 引擎 + utf8mb4 字符集（即使数据库默认是 latin1，中文也能正常保存）
MYSQL_TABLE_OPTIONS = {
    "mysql_engine": "InnoDB",
    "mysql_charset": "utf8mb4",
    "mysql_collate": "utf8mb4_unicode_ci",
}

# 主键：MySQL 用 BIGINT；SQLite（测试用）只有 INTEGER 主键才会自增
ID_TYPE = db.BigInteger().with_variant(db.Integer(), "sqlite")


def enum_column(enum_cls, length):
    """枚举列：以 VARCHAR 保存成员名（如 PENDING），而不是 MySQL 原生 ENUM 类型，方便扩展。"""
    return db.Enum(enum_cls, native_enum=False, length=length, validate_strings=True)


class User(db.Model):
    """用户表：学生和维修人员共用，role 区分角色。"""
    __tablename__ = "sys_user"
    __table_args__ = (dict(MYSQL_TABLE_OPTIONS, comment="用户表"),)

    id = db.Column(ID_TYPE, primary_key=True, autoincrement=True, comment="主键")
    username = db.Column(db.String(32), nullable=False, unique=True, comment="登录账号：学生为学号，维修人员为工号")
    password = db.Column(db.String(255), nullable=False, comment="加盐哈希后的密码（scrypt）")
    real_name = db.Column(db.String(32), nullable=False, comment="姓名")
    role = db.Column(enum_column(Role, 16), nullable=False, comment="角色：STUDENT / REPAIRER")
    phone = db.Column(db.String(11), nullable=False, comment="手机号")
    building = db.Column(db.String(32), comment="楼栋（仅学生）")
    room_no = db.Column(db.String(16), comment="宿舍号（仅学生）")
    created_at = db.Column(db.DateTime, nullable=False, default=datetime.now, comment="注册时间")
    updated_at = db.Column(db.DateTime, nullable=False, default=datetime.now, comment="最后修改时间")


class RepairOrder(db.Model):
    """报修工单表。评价（rating / evaluation）与工单一对一，直接存放在本表。"""
    __tablename__ = "repair_order"
    __table_args__ = (dict(MYSQL_TABLE_OPTIONS, comment="报修工单表"),)

    id = db.Column(ID_TYPE, primary_key=True, autoincrement=True, comment="工单号")
    student_id = db.Column(ID_TYPE, db.ForeignKey("sys_user.id"), nullable=False, index=True, comment="报修学生 ID")
    building = db.Column(db.String(32), nullable=False, comment="楼栋")
    room_no = db.Column(db.String(16), nullable=False, comment="宿舍号")
    category = db.Column(enum_column(RepairCategory, 20), nullable=False, comment="报修类型")
    description = db.Column(db.String(500), nullable=False, comment="故障描述")
    image_path = db.Column(db.String(255), comment="故障图片相对路径，可空")
    status = db.Column(enum_column(OrderStatus, 20), nullable=False, index=True, comment="状态")
    repairer_id = db.Column(ID_TYPE, db.ForeignKey("sys_user.id"), index=True, comment="接单维修人员 ID")
    repair_note = db.Column(db.String(500), comment="维修备注")
    rating = db.Column(db.SmallInteger, comment="评价星级 1~5")
    evaluation = db.Column(db.String(200), comment="评价内容")
    created_at = db.Column(db.DateTime, nullable=False, default=datetime.now, comment="提交时间")
    accepted_at = db.Column(db.DateTime, comment="接单时间")
    completed_at = db.Column(db.DateTime, comment="完成时间")
    rated_at = db.Column(db.DateTime, comment="评价时间")

    # 关联的学生、维修人员（lazy="joined"：查询工单时一并联表查出，列表页不会逐条再查）
    student = db.relationship(User, foreign_keys=[student_id], lazy="joined")
    repairer = db.relationship(User, foreign_keys=[repairer_id], lazy="joined")

    @property
    def ratable(self) -> bool:
        """是否可以评价：已完成且尚未评价。"""
        return self.status == OrderStatus.COMPLETED and self.rating is None
