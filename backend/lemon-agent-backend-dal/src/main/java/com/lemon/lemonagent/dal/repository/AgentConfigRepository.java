package com.lemon.lemonagent.dal.repository;

import com.lemon.lemonagent.dal.entity.AgentConfigEntity;

import java.util.List;

/** Agent 配置仓储端口（平台公共 + 用户自建共用一张表，userId 区分）。 */
public interface AgentConfigRepository {

    /**
     * 平台公共 Agent（user_id IS NULL，按 id 排序）。
     *
     * @return 出参：平台公共 Agent 列表
     */
    List<AgentConfigEntity> findPlatform();

    /**
     * 用户自建 Agent（更新时间倒序）。
     *
     * @param userId 入参：归属用户
     * @return 出参：自建 Agent 列表
     */
    List<AgentConfigEntity> findByUser(Long userId);

    /**
     * 按 ID 查（平台公共或本人自建均可见）。
     *
     * @param id     入参：Agent ID
     * @param userId 入参：用户（用于过滤自建；平台公共对所有人可见）
     * @return 出参：Agent 实体；不可见返回 null
     */
    AgentConfigEntity findByIdVisible(Long id, Long userId);

    /**
     * 按主键查（不限归属）。
     *
     * @param id 入参：Agent ID
     * @return 出参：Agent 实体；不存在返回 null
     */
    AgentConfigEntity findById(Long id);

    /**
     * 统计挂载了某工作流的 Agent 数（删除工作流前的保护检查）。
     *
     * @param workflowId 入参：工作流 ID
     * @return 出参：挂载中的 Agent 数
     */
    long countByWorkflowId(Long workflowId);

    /**
     * 插入自建 Agent，回填自增主键。
     *
     * @param entity 入参：待插入实体
     * @return 出参：影响行数
     */
    int insert(AgentConfigEntity entity);

    /**
     * 更新自建 Agent（配置变更 version +1）。
     *
     * @param entity 入参：待更新实体
     * @return 出参：影响行数
     */
    int update(AgentConfigEntity entity);

    /**
     * 删除自建 Agent。
     *
     * @param id     入参：Agent ID
     * @param userId 入参：归属用户
     * @return 出参：影响行数
     */
    int delete(Long id, Long userId);

}
