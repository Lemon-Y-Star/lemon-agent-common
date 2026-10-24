package com.lemon.lemonagent.dal.repository;

import com.lemon.lemonagent.dal.entity.McpConnectionEntity;

import java.util.List;

/**
 * MCP 连接仓储端口（平台公共 + 用户自建共用一张表，userId 区分）。
 */
public interface McpConnectionRepository {

    /**
     * 查平台公共连接（user_id IS NULL，按名称排序）。
     *
     * @return 出参：平台公共连接列表
     */
    List<McpConnectionEntity> findPlatform();

    /**
     * 查用户的自建连接（创建时间倒序）。
     *
     * @param userId 入参：用户 ID
     * @return 出参：自建连接列表
     */
    List<McpConnectionEntity> findByUser(Long userId);

    /**
     * 按 ID 查用户的自建连接。
     *
     * @param id     入参：连接 ID
     * @param userId 入参：归属用户
     * @return 出参：连接实体；不存在、不属于该用户或为平台公共返回 null
     */
    McpConnectionEntity findByIdAndUser(Long id, Long userId);

    /**
     * 插入自建连接，回填自增主键。
     *
     * @param entity 入参：待插入实体
     * @return 出参：影响行数
     */
    int insert(McpConnectionEntity entity);

    /**
     * 更新自建连接。
     *
     * @param entity 入参：待更新实体
     * @return 出参：影响行数
     */
    int update(McpConnectionEntity entity);

    /**
     * 删除自建连接。
     *
     * @param id     入参：连接 ID
     * @param userId 入参：归属用户
     * @return 出参：影响行数
     */
    int delete(Long id, Long userId);

}
