package com.lemon.lemonagent.dal.repository;

import com.lemon.lemonagent.dal.entity.WorkflowEntity;
import com.lemon.lemonagent.dal.entity.WorkflowVersionEntity;

import java.util.List;

/** 工作流仓储端口。 */
public interface WorkflowRepository {

    /**
     * 当前用户的全部工作流（按 ID 倒序）。
     *
     * @param userId 入参：用户 ID
     * @return 出参：工作流列表
     */
    List<WorkflowEntity> findByUser(Long userId);

    /**
     * 全部启用中的工作流（启动时回放为 run_wf_* 工具）。
     *
     * @return 出参：工作流列表
     */
    List<WorkflowEntity> findAllEnabled();

    /**
     * 按 ID 读取（不校验归属，由领域服务校验）。
     *
     * @param id 入参：工作流 ID
     * @return 出参：实体；不存在则 null
     */
    WorkflowEntity findById(Long id);

    /**
     * 插入工作流。
     *
     * @param e 入参：实体
     * @return 出参：影响行数
     */
    int insert(WorkflowEntity e);

    /**
     * 工作流当前生效版本（status=1）。
     *
     * @param workflowId 入参：工作流 ID
     * @return 出参：版本实体；没有则 null
     */
    WorkflowVersionEntity findActive(Long workflowId);

    /**
     * 读取指定版本。
     *
     * @param workflowId 入参：工作流 ID
     * @param version    入参：版本号
     * @return 出参：版本实体；没有则 null
     */
    WorkflowVersionEntity findVersion(Long workflowId, Integer version);

    /**
     * 全部版本（按版本倒序）。
     *
     * @param workflowId 入参：工作流 ID
     * @return 出参：版本列表
     */
    List<WorkflowVersionEntity> findVersions(Long workflowId);

    /**
     * 插入新版本（调用方负责把旧版本置 0）。
     *
     * @param e 入参：版本实体
     * @return 出参：影响行数
     */
    int insertVersion(WorkflowVersionEntity e);

    /**
     * 把某工作流的生效版本全部置为历史（保存新版本前调用）。
     *
     * @param workflowId 入参：工作流 ID
     * @return 出参：影响行数
     */
    int deactivateAll(Long workflowId);

    /**
     * 删除工作流本体（本人，调用方先做挂载检查）。
     *
     * @param id     入参：工作流 ID
     * @param userId 入参：归属用户
     * @return 出参：影响行数
     */
    int delete(Long id, Long userId);

    /**
     * 级联删除全部版本（删除本体前调用）。
     *
     * @param workflowId 入参：工作流 ID
     * @return 出参：影响行数
     */
    int deleteVersions(Long workflowId);

}
