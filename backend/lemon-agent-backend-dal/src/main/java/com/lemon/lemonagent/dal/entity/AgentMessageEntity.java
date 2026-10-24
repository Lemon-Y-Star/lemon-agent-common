package com.lemon.lemonagent.dal.entity;

import lombok.Data;

import java.time.Instant;

/** 对话消息（表 agent_message）：给人看，与事件流解耦。 */
@Data
public class AgentMessageEntity {

    /** 消息 ID */
    private Long id;

    /** 所属会话 */
    private Long sessionId;

    /** 归属用户 */
    private Long userId;

    /** USER / ASSISTANT */
    private String role;

    /** 正文 */
    private String content;

    /** ASSISTANT 消息关联的运行 */
    private Long runId;

    /** 创建时间 */
    private Instant createdAt;

}
