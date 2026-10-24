package com.lemon.lemonagent.model.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录成功返回的数据：后续请求以 {@code Authorization: Bearer <token>} 携带。
 */
@Data
@AllArgsConstructor
public class LoginData {

    /** 访问令牌（服务端签发，内存会话存储，过期需重新登录） */
    private String token;

    /** 用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 角色（v1 仅 admin） */
    private String role;

}
