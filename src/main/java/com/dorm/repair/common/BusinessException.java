package com.dorm.repair.common;

/**
 * 业务异常：用于表示"用户操作不合法"的情况（如账号已存在、工单已被接单等）。
 * <p>
 * service 层发现业务规则不满足时抛出，controller 捕获后把 message 提示给用户。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }
}
