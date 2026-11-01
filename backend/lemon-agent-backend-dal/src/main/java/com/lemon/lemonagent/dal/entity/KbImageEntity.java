package com.lemon.lemonagent.dal.entity;

import lombok.Data;

import java.time.Instant;

/** 知识库文档图片（v4.1 多模态：markdown 图链随文档入库，检索命中随文带出）。 */
@Data
public class KbImageEntity {

    private Long id;

    private Long userId;

    private Long docId;

    private String url;

    private String alt;

    private Instant createdAt;

}
