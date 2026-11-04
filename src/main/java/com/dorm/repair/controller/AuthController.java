package com.dorm.repair.controller;

import com.dorm.repair.common.BusinessException;
import com.dorm.repair.common.Constants;
import com.dorm.repair.dto.LoginForm;
import com.dorm.repair.dto.LoginUser;
import com.dorm.repair.dto.RegisterForm;
import com.dorm.repair.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 登录、注册、退出，以及登录后按角色跳转首页。
 */
@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /** 根路径：学生进入学生首页，维修人员进入待接单列表（未登录会被拦截器送去登录页） */
    @GetMapping("/")
    public String home(HttpSession session) {
        LoginUser user = (LoginUser) session.getAttribute(Constants.SESSION_USER);
        return user.isStudent() ? "redirect:/student/home" : "redirect:/repair/pending";
    }

    @GetMapping("/login")
    public String loginPage(HttpSession session, Model model) {
        if (session.getAttribute(Constants.SESSION_USER) != null) {
            return "redirect:/";
        }
        model.addAttribute("loginForm", new LoginForm());
        return "login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute("loginForm") LoginForm form, BindingResult result,
                        HttpServletRequest request, Model model) {
        if (result.hasErrors()) {
            return "login";
        }
        try {
            LoginUser user = userService.login(form.getUsername(), form.getPassword());
            HttpSession session = request.getSession();
            // 登录成功后更换 session id，防止"会话固定"攻击
            request.changeSessionId();
            session.setAttribute(Constants.SESSION_USER, user);
            return "redirect:/";
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "login";
        }
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerForm", new RegisterForm());
        return "register";
    }

    /** 学生注册；成功后跳到登录页 */
    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerForm") RegisterForm form, BindingResult result,
                           Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "register";
        }
        try {
            userService.register(form);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
        redirect.addFlashAttribute("success", "注册成功，请使用学号登录");
        return "redirect:/login";
    }

    /** 退出登录：销毁 session。用 POST 而不是 GET，防止被别的网站用一个图片链接让用户"被退出" */
    @PostMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes redirect) {
        session.invalidate();
        redirect.addFlashAttribute("success", "已退出登录");
        return "redirect:/login";
    }
}
