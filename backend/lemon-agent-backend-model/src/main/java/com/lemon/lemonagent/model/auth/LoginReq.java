package com.lemon.lemonagent.model.auth;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/**
 * 登录请求：业务字段 + 从 {@link BaseReq} 继承的路由字段。
 */
@Getter
@Setter
public class LoginReq extends BaseReq {

    /** 用户名 */
    private String username;

    /** 口令明文（TLS 内传输，服务端只留 PBKDF2 哈希） */
    private String password;

    /**
     * 填充路由三元组。
     *
     * @param source 调用来源（X-Source 请求头）
     */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.AUTH_LOGIN);
    }

}
