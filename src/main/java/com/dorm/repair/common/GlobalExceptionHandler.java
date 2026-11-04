package com.dorm.repair.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * 全局异常处理：把未在 controller 中处理的异常转换成友好的页面，避免直接显示错误堆栈。
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 角色不符 → 403 页面 */
    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ModelAndView handleForbidden(ForbiddenException e) {
        ModelAndView mv = new ModelAndView("403");
        mv.addObject("message", e.getMessage());
        return mv;
    }

    /**
     * 上传图片超过 application.yml 中 spring.servlet.multipart.max-file-size 的限制。
     * 这个异常在进入 controller 之前就会抛出，所以只能在这里统一处理：带提示跳回报修表单。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleUploadTooLarge(HttpServletRequest request) {
        RequestContextUtils.getOutputFlashMap(request).put("error", "图片大小不能超过 5MB");
        return "redirect:/student/orders/new";
    }

    /** 未被 controller 捕获的业务异常（如查看不属于自己的工单）→ 错误提示页 */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ModelAndView handleBusiness(BusinessException e) {
        ModelAndView mv = new ModelAndView("error");
        mv.addObject("message", e.getMessage());
        return mv;
    }
}
