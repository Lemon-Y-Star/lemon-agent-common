package com.lemon.lemonagent.dal.entity;

import lombok.Data;

import java.time.Instant;

/** 长期记忆（表 agent_memory，用户级）。 */
@Data
public class AgentMemoryEntity {

    /** 记忆 ID */
    private Long id;

    /** 归属用户 */
    private Long userId;

    /** 记忆内容 */
    private String content;

    /** 是否生效：1 生效 / 0 停用 */
    private Integer enabled;

    /** 创建时间 */
    private Instant createdAt;

}
