package com.dorm.repair.service;

import com.dorm.repair.common.BusinessException;
import com.dorm.repair.common.Constants;
import com.dorm.repair.dto.LoginUser;
import com.dorm.repair.dto.PasswordForm;
import com.dorm.repair.dto.ProfileForm;
import com.dorm.repair.dto.RegisterForm;
import com.dorm.repair.entity.User;
import com.dorm.repair.enums.Role;
import com.dorm.repair.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 用户相关业务：注册、登录、修改个人信息、修改密码。
 * <p>
 * 表单格式校验（非空、长度、手机号格式）在 DTO 上用注解完成；
 * 这里只处理需要查数据库或跨字段比较的业务规则。
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 学生注册。
     * 规则：两次密码一致；楼栋必须在预设列表中；学号不能重复；角色固定为 STUDENT。
     */
    public void register(RegisterForm form) {
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new BusinessException("两次输入的密码不一致");
        }
        checkBuilding(form.getBuilding());
        String username = form.getUsername().trim();
        if (userMapper.findByUsername(username) != null) {
            throw new BusinessException("该学号已注册，请直接登录");
        }

        LocalDateTime now = LocalDateTime.now();
        User user = new User();
        user.setUsername(username);
        // 密码使用 BCrypt 单向加密后保存，数据库泄露也无法直接得到明文
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setRealName(form.getRealName().trim());
        // 角色写死为学生：维修人员账号只能由数据库预置，不开放注册
        user.setRole(Role.STUDENT);
        user.setPhone(form.getPhone());
        user.setBuilding(form.getBuilding());
        user.setRoomNo(form.getRoomNo().trim());
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 极端情况下两人同时用同一学号注册，上面的查询都没查到，由数据库唯一索引兜底
            throw new BusinessException("该学号已注册，请直接登录");
        }
    }

    /**
     * 登录校验。账号不存在和密码错误返回同一提示，避免被用来探测哪些账号存在。
     *
     * @return 放入 session 的登录用户信息
     */
    public LoginUser login(String username, String rawPassword) {
        User user = userMapper.findByUsername(username.trim());
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BusinessException("账号或密码错误");
        }
        return new LoginUser(user.getId(), user.getUsername(), user.getRealName(), user.getRole());
    }

    public User getById(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    /**
     * 修改个人信息。学生必须填写楼栋和宿舍号；维修人员没有宿舍信息，忽略这两项。
     *
     * @return 修改后的用户（controller 用它同步 session 中的姓名）
     */
    public User updateProfile(Long userId, ProfileForm form) {
        User user = getById(userId);
        user.setRealName(form.getRealName().trim());
        user.setPhone(form.getPhone());
        if (user.getRole() == Role.STUDENT) {
            checkBuilding(form.getBuilding());
            if (!StringUtils.hasText(form.getRoomNo())) {
                throw new BusinessException("请输入宿舍号");
            }
            user.setBuilding(form.getBuilding());
            user.setRoomNo(form.getRoomNo().trim());
        }
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateProfile(user);
        return user;
    }

    /**
     * 修改密码：必须先验证原密码，新密码两次输入一致且不能与原密码相同。
     */
    public void changePassword(Long userId, PasswordForm form) {
        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            throw new BusinessException("两次输入的新密码不一致");
        }
        User user = getById(userId);
        if (!passwordEncoder.matches(form.getOldPassword(), user.getPassword())) {
            throw new BusinessException("原密码错误");
        }
        if (passwordEncoder.matches(form.getNewPassword(), user.getPassword())) {
            throw new BusinessException("新密码不能与原密码相同");
        }
        userMapper.updatePassword(userId, passwordEncoder.encode(form.getNewPassword()), LocalDateTime.now());
    }

    /** 楼栋只能从预设列表中选择，防止伪造请求提交任意值 */
    private void checkBuilding(String building) {
        if (!Constants.BUILDINGS.contains(building)) {
            throw new BusinessException("请选择正确的楼栋");
        }
    }
}
