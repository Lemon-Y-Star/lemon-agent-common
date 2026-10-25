package com.lemon.lemonagent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 装配：把认证拦截器挂到 /api/** 上，登录接口与活性检查放行。
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                // 登录与健康检查不需要令牌；登出 / 改密 / me 都需要已认证身份
                .excludePathPatterns("/api/v1/auth/login", "/api/v1/open/**", "/actuator/health");
    }

}
