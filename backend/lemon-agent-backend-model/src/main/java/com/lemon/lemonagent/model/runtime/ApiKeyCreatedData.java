package com.lemon.lemonagent.model.runtime;

import lombok.Data;

/** 创建成功的密钥（明文只在此响应里出现一次）。 */
@Data
public class ApiKeyCreatedData {

    /** 密钥 ID */
    private Long id;

    /** 名称 */
    private String name;

    /** 完整明文密钥（sk-…，仅此一次） */
    private String key;

}
