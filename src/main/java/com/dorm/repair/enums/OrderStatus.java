package com.dorm.repair.enums;

/**
 * 工单状态。状态只能单向流转：
 * <pre>
 *   PENDING(待接单) --维修人员接单--> IN_PROGRESS(维修中) --维修人员标记完成--> COMPLETED(已完成)
 * </pre>
 * 流转的合法性由 RepairOrderMapper 中带状态条件的 UPDATE 语句保证。
 */
public enum OrderStatus {
    PENDING("待接单"),
    IN_PROGRESS("维修中"),
    COMPLETED("已完成");

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
