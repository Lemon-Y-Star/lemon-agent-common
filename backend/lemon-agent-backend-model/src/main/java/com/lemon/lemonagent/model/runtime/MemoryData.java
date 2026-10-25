package com.lemon.lemonagent.model.runtime;

import lombok.Data;

/** 长期记忆条目。 */
@Data
public class MemoryData {

    /** ID */
    private Long id;

    /** 内容 */
    private String content;

    /** 是否生效 */
    private boolean enabled;

    /** 创建时间 */
    private String createdAt;
}
