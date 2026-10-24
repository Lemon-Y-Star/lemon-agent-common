package com.lemon.lemonagent.dal.repository;

import com.lemon.lemonagent.dal.entity.AgentSessionEntity;

import java.util.List;

/** 对话会话仓储端口。 */
public interface AgentSessionRepository {

    /**
     * 插入会话，回填自增主键。
     *
     * @param entity 入参：待插入实体
     * @return 出参：影响行数
     */
    int insert(AgentSessionEntity entity);

    /**
     * 按主键查用户的会话。
     *
     * @param id     入参：会话 ID
     * @param userId 入参：归属用户
     * @return 出参：会话实体；不存在或不属于该用户返回 null
     */
    AgentSessionEntity findByIdAndUser(Long id, Long userId);

    /**
     * 用户的会话列表（按 lastActiveAt 倒序，带消息条数）。
     *
     * @param userId 入参：归属用户
     * @param offset 入参：分页偏移
     * @param limit  入参：页大小
     * @return 出参：会话列表
     */
    List<AgentSessionEntity> findByUser(Long userId, int offset, int limit);

    /**
     * 用户会话总数。
     *
     * @param userId 入参：归属用户
     * @return 出参：总数
     */
    long countByUser(Long userId);

    /**
     * 刷新最近活跃时间。
     *
     * @param id 入参：会话 ID
     * @return 出参：影响行数
     */
    int touch(Long id);

    /**
     * 会话消息条数。
     *
     * @param id 入参：会话 ID
     * @return 出参：消息数
     */
    long messageCount(Long id);

    /**
     * 重命名会话。
     *
     * @param id    入参：会话 ID
     * @param title 入参：新标题
     * @return 出参：影响行数
     */
    int rename(Long id, String title);

    /**
     * 级联删除会话：消息 → 事件 → 运行 → 会话（同一数据源内按依赖顺序删除）。
     *
     * @param id     入参：会话 ID
     * @param userId 入参：归属用户
     * @return 出参：删除的会话行数（0 = 会话不存在或不属于该用户）
     */
    int deleteCascade(Long id, Long userId);

}
