package com.lemon.lemonagent.dal.repository;

import com.lemon.lemonagent.dal.entity.AgentMessageEntity;

import java.util.List;

/** 对话消息仓储端口。 */
public interface AgentMessageRepository {

    /**
     * 插入消息，回填自增主键。
     *
     * @param entity 入参：待插入实体
     * @return 出参：影响行数
     */
    int insert(AgentMessageEntity entity);

    /**
     * 会话消息（messageId 升序）。
     *
     * @param sessionId 入参：会话 ID
     * @param userId    入参：归属用户
     * @return 出参：消息列表
     */
    List<AgentMessageEntity> findBySession(Long sessionId, Long userId);

    /**
     * 会话消息条数。
     *
     * @param sessionId 入参：会话 ID
     * @return 出参：条数
     */
    long countBySession(Long sessionId);

}
