package com.dorm.repair.controller;

import com.dorm.repair.common.BusinessException;
import com.dorm.repair.common.Constants;
import com.dorm.repair.dto.LoginUser;
import com.dorm.repair.dto.PasswordForm;
import com.dorm.repair.dto.ProfileForm;
import com.dorm.repair.entity.User;
import com.dorm.repair.service.UserService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 个人中心：修改个人信息、修改密码（学生和维修人员共用）。
 * {@code @SessionAttribute} 直接从 session 中取出当前登录用户。
 */
@Controller
@RequestMapping("/profile")
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String profilePage(@SessionAttribute(Constants.SESSION_USER) LoginUser loginUser, Model model) {
        User user = userService.getById(loginUser.getId());
        ProfileForm form = new ProfileForm();
        form.setRealName(user.getRealName());
        form.setPhone(user.getPhone());
        form.setBuilding(user.getBuilding());
        form.setRoomNo(user.getRoomNo());
        model.addAttribute("user", user);
        model.addAttribute("profileForm", form);
        return "profile";
    }

    @PostMapping
    public String updateProfile(@SessionAttribute(Constants.SESSION_USER) LoginUser loginUser,
                                @Valid @ModelAttribute("profileForm") ProfileForm form, BindingResult result,
                                HttpSession session, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            model.addAttribute("user", userService.getById(loginUser.getId()));
            return "profile";
        }
        try {
            User updated = userService.updateProfile(loginUser.getId(), form);
            // 同步 session 中的姓名，导航栏立即显示新名字
            loginUser.setRealName(updated.getRealName());
            session.setAttribute(Constants.SESSION_USER, loginUser);
        } catch (BusinessException e) {
            model.addAttribute("user", userService.getById(loginUser.getId()));
            model.addAttribute("error", e.getMessage());
            return "profile";
        }
        redirect.addFlashAttribute("success", "个人信息已保存");
        return "redirect:/profile";
    }

    @GetMapping("/password")
    public String passwordPage(Model model) {
        model.addAttribute("passwordForm", new PasswordForm());
        return "password";
    }

    /** 修改密码成功后让当前登录失效，要求用新密码重新登录 */
    @PostMapping("/password")
    public String changePassword(@SessionAttribute(Constants.SESSION_USER) LoginUser loginUser,
                                 @Valid @ModelAttribute("passwordForm") PasswordForm form, BindingResult result,
                                 HttpSession session, Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "password";
        }
        try {
            userService.changePassword(loginUser.getId(), form);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "password";
        }
        session.invalidate();
        redirect.addFlashAttribute("success", "密码修改成功，请使用新密码重新登录");
        return "redirect:/login";
    }
}
