package com.lemon.lemonagent.dal.repository;

import com.lemon.lemonagent.dal.entity.AgentShareEntity;

/** Agent 分享配置仓储端口（v4.6.0）：每 Agent 一条（upsert 语义）。 */
public interface AgentShareRepository {

    /** 按公开标识查（公开对话页入口校验用）。 */
    AgentShareEntity findByCode(String shareCode);

    /** 按 Agent 查（分享配置管理用）。 */
    AgentShareEntity findByAgentId(Long agentId);

    /** 插入或更新（按 agent_id upsert）。 */
    int upsert(AgentShareEntity entity);
}
