package com.dorm.repair.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 学生注册表单。
 * <p>
 * 注意：表单中没有 role 字段，注册出来的账号在 service 中固定为学生，
 * 即使有人伪造请求参数 role=REPAIRER 也不会生效。
 */
public class RegisterForm {

    @NotBlank(message = "请输入学号")
    @Pattern(regexp = "^[A-Za-z0-9]{4,20}$", message = "学号为 4~20 位字母或数字")
    private String username;

    @NotBlank(message = "请输入姓名")
    @Size(max = 20, message = "姓名不能超过 20 个字")
    private String realName;

    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 20, message = "密码长度为 6~20 位")
    private String password;

    @NotBlank(message = "请再次输入密码")
    private String confirmPassword;

    @NotBlank(message = "请选择楼栋")
    private String building;

    @NotBlank(message = "请输入宿舍号")
    @Pattern(regexp = "^[A-Za-z0-9-]{1,10}$", message = "宿舍号为 1~10 位字母、数字或 -，如 302")
    private String roomNo;

    @NotBlank(message = "请输入手机号")
    @Pattern(regexp = "^1\\d{10}$", message = "请输入 11 位手机号")
    private String phone;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }

    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
