package com.lemon.lemonagent.dal.entity;

import lombok.Data;

import java.time.Instant;

/** 对话会话（表 agent_session）。 */
@Data
public class AgentSessionEntity {

    /** 会话 ID */
    private Long id;

    /** 归属用户 */
    private Long userId;

    /** 标题 */
    private String title;

    /** 最近活跃时间（发消息 / 建 run 刷新） */
    private Instant lastActiveAt;

    /** 创建时间 */
    private Instant createdAt;

    /** 更新时间 */
    private Instant updatedAt;

}
