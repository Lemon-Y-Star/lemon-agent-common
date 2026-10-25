package com.lemon.lemonagent.model.runtime;

import lombok.Data;

/** API 密钥列表项（不含哈希与明文）。 */
@Data
public class ApiKeyData {

    /** 密钥 ID */
    private Long id;

    /** 名称 */
    private String name;

    /** 明文前缀（sk- + 前 8 位） */
    private String keyPrefix;

    /** 启用状态 */
    private boolean enabled;

    /** 最近调用时间 */
    private String lastUsedAt;

    /** 创建时间 */
    private String createdAt;

}
