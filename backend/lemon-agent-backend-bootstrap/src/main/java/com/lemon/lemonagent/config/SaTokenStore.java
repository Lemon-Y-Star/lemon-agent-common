package com.lemon.lemonagent.config;

import cn.dev33.satoken.stp.StpUtil;
import com.lemon.lemonagent.servicecore.auth.TokenStore;
import org.springframework.stereotype.Component;

/**
 * 令牌端口的 Sa-Token 实现（登录会话全部交给 Sa-Token：签发、校验、吊销、踢人下线）。
 *
 * <p>选择显式 API 而不是上下文读取（{@code StpUtil.getLoginIdDefaultNull()}）：
 * {@code resolve} / {@code revoke} 直接操作传入的 token 值，不依赖请求线程上下文，
 * 单测与异步场景都能用。{@code issue} 里的 {@code StpUtil.login} 需要请求上下文，
 * 调用方（认证领域服务）始终在 Controller 请求线程内，满足前提。</p>
 *
 * <p>多实例部署时把 sa-token 的存储源换成 Redis（sa-token-redis-jackson）即可，
 * 本端口与其余认证链路代码不动。</p>
 */
@Component
public class SaTokenStore implements TokenStore {

    @Override
    public String issue(Long userId) {
        StpUtil.login(userId);
        return StpUtil.getTokenValue();
    }

    @Override
    public Long resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Object loginId = StpUtil.getLoginIdByToken(token);
        if (loginId == null) {
            return null;
        }
        try {
            return Long.valueOf(loginId.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void revoke(String token) {
        StpUtil.logoutByTokenValue(token);
    }

    @Override
    public void revokeByUser(Long userId) {
        StpUtil.logout(userId);
    }

}
