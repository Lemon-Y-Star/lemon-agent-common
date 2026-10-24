package com.lemon.lemonagent.dal.repository;

import com.lemon.lemonagent.dal.entity.AgentRunEventEntity;

import java.util.List;

/** 运行事件仓储端口（append-only，先落库再推送）。 */
public interface AgentRunEventRepository {

    /**
     * 追加事件。
     *
     * @param entity 入参：事件（runId/seq/type/payload）
     * @return 出参：影响行数（0 = UNIQUE(run_id,seq) 冲突，seq 已被占用）
     */
    int insert(AgentRunEventEntity entity);

    /**
     * 重放查询：seq 大于游标的事件（升序）。
     *
     * @param runId    入参：运行 ID
     * @param afterSeq 入参：游标（不含）
     * @return 出参：事件列表
     */
    List<AgentRunEventEntity> findByRunAfter(Long runId, long afterSeq);

}
