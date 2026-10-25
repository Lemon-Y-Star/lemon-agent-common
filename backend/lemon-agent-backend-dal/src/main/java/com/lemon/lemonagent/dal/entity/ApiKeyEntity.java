package com.lemon.lemonagent.dal.entity;

import lombok.Data;

import java.time.Instant;

/** API 分发密钥（表 api_key）：只存 SHA-256 哈希，明文只在创建响应里出现一次。 */
@Data
public class ApiKeyEntity {

    /** 主键 */
    private Long id;

    /** 归属用户 */
    private Long userId;

    /** 密钥名称 */
    private String name;

    /** 明文前缀（sk- + 前 8 位），列表展示用 */
    private String keyPrefix;

    /** SHA-256 hex（整串密钥） */
    private String keyHash;

    /** 1=启用 / 0=已吊销 */
    private Integer status;

    /** 最近一次调用时间 */
    private Instant lastUsedAt;

    /** 创建时间 */
    private Instant createdAt;

    /** 更新时间 */
    private Instant updatedAt;

}
