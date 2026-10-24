package com.lemon.lemonagent.model.modelconn;

import lombok.Data;

import java.util.List;

/**
 * 单条模型连接的对外数据。
 *
 * <p>密钥只出"有没有"与"末 4 位"（{@code hasSecret} / {@code secretTail}），
 * 明文与密文均不回传——轮换密钥走更新接口重新写入。</p>
 */
@Data
public class ModelConnectionData {

    /** 连接 ID */
    private Long id;

    /** 连接名称（用户可读） */
    private String name;

    /** 提供方标识（如 openai / deepseek / custom） */
    private String provider;

    /** OpenAI 兼容端点（如 https://api.deepseek.com/v1） */
    private String baseUrl;

    /** 是否已配置密钥 */
    private boolean hasSecret;

    /** 密钥末 4 位（脱敏展示用；未配置时为空） */
    private String secretTail;

    /** 该连接可用的模型名列表 */
    private List<String> models;

    /** 启用状态：true 启用 / false 停用 */
    private boolean enabled;

    /** 是否允许辅助功能（路由/摘要/向量）使用该连接 */
    private boolean allowAuxiliary;

    /** 创建时间（ISO-8601） */
    private String createdAt;

    /** 更新时间（ISO-8601） */
    private String updatedAt;

}
