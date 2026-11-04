package com.dorm.repair.dto;

import com.dorm.repair.enums.RepairCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 提交报修表单（图片通过单独的 MultipartFile 参数接收）。
 */
public class OrderForm {

    @NotBlank(message = "请选择楼栋")
    private String building;

    @NotBlank(message = "请输入宿舍号")
    @Pattern(regexp = "^[A-Za-z0-9-]{1,10}$", message = "宿舍号为 1~10 位字母、数字或 -，如 302")
    private String roomNo;

    /** Spring 会把请求参数 "WATER_ELECTRIC" 等自动转换成枚举；不在枚举范围内的值会校验失败 */
    @NotNull(message = "请选择报修类型")
    private RepairCategory category;

    @NotBlank(message = "请填写故障描述")
    @Size(max = 500, message = "故障描述不能超过 500 字")
    private String description;

    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }

    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }

    public RepairCategory getCategory() { return category; }
    public void setCategory(RepairCategory category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
