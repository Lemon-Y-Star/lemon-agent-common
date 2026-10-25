package com.lemon.lemonagent.dal.entity;

import lombok.Data;

import java.time.Instant;

/** 知识库文档（表 kb_document）。 */
@Data
public class KbDocumentEntity {

    /** 文档 ID */
    private Long id;

    /** 归属用户 */
    private Long userId;

    /** 标题 */
    private String title;

    /** 正文 */
    private String content;

    /** 创建时间 */
    private Instant createdAt;

    /** 更新时间 */
    private Instant updatedAt;

}
