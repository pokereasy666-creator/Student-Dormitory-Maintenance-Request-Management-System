"""
报修工单业务。整个闭环：

    学生 submit → [待接单] → 维修人员 accept → [维修中] → 维修人员 complete → [已完成] → 学生 rate

关键设计：接单 / 完成 / 评价都使用"带状态条件的 UPDATE"，例如接单：

    UPDATE repair_order SET status='IN_PROGRESS', repairer_id=? ... WHERE id=? AND status='PENDING'

数据库对单行 UPDATE 加行锁、原子执行。两个维修人员同时接同一单时，只有一条语句能匹配到
status='PENDING'（影响行数 1），另一条影响行数为 0 → 提示"已被接单"。这样不需要额外加锁，
就保证了状态只能单向流转、不会被重复接单、每单只能评价一次。

数据归属校验（学生只能看自己的单等）也都在这一层完成，不依赖页面是否显示按钮。
"""
from datetime import datetime

from sqlalchemy import func, select, update

from ..constants import OrderStatus, RepairCategory
from ..errors import BusinessError
from ..extensions import db
from ..models import RepairOrder
from . import file_storage


def _conditional_update(*conditions, **values) -> int:
    """执行带条件的 UPDATE 并提交，返回影响的行数。"""
    stmt = (update(RepairOrder).where(*conditions).values(**values)
            # 直接执行一条 UPDATE 语句，不预先查询，保证"判断状态 + 修改"是一个原子操作
            .execution_options(synchronize_session=False))
    result = db.session.execute(stmt)
    db.session.commit()
    return result.rowcount


# ======================== 学生端 ========================

def submit(student_id: int, building: str, room_no: str, category: RepairCategory,
           description: str, image=None) -> int:
    """学生提交报修工单，初始状态为"待接单"，返回工单号。"""
    # 先保存图片（内部会校验格式和大小），拿到相对路径再写数据库
    image_path = file_storage.store(image)
    order = RepairOrder(student_id=student_id, building=building, room_no=room_no.strip(),
                        category=category, description=description.strip(), image_path=image_path,
                        status=OrderStatus.PENDING, created_at=datetime.now())
    try:
        db.session.add(order)
        db.session.commit()
    except Exception:
        # 数据库写入失败时删除刚保存的图片，避免产生无主文件
        db.session.rollback()
        file_storage.delete(image_path)
        raise
    return order.id


def list_for_student(student_id: int, status: OrderStatus | None = None) -> list[RepairOrder]:
    """学生查看自己的工单；status 为 None 表示全部。按提交时间倒序。"""
    stmt = select(RepairOrder).where(RepairOrder.student_id == student_id)
    if status is not None:
        stmt = stmt.where(RepairOrder.status == status)
    stmt = stmt.order_by(RepairOrder.created_at.desc(), RepairOrder.id.desc())
    return list(db.session.scalars(stmt).unique())


def count_for_student(student_id: int) -> dict[OrderStatus, int]:
    """统计学生各状态的工单数量（学生首页展示）。"""
    counts = {s: 0 for s in OrderStatus}
    rows = db.session.execute(
        select(RepairOrder.status, func.count()).where(RepairOrder.student_id == student_id)
        .group_by(RepairOrder.status))
    for status, count in rows:
        counts[status] = count
    return counts


def get_for_student(order_id: int, student_id: int) -> RepairOrder:
    """学生查看工单详情：只能看自己的。不存在和不属于自己返回同一提示，不暴露别人的工单是否存在。"""
    order = db.session.get(RepairOrder, order_id)
    if order is None or order.student_id != student_id:
        raise BusinessError("工单不存在或无权查看")
    return order


def rate(order_id: int, student_id: int, rating: int, evaluation: str | None) -> None:
    """学生评价：工单必须属于自己、已完成、且尚未评价（每单只能评价一次）。"""
    order = get_for_student(order_id, student_id)
    if order.status != OrderStatus.COMPLETED:
        raise BusinessError("维修完成后才能评价")
    if order.rating is not None:
        raise BusinessError("该工单已评价，不能重复评价")
    evaluation = evaluation.strip() if evaluation and evaluation.strip() else None
    # 上面的检查用于给出友好提示；这里的条件 UPDATE 防止并发重复提交（如连点两次按钮）
    rows = _conditional_update(
        RepairOrder.id == order_id,
        RepairOrder.student_id == student_id,
        RepairOrder.status == OrderStatus.COMPLETED,
        RepairOrder.rating.is_(None),
        rating=rating, evaluation=evaluation, rated_at=datetime.now())
    if rows == 0:
        raise BusinessError("该工单已评价，不能重复评价")


# ======================== 维修人员端 ========================

def list_pending() -> list[RepairOrder]:
    """待接单池：所有待接单工单，按提交时间正序（先报先修）。"""
    stmt = (select(RepairOrder).where(RepairOrder.status == OrderStatus.PENDING)
            .order_by(RepairOrder.created_at.asc(), RepairOrder.id.asc()))
    return list(db.session.scalars(stmt).unique())


def accept(order_id: int, repairer_id: int) -> None:
    """接单：待接单 → 维修中，并记录接单人。多人同时接同一单时只有一人成功。"""
    rows = _conditional_update(
        RepairOrder.id == order_id,
        RepairOrder.status == OrderStatus.PENDING,
        status=OrderStatus.IN_PROGRESS, repairer_id=repairer_id, accepted_at=datetime.now())
    if rows == 0:
        if db.session.get(RepairOrder, order_id) is None:
            raise BusinessError("工单不存在")
        raise BusinessError("手慢了，该工单已被其他维修人员接单")


def complete(order_id: int, repairer_id: int, repair_note: str) -> None:
    """维修完成：维修中 → 已完成，必须填写维修备注。只能完成自己接的单。"""
    rows = _conditional_update(
        RepairOrder.id == order_id,
        RepairOrder.repairer_id == repairer_id,
        RepairOrder.status == OrderStatus.IN_PROGRESS,
        status=OrderStatus.COMPLETED, repair_note=repair_note.strip(), completed_at=datetime.now())
    if rows == 0:
        raise BusinessError("只能完成自己接单且处于维修中的工单")


def list_for_repairer(repairer_id: int, status: OrderStatus | None = None) -> list[RepairOrder]:
    """维修人员查看自己接过的工单；status 为 None 表示全部。按接单时间倒序。"""
    stmt = select(RepairOrder).where(RepairOrder.repairer_id == repairer_id)
    if status is not None:
        stmt = stmt.where(RepairOrder.status == status)
    stmt = stmt.order_by(RepairOrder.accepted_at.desc(), RepairOrder.id.desc())
    return list(db.session.scalars(stmt).unique())


def get_for_repairer(order_id: int, repairer_id: int) -> RepairOrder:
    """维修人员查看详情：可以看待接单工单（决定是否接单）或自己接过的工单，不能看别人的。"""
    order = db.session.get(RepairOrder, order_id)
    if order is None or not (order.status == OrderStatus.PENDING or order.repairer_id == repairer_id):
        raise BusinessError("工单不存在或无权查看")
    return order
