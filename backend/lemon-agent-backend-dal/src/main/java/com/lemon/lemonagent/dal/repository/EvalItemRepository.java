package com.lemon.lemonagent.dal.repository;

import com.lemon.lemonagent.dal.entity.EvalItemEntity;

import java.util.List;

/** 评测用例项仓储端口。 */
public interface EvalItemRepository {

    /**
     * 插入用例项。
     *
     * @param entity 入参：用例项
     * @return 出参：影响行数
     */
    int insert(EvalItemEntity entity);

    /**
     * 某批次的全部用例项（按 seq 升序）。
     *
     * @param batchId 入参：批次 ID
     * @return 出参：用例项列表
     */
    List<EvalItemEntity> listByBatch(Long batchId);

}
