package com.lemon.lemonagent.dal.entity;

import lombok.Data;

import java.time.Instant;

/** 运行事件（表 agent_run_event）：append-only，UNIQUE(run_id, seq) 是并发正确性最后一道闸。 */
@Data
public class AgentRunEventEntity {

    /** 自增主键 */
    private Long id;

    /** 所属运行 */
    private Long runId;

    /** run 内序号（从 1 连续递增） */
    private long seq;

    /** 事件类型（线格式名） */
    private String eventType;

    /** 事件负载 JSON */
    private String payload;

    /** 创建时间 */
    private Instant createdAt;

}
