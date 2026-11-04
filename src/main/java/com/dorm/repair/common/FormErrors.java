package com.dorm.repair.common;

import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

/**
 * 表单错误工具：取第一条校验错误作为提示语（用于"跳转后提示"的场景，如评价、完成维修）。
 */
public final class FormErrors {

    private FormErrors() {
    }

    public static String first(BindingResult result) {
        FieldError error = result.getFieldError();
        if (error == null) {
            return "输入信息有误";
        }
        // isBindingFailure：类型转换失败（如星级传了非数字），默认提示是英文，这里换成中文
        if (error.isBindingFailure()) {
            return "输入格式不正确";
        }
        return error.getDefaultMessage();
    }
}
