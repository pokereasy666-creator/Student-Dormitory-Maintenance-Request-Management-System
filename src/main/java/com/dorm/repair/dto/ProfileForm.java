package com.dorm.repair.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 修改个人信息表单。
 * 楼栋、宿舍号只对学生有效：是否必填在 UserService 中按角色判断。
 */
public class ProfileForm {

    @NotBlank(message = "请输入姓名")
    @Size(max = 20, message = "姓名不能超过 20 个字")
    private String realName;

    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1\\d{10}$", message = "请输入 11 位手机号")
    private String phone;

    private String building;

    @Pattern(regexp = "^$|^[A-Za-z0-9-]{1,10}$", message = "宿舍号为 1~10 位字母、数字或 -，如 302")
    private String roomNo;

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }

    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }
}
