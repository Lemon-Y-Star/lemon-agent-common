package com.lemon.lemonagent.dal.entity;

import lombok.Data;

import java.time.Instant;

/** Agent 执行图（表 agent_graph）：按 version 递增，status=1 为当前生效版本。 */
@Data
public class AgentGraphEntity {

    /** 主键 */
    private Long id;

    /** 所属 Agent */
    private Long agentId;

    /** 版本（保存 +1） */
    private Integer version;

    /** 图定义 JSON {nodes:[], edges:[]} */
    private String graphJson;

    /** 1=当前生效 / 0=历史版本 */
    private Integer status;

    /** 创建时间 */
    private Instant createdAt;

    /** 更新时间 */
    private Instant updatedAt;

}
