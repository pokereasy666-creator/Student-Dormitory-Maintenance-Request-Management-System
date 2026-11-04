package com.dorm.repair.enums;

/**
 * 用户角色。数据库中以枚举名（STUDENT / REPAIRER）保存。
 */
public enum Role {
    /** 学生：可自主注册，提交报修、查看进度、评价 */
    STUDENT("学生"),
    /** 维修人员：账号由数据库预置，不开放注册；负责接单和维修 */
    REPAIRER("维修人员");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    /** 页面上显示的中文名称 */
    public String getLabel() {
        return label;
    }
}
