package com.lemon.lemonagent.model.runtime;

import lombok.Data;

/** 知识库文档（列表/新增返回）。 */
@Data
public class KbDocumentData {

    /** ID */
    private Long id;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 创建时间 */
    private String createdAt;
}
