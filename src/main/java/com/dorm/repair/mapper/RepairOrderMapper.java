package com.dorm.repair.mapper;

import com.dorm.repair.entity.RepairOrder;
import com.dorm.repair.enums.OrderStatus;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 工单表 repair_order 的数据访问接口。
 * <p>
 * 关键设计：接单 / 完成 / 评价 三个 UPDATE 都在 WHERE 中带上"当前应处的状态"作为条件。
 * 数据库对单行 UPDATE 是原子执行的，所以即使两个维修人员同时点击"接单"，
 * 也只有一条 UPDATE 能匹配到 status = 'PENDING' 的记录（返回 1），另一条返回 0。
 * service 层根据返回的影响行数判断操作是否成功，从而保证状态只能单向流转、不会被重复接单。
 */
public interface RepairOrderMapper {

    /** 公共查询片段：联表带出学生和维修人员的姓名、电话 */
    String SELECT_WITH_USER = "SELECT o.*, "
            + "s.real_name AS student_name, s.phone AS student_phone, "
            + "r.real_name AS repairer_name, r.phone AS repairer_phone "
            + "FROM repair_order o "
            + "JOIN sys_user s ON o.student_id = s.id "
            + "LEFT JOIN sys_user r ON o.repairer_id = r.id ";

    @Insert("INSERT INTO repair_order (student_id, building, room_no, category, description, image_path, status, created_at) "
            + "VALUES (#{studentId}, #{building}, #{roomNo}, #{category}, #{description}, #{imagePath}, #{status}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(RepairOrder order);

    @Select(SELECT_WITH_USER + "WHERE o.id = #{id}")
    RepairOrder findById(Long id);

    /** 学生查看自己的工单；status 为 null 时查全部。按提交时间倒序（最新的在前） */
    @Select("<script>" + SELECT_WITH_USER
            + "WHERE o.student_id = #{studentId} "
            + "<if test='status != null'> AND o.status = #{status} </if>"
            + "ORDER BY o.created_at DESC, o.id DESC"
            + "</script>")
    List<RepairOrder> listByStudent(@Param("studentId") Long studentId, @Param("status") OrderStatus status);

    /** 待接单池：所有维修人员可见。按提交时间正序（先报先修） */
    @Select(SELECT_WITH_USER + "WHERE o.status = 'PENDING' ORDER BY o.created_at ASC, o.id ASC")
    List<RepairOrder> listPending();

    /** 维修人员查看自己接过的工单；status 为 null 时查全部 */
    @Select("<script>" + SELECT_WITH_USER
            + "WHERE o.repairer_id = #{repairerId} "
            + "<if test='status != null'> AND o.status = #{status} </if>"
            + "ORDER BY o.accepted_at DESC, o.id DESC"
            + "</script>")
    List<RepairOrder> listByRepairer(@Param("repairerId") Long repairerId, @Param("status") OrderStatus status);

    /**
     * 接单：只有"待接单"的工单才能被接。
     * 返回 1 表示接单成功；返回 0 表示工单不存在或已被别人接走。
     */
    @Update("UPDATE repair_order SET status = 'IN_PROGRESS', repairer_id = #{repairerId}, accepted_at = #{now} "
            + "WHERE id = #{id} AND status = 'PENDING'")
    int accept(@Param("id") Long id, @Param("repairerId") Long repairerId, @Param("now") LocalDateTime now);

    /**
     * 标记完成：只能完成"自己接的、维修中"的工单。
     * 返回 0 表示不满足条件（不是自己的单 / 已完成 / 还没接单）。
     */
    @Update("UPDATE repair_order SET status = 'COMPLETED', repair_note = #{repairNote}, completed_at = #{now} "
            + "WHERE id = #{id} AND repairer_id = #{repairerId} AND status = 'IN_PROGRESS'")
    int complete(@Param("id") Long id, @Param("repairerId") Long repairerId,
                 @Param("repairNote") String repairNote, @Param("now") LocalDateTime now);

    /**
     * 评价：只能评价"自己的、已完成、尚未评价"的工单，保证每张工单只评价一次。
     */
    @Update("UPDATE repair_order SET rating = #{rating}, evaluation = #{evaluation}, rated_at = #{now} "
            + "WHERE id = #{id} AND student_id = #{studentId} AND status = 'COMPLETED' AND rating IS NULL")
    int rate(@Param("id") Long id, @Param("studentId") Long studentId,
             @Param("rating") Integer rating, @Param("evaluation") String evaluation,
             @Param("now") LocalDateTime now);
}
