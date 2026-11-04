package com.dorm.repair.dto;

import com.dorm.repair.enums.Role;

import java.io.Serializable;

/**
 * 保存在 HttpSession 中的"当前登录用户"信息。
 * 只放必要字段，不放密码。拦截器根据其中的 role 做权限判断。
 */
public class LoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private String realName;
    private Role role;

    public LoginUser() {
    }

    public LoginUser(Long id, String username, String realName, Role role) {
        this.id = id;
        this.username = username;
        this.realName = realName;
        this.role = role;
    }

    public boolean isStudent() {
        return role == Role.STUDENT;
    }

    public boolean isRepairer() {
        return role == Role.REPAIRER;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
}
