package com.lemon.lemonagent.dal.repository;

import com.lemon.lemonagent.dal.entity.UserAccountEntity;

/**
 * 用户仓储端口。
 */
public interface UserAccountRepository {

    /**
     * 按用户名查用户。
     *
     * @param username 入参：用户名
     * @return 出参：用户实体；不存在返回 null
     */
    UserAccountEntity findByUsername(String username);

    /**
     * 按主键查用户。
     *
     * @param id 入参：用户 ID
     * @return 出参：用户实体；不存在返回 null
     */
    UserAccountEntity findById(Long id);

    /**
     * 更新口令存储串。
     *
     * @param id           入参：用户 ID
     * @param passwordHash 入参：新的 PBKDF2 存储串
     * @return 出参：影响的行数
     */
    int updatePasswordHash(Long id, String passwordHash);

}
