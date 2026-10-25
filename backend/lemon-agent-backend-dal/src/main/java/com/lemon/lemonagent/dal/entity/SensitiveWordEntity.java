package com.lemon.lemonagent.dal.entity;

import lombok.Data;

import java.time.Instant;

/** 敏感词（表 sensitive_word）：平台级词库，输入拦截与输出脱敏共用。 */
@Data
public class SensitiveWordEntity {

    /** 主键 */
    private Long id;

    /** 敏感词 */
    private String word;

    /** 创建时间 */
    private Instant createdAt;

}
