"""
常量与枚举（按需求写死在代码中，不做后台维护）。

枚举在数据库中保存的是成员名（如 PENDING），页面显示用 .label（中文）。
"""
import enum

# 楼栋列表：注册、修改资料、提交报修时只能从这里选择
BUILDINGS = [f"{i}号楼" for i in range(1, 9)]


class LabeledEnum(enum.Enum):
    """枚举基类：value 就是页面上显示的中文名称。"""

    @property
    def label(self) -> str:
        return self.value


class Role(LabeledEnum):
    """用户角色。学生可自主注册；维修人员账号由系统预置，不开放注册。"""
    STUDENT = "学生"
    REPAIRER = "维修人员"


class OrderStatus(LabeledEnum):
    """
    工单状态，只能单向流转：
        PENDING(待接单) --接单--> IN_PROGRESS(维修中) --完成--> COMPLETED(已完成)
    流转的合法性由 order_service 中带状态条件的 UPDATE 语句保证。
    """
    PENDING = "待接单"
    IN_PROGRESS = "维修中"
    COMPLETED = "已完成"


class RepairCategory(LabeledEnum):
    """报修类型（写死）。"""
    WATER_ELECTRIC = "水电"
    DOOR_WINDOW = "门窗"
    FURNITURE = "家具"
    BATHROOM = "卫生间"


def parse_enum(enum_cls, name):
    """把字符串（如 'PENDING'）转换成枚举成员；为空或不合法时返回 None。"""
    if not name:
        return None
    return enum_cls.__members__.get(name)
