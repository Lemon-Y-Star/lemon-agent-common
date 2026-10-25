package com.lemon.lemonagent.dal.repository;

import com.lemon.lemonagent.dal.entity.RunFeedbackEntity;

/** 运行反馈仓储端口。 */
public interface RunFeedbackRepository {

    /**
     * 某用户对某运行的反馈。
     *
     * @param runId  入参：运行 ID
     * @param userId 入参：用户
     * @return 出参：反馈；没有则 null
     */
    RunFeedbackEntity findByRunAndUser(Long runId, Long userId);

    /**
     * 插入或覆盖（一人一运行一条）。
     *
     * @param e 入参：反馈实体
     * @return 出参：影响行数
     */
    int upsert(RunFeedbackEntity e);

}
