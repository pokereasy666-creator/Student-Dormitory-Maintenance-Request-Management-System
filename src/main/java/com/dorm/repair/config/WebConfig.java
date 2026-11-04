package com.dorm.repair.config;

import com.dorm.repair.interceptor.AuthInterceptor;
import com.dorm.repair.service.FileStorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置：注册登录/权限拦截器，映射上传图片的访问路径。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final FileStorageService fileStorageService;

    public WebConfig(AuthInterceptor authInterceptor, FileStorageService fileStorageService) {
        this.authInterceptor = authInterceptor;
        this.fileStorageService = fileStorageService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                // 默认拦截所有请求（包括 /uploads/** 图片，未登录不能看）
                .addPathPatterns("/**")
                // 放行：登录、注册页面，静态样式，Spring Boot 的错误页
                .excludePathPatterns("/login", "/register", "/css/**", "/error", "/favicon.ico");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 访问 /uploads/20261104/xxx.jpg 时，到本地上传目录中读取对应文件
        String location = fileStorageService.getRootDir().toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
