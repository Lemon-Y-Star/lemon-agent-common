package com.lemon.lemonagent.dal.repository;

import com.lemon.lemonagent.dal.entity.RunArtifactEntity;

import java.util.List;

/**
 * 运行文件产物仓储（表 {@code run_artifact}，v4.4.4）。
 */
public interface RunArtifactRepository {

    /**
     * 新增产物。
     *
     * @param entity 入参：产物（须含 runId/userId/name/contentType/data）
     * @return 出参：自增主键
     */
    long insert(RunArtifactEntity entity);

    /**
     * 按运行列产物元数据（不含内容）。
     *
     * @param runId 入参：运行 ID
     * @param userId 入参：归属用户
     * @return 出参：产物列表
     */
    List<RunArtifactEntity> findByRun(Long runId, Long userId);

    /**
     * 按 ID 取产物（含内容，下载用；归属校验内聚）。
     *
     * @param id     入参：产物 ID
     * @param userId 入参：归属用户
     * @return 出参：产物；无权限或不存在返回 null
     */
    RunArtifactEntity findByIdAndUser(Long id, Long userId);

}
