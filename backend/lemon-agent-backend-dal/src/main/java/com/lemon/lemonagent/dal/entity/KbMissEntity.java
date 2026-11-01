package com.lemon.lemonagent.dal.entity;

import lombok.Data;

import java.time.Instant;

/** 知识库检索未命中记录（v4.1：0 命中 query 按 (user_id, query) 去重计数）。 */
@Data
public class KbMissEntity {

    private Long id;

    private Long userId;

    private String query;

    /** 累计未命中次数 */
    private Integer hits;

    private Instant lastSeenAt;

    private Instant createdAt;

}
