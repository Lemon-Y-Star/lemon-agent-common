package com.lemon.lemonagent.dal.repository;

import com.lemon.lemonagent.dal.entity.ApiKeyEntity;

import java.util.List;

/** API 分发密钥仓储端口。 */
public interface ApiKeyRepository {

    /**
     * 用户的全部密钥（按 ID 倒序）。
     *
     * @param userId 入参：用户
     * @return 出参：密钥列表
     */
    List<ApiKeyEntity> findByUser(Long userId);

    /**
     * 按哈希查（鉴权路径）。
     *
     * @param keyHash 入参：SHA-256 hex
     * @return 出参：密钥；没有则 null
     */
    ApiKeyEntity findByHash(String keyHash);

    /**
     * 插入。
     *
     * @param e 入参：实体
     * @return 出参：影响行数
     */
    int insert(ApiKeyEntity e);

    /**
     * 吊销（本人密钥置 0）。
     *
     * @param id     入参：密钥 ID
     * @param userId 入参：归属用户
     * @return 出参：影响行数
     */
    int revoke(Long id, Long userId);

    /**
     * 回写最近调用时间。
     *
     * @param id 入参：密钥 ID
     * @return 出参：影响行数
     */
    int touch(Long id);

}
