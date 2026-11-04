package com.dorm.repair.enums;

/**
 * 报修类型（按需求写死在代码中，不做后台维护）。
 */
public enum RepairCategory {
    WATER_ELECTRIC("水电"),
    DOOR_WINDOW("门窗"),
    FURNITURE("家具"),
    BATHROOM("卫生间");

    private final String label;

    RepairCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
