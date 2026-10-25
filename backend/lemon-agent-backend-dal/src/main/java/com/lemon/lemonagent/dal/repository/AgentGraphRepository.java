package com.lemon.lemonagent.dal.repository;

import com.lemon.lemonagent.dal.entity.AgentGraphEntity;

/** Agent 执行图仓储端口。 */
public interface AgentGraphRepository {

    /**
     * 当前生效版本（status=1）。
     *
     * @param agentId 入参：Agent ID
     * @return 出参：图实体；没有则 null
     */
    AgentGraphEntity findActive(Long agentId);

    /**
     * 插入新版本（调用方负责把旧版本置 0）。
     *
     * @param entity 入参：图实体
     * @return 出参：影响行数
     */
    int insert(AgentGraphEntity entity);

    /**
     * 把某 Agent 的生效版本全部置为历史（保存新版本前调用）。
     *
     * @param agentId 入参：Agent ID
     * @return 出参：影响行数
     */
    int deactivateAll(Long agentId);

    /**
     * 删除某 Agent 的全部图版本（Agent 被删除时级联清理，避免孤儿行）。
     *
     * @param agentId 入参：Agent ID
     * @return 出参：影响行数
     */
    int deleteByAgent(Long agentId);

}
