package com.lemon.lemonagent.model.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录身份（{@code GET /api/v1/auth/me} 返回的数据，PRD FR-1）。
 */
@Data
@AllArgsConstructor
public class UserData {

    /** 用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 角色 */
    private String role;

}
