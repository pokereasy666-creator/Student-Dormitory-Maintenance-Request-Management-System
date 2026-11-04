package com.dorm.repair.interceptor;

import com.dorm.repair.common.Constants;
import com.dorm.repair.common.ForbiddenException;
import com.dorm.repair.dto.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录 + 角色权限拦截器（角色权限隔离的核心）。
 * <ol>
 *   <li>未登录：重定向到登录页；</li>
 *   <li>/student/** 只允许学生访问，/repair/** 只允许维修人员访问，否则返回 403。</li>
 * </ol>
 * 因为是在服务端拦截，即使学生在地址栏手动输入维修人员的 URL（或伪造 POST 请求）也无法访问。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        HttpSession session = request.getSession(false);
        LoginUser user = session == null ? null : (LoginUser) session.getAttribute(Constants.SESSION_USER);
        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }

        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/student/") && !user.isStudent()) {
            throw new ForbiddenException("该功能仅限学生使用");
        }
        if (path.startsWith("/repair/") && !user.isRepairer()) {
            throw new ForbiddenException("该功能仅限维修人员使用");
        }
        return true;
    }
}
