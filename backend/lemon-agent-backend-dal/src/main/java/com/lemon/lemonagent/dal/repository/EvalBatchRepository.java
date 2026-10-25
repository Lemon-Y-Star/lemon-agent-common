package com.lemon.lemonagent.dal.repository;

import com.lemon.lemonagent.dal.entity.EvalBatchEntity;

import java.util.List;

/** 评测批次仓储端口。 */
public interface EvalBatchRepository {

    /**
     * 插入批次（回填自增 ID）。
     *
     * @param entity 入参：批次
     * @return 出参：影响行数
     */
    int insert(EvalBatchEntity entity);

    /**
     * 按 ID 查（带归属校验）。
     *
     * @param id     入参：批次 ID
     * @param userId 入参：用户
     * @return 出参：批次；不存在返回 null
     */
    EvalBatchEntity findByIdAndUser(Long id, Long userId);

    /**
     * 某 Agent 的最近批次（倒序）。
     *
     * @param agentId 入参：Agent ID
     * @param userId  入参：用户
     * @param limit   入参：条数上限
     * @return 出参：批次列表
     */
    List<EvalBatchEntity> listByAgent(Long agentId, Long userId, int limit);

}
