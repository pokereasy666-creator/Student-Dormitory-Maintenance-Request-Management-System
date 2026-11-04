package com.dorm.repair.entity;

import com.dorm.repair.enums.OrderStatus;
import com.dorm.repair.enums.RepairCategory;

import java.time.LocalDateTime;

/**
 * 报修工单实体，对应数据库表 repair_order。
 * <p>
 * 评价（rating / evaluation）与工单是一对一关系，直接存放在工单表中。
 * 末尾几个 studentXxx / repairerXxx 字段不是表字段，是查询时联表 sys_user 带出来用于页面展示的。
 */
public class RepairOrder {

    private Long id;
    /** 报修学生 ID */
    private Long studentId;
    private String building;
    private String roomNo;
    private RepairCategory category;
    /** 故障描述 */
    private String description;
    /** 图片相对路径（如 20261104/uuid.jpg），未上传则为 null */
    private String imagePath;
    private OrderStatus status;
    /** 接单的维修人员 ID，待接单时为 null */
    private Long repairerId;
    /** 维修备注（完成时填写） */
    private String repairNote;
    /** 评价星级 1~5，未评价为 null */
    private Integer rating;
    /** 评价文字 */
    private String evaluation;
    private LocalDateTime createdAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime completedAt;
    private LocalDateTime ratedAt;

    // ---------- 以下为联表查询的展示字段 ----------
    private String studentName;
    private String studentPhone;
    private String repairerName;
    private String repairerPhone;

    /** 是否可以评价：已完成且尚未评价 */
    public boolean isRatable() {
        return status == OrderStatus.COMPLETED && rating == null;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }

    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }

    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }

    public RepairCategory getCategory() { return category; }
    public void setCategory(RepairCategory category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public Long getRepairerId() { return repairerId; }
    public void setRepairerId(Long repairerId) { this.repairerId = repairerId; }

    public String getRepairNote() { return repairNote; }
    public void setRepairNote(String repairNote) { this.repairNote = repairNote; }

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public String getEvaluation() { return evaluation; }
    public void setEvaluation(String evaluation) { this.evaluation = evaluation; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public LocalDateTime getRatedAt() { return ratedAt; }
    public void setRatedAt(LocalDateTime ratedAt) { this.ratedAt = ratedAt; }

    public String getStudentName() { return studentName; }
    public void setStudentName(String studentName) { this.studentName = studentName; }

    public String getStudentPhone() { return studentPhone; }
    public void setStudentPhone(String studentPhone) { this.studentPhone = studentPhone; }

    public String getRepairerName() { return repairerName; }
    public void setRepairerName(String repairerName) { this.repairerName = repairerName; }

    public String getRepairerPhone() { return repairerPhone; }
    public void setRepairerPhone(String repairerPhone) { this.repairerPhone = repairerPhone; }
}
