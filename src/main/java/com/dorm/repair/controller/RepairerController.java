package com.dorm.repair.controller;

import com.dorm.repair.common.BusinessException;
import com.dorm.repair.common.Constants;
import com.dorm.repair.common.FormErrors;
import com.dorm.repair.dto.CompleteForm;
import com.dorm.repair.dto.LoginUser;
import com.dorm.repair.enums.OrderStatus;
import com.dorm.repair.service.RepairOrderService;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 维修人员端：待接单列表、接单、完成维修、我的维修记录。
 * 所有 /repair/** 请求已由 AuthInterceptor 保证是维修人员登录。
 */
@Controller
@RequestMapping("/repair")
public class RepairerController {

    private final RepairOrderService orderService;

    public RepairerController(RepairOrderService orderService) {
        this.orderService = orderService;
    }

    /** 待接单池 */
    @GetMapping("/pending")
    public String pending(Model model) {
        model.addAttribute("orders", orderService.listPending());
        return "repair/pending";
    }

    /** 接单：成功后进入工单详情页，失败（已被别人接走）回到待接单列表 */
    @PostMapping("/orders/{id}/accept")
    public String accept(@SessionAttribute(Constants.SESSION_USER) LoginUser loginUser,
                         @PathVariable Long id, RedirectAttributes redirect) {
        try {
            orderService.accept(id, loginUser.getId());
        } catch (BusinessException e) {
            redirect.addFlashAttribute("error", e.getMessage());
            return "redirect:/repair/pending";
        }
        redirect.addFlashAttribute("success", "接单成功，请尽快上门维修");
        return "redirect:/repair/orders/" + id;
    }

    /** 维修完成：填写维修备注并标记已完成 */
    @PostMapping("/orders/{id}/complete")
    public String complete(@SessionAttribute(Constants.SESSION_USER) LoginUser loginUser,
                           @PathVariable Long id,
                           @Valid @ModelAttribute("completeForm") CompleteForm form, BindingResult result,
                           RedirectAttributes redirect) {
        if (result.hasErrors()) {
            redirect.addFlashAttribute("error", FormErrors.first(result));
        } else {
            try {
                orderService.complete(id, loginUser.getId(), form);
                redirect.addFlashAttribute("success", "工单已标记为完成");
            } catch (BusinessException e) {
                redirect.addFlashAttribute("error", e.getMessage());
            }
        }
        return "redirect:/repair/orders/" + id;
    }

    /** 我的维修记录（维修中 + 已完成）；status 为空表示全部 */
    @GetMapping("/mine")
    public String mine(@SessionAttribute(Constants.SESSION_USER) LoginUser loginUser,
                       @RequestParam(value = "status", required = false) OrderStatus status,
                       Model model) {
        model.addAttribute("orders", orderService.listForRepairer(loginUser.getId(), status));
        model.addAttribute("currentStatus", status);
        return "repair/mine";
    }

    /** 工单详情：待接单的可看（决定是否接），自己接的可看，别人的不可看 */
    @GetMapping("/orders/{id}")
    public String orderDetail(@SessionAttribute(Constants.SESSION_USER) LoginUser loginUser,
                              @PathVariable Long id, Model model) {
        model.addAttribute("order", orderService.getForRepairer(id, loginUser.getId()));
        model.addAttribute("completeForm", new CompleteForm());
        return "repair/order-detail";
    }
}
