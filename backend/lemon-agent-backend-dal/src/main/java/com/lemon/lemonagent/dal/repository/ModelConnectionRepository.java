package com.lemon.lemonagent.dal.repository;

import com.lemon.lemonagent.dal.entity.ModelConnectionEntity;

import java.util.List;

/**
 * 个人模型连接仓储端口。
 *
 * <p>所有方法都按 {@code userId} 收口：用户只能看到与操作自己的连接，
 * 越权访问表现为"查不到 / 删不动"（404 语义），不在 SQL 层暴露他人数据。</p>
 */
public interface ModelConnectionRepository {

    /**
     * 查用户的全部连接（创建时间倒序）。
     *
     * @param userId 入参：用户 ID
     * @return 出参：连接列表
     */
    List<ModelConnectionEntity> findByUser(Long userId);

    /**
     * 按 ID 查用户的连接。
     *
     * @param id     入参：连接 ID
     * @param userId 入参：归属用户
     * @return 出参：连接实体；不存在或不属于该用户返回 null
     */
    ModelConnectionEntity findByIdAndUser(Long id, Long userId);

    /**
     * 插入连接，回填自增主键。
     *
     * @param entity 入参：待插入实体
     * @return 出参：影响行数
     */
    int insert(ModelConnectionEntity entity);

    /**
     * 更新连接（不含 createdAt）。
     *
     * @param entity 入参：待更新实体
     * @return 出参：影响行数
     */
    int update(ModelConnectionEntity entity);

    /**
     * 删除连接。
     *
     * @param id     入参：连接 ID
     * @param userId 入参：归属用户
     * @return 出参：影响行数
     */
    int delete(Long id, Long userId);

}
