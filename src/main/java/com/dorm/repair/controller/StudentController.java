package com.dorm.repair.controller;

import com.dorm.repair.common.BusinessException;
import com.dorm.repair.common.Constants;
import com.dorm.repair.common.FormErrors;
import com.dorm.repair.dto.LoginUser;
import com.dorm.repair.dto.OrderForm;
import com.dorm.repair.dto.RateForm;
import com.dorm.repair.entity.User;
import com.dorm.repair.enums.OrderStatus;
import com.dorm.repair.service.RepairOrderService;
import com.dorm.repair.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 学生端：首页公告、提交报修、我的报修、工单评价。
 * 所有 /student/** 请求已由 AuthInterceptor 保证是学生本人登录。
 */
@Controller
@RequestMapping("/student")
public class StudentController {

    private final RepairOrderService orderService;
    private final UserService userService;

    public StudentController(RepairOrderService orderService, UserService userService) {
        this.orderService = orderService;
        this.userService = userService;
    }

    /** 学生首页：公告（写死在 student/home.html 中）+ 我的工单统计 */
    @GetMapping("/home")
    public String home(@SessionAttribute(Constants.SESSION_USER) LoginUser loginUser, Model model) {
        model.addAttribute("counts", orderService.countForStudent(loginUser.getId()));
        return "student/home";
    }

    /** 报修表单：楼栋、宿舍号默认带出学生注册时填写的信息，可以修改 */
    @GetMapping("/orders/new")
    public String newOrder(@SessionAttribute(Constants.SESSION_USER) LoginUser loginUser, Model model) {
        User user = userService.getById(loginUser.getId());
        OrderForm form = new OrderForm();
        form.setBuilding(user.getBuilding());
        form.setRoomNo(user.getRoomNo());
        model.addAttribute("orderForm", form);
        return "student/order-form";
    }

    /** 提交报修（multipart 表单，image 为可选图片） */
    @PostMapping("/orders")
    public String submitOrder(@SessionAttribute(Constants.SESSION_USER) LoginUser loginUser,
                              @Valid @ModelAttribute("orderForm") OrderForm form, BindingResult result,
                              @RequestParam(value = "image", required = false) MultipartFile image,
                              Model model, RedirectAttributes redirect) {
        if (result.hasErrors()) {
            return "student/order-form";
        }
        Long orderId;
        try {
            orderId = orderService.submit(loginUser.getId(), form, image);
        } catch (BusinessException e) {
            model.addAttribute("error", e.getMessage());
            return "student/order-form";
        }
        redirect.addFlashAttribute("success", "报修已提交（工单号 #" + orderId + "），请等待维修人员接单");
        return "redirect:/student/orders/" + orderId;
    }

    /** 我的报修列表；status 参数为空表示全部 */
    @GetMapping("/orders")
    public String myOrders(@SessionAttribute(Constants.SESSION_USER) LoginUser loginUser,
                           @RequestParam(value = "status", required = false) OrderStatus status,
                           Model model) {
        model.addAttribute("orders", orderService.listForStudent(loginUser.getId(), status));
        model.addAttribute("currentStatus", status);
        return "student/orders";
    }

    /** 工单详情（含维修进度、维修备注、评价表单）；不是自己的工单会抛 BusinessException 显示错误页 */
    @GetMapping("/orders/{id}")
    public String orderDetail(@SessionAttribute(Constants.SESSION_USER) LoginUser loginUser,
                              @PathVariable Long id, Model model) {
        model.addAttribute("order", orderService.getForStudent(id, loginUser.getId()));
        model.addAttribute("rateForm", new RateForm());
        return "student/order-detail";
    }

    /** 提交评价：星级 + 简短文字 */
    @PostMapping("/orders/{id}/rate")
    public String rate(@SessionAttribute(Constants.SESSION_USER) LoginUser loginUser,
                       @PathVariable Long id,
                       @Valid @ModelAttribute("rateForm") RateForm form, BindingResult result,
                       RedirectAttributes redirect) {
        if (result.hasErrors()) {
            redirect.addFlashAttribute("error", FormErrors.first(result));
        } else {
            try {
                orderService.rate(id, loginUser.getId(), form);
                redirect.addFlashAttribute("success", "评价成功，感谢您的反馈");
            } catch (BusinessException e) {
                redirect.addFlashAttribute("error", e.getMessage());
            }
        }
        return "redirect:/student/orders/" + id;
    }
}
