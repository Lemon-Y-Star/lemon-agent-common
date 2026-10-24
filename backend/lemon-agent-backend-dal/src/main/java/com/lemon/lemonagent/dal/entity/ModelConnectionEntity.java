package com.lemon.lemonagent.dal.entity;

import lombok.Data;

import java.time.Instant;

/**
 * 个人模型连接（表 {@code model_connection}，PRD FR-13 的 BYOK 连接）。
 *
 * <p>{@code apiKeyCipher} 是 AES-GCM 密文（CryptoUtil 产出），库里永无明文。</p>
 */
@Data
public class ModelConnectionEntity {

    /** 主键 */
    private Long id;

    /** 归属用户 */
    private Long userId;

    /** 连接名称 */
    private String name;

    /** 提供方标识 */
    private String provider;

    /** OpenAI 兼容端点 */
    private String baseUrl;

    /** API 密钥密文 */
    private String apiKeyCipher;

    /** 可用模型名列表（JSON 数组字符串） */
    private String modelsJson;

    /** 是否允许辅助功能使用：1 是 / 0 否 */
    private Integer allowAuxiliary;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 创建时间 */
    private Instant createdAt;

    /** 更新时间 */
    private Instant updatedAt;

}
