package com.dorm.repair.common;

import com.dorm.repair.enums.OrderStatus;
import com.dorm.repair.enums.RepairCategory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/**
 * 给所有页面统一放入下拉框 / 标签页需要的固定数据，省得每个 controller 都写一遍。
 */
@ControllerAdvice
public class GlobalModelAttributes {

    /** 楼栋下拉框 */
    @ModelAttribute("buildings")
    public List<String> buildings() {
        return Constants.BUILDINGS;
    }

    /** 报修类型下拉框 */
    @ModelAttribute("categories")
    public RepairCategory[] categories() {
        return RepairCategory.values();
    }

    /** 工单状态筛选标签 */
    @ModelAttribute("statuses")
    public OrderStatus[] statuses() {
        return OrderStatus.values();
    }
}
