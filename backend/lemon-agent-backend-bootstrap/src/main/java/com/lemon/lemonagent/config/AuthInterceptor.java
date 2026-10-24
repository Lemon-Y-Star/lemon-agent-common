package com.lemon.lemonagent.config;

import com.lemon.lemonagent.servicecore.auth.TokenStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器：校验 {@code Authorization: Bearer <token>}，把 userId 挂到请求属性。
 *
 * <p>无效 / 缺失令牌直接 HTTP 401——这是"未认证"的传输层语义（契约 §1.2），
 * 与业务层失败（HTTP 200 + 包络 code）分离。前端统一拦截 401 清登录态跳登录页（PRD FR-1）。</p>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** 请求属性键：拦截器解析出的用户 ID，Controller 用 @RequestAttribute 读取。 */
    public static final String ATTR_USER_ID = "lemon.userId";

    private final TokenStore tokenStore;

    public AuthInterceptor(TokenStore tokenStore) {
        this.tokenStore = tokenStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return reject(response);
        }
        Long userId = tokenStore.resolve(authorization.substring(7).trim());
        if (userId == null) {
            return reject(response);
        }
        request.setAttribute(ATTR_USER_ID, userId);
        return true;
    }

    private static boolean reject(HttpServletResponse response) throws Exception {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"登录状态已失效，请重新登录\",\"data\":null}");
        return false;
    }

}
