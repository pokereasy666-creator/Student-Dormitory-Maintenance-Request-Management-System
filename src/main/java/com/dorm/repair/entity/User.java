package com.dorm.repair.entity;

import com.dorm.repair.enums.Role;

import java.time.LocalDateTime;

/**
 * 用户实体，对应数据库表 sys_user。
 * 学生和维修人员共用一张表，通过 role 字段区分。
 */
public class User {

    private Long id;
    /** 登录账号：学生为学号，维修人员为工号 */
    private String username;
    /** BCrypt 加密后的密码（数据库中不保存明文） */
    private String password;
    private String realName;
    private Role role;
    private String phone;
    /** 楼栋（仅学生有值） */
    private String building;
    /** 宿舍号（仅学生有值） */
    private String roomNo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getBuilding() { return building; }
    public void setBuilding(String building) { this.building = building; }

    public String getRoomNo() { return roomNo; }
    public void setRoomNo(String roomNo) { this.roomNo = roomNo; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
