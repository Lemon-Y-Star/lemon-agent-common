package com.lemon.lemonagent.dal.entity;

import lombok.Data;

import java.time.Instant;

/**
 * 平台用户（表 {@code user_account}）。
 *
 * <p>口令只存 PBKDF2 存储串（{@code pbkdf2$iters$salt$hash}），明文在任何环节都不落库、不打日志。</p>
 */
@Data
public class UserAccountEntity {

    /** 主键 */
    private Long id;

    /** 用户名（唯一） */
    private String username;

    /** 口令存储串 */
    private String passwordHash;

    /** 角色（v1 仅 admin） */
    private String role;

    /** 状态：1 启用 / 0 禁用 */
    private Integer status;

    /** 创建时间 */
    private Instant createdAt;

    /** 更新时间 */
    private Instant updatedAt;

}
