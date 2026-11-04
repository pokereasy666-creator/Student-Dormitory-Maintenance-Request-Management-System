package com.dorm.repair.common;

/**
 * 越权访问异常：已登录但角色不符（如学生访问维修人员页面）时由拦截器抛出，
 * 由 {@link GlobalExceptionHandler} 统一返回 403 页面。
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
