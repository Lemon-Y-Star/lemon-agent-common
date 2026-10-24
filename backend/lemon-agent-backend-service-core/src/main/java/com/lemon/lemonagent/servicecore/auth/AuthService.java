package com.lemon.lemonagent.servicecore.auth;

import com.lemon.lemonagent.model.auth.LoginData;
import com.lemon.lemonagent.model.auth.UserData;

/**
 * 认证领域服务：登录 / 登出 / 当前身份 / 修改密码。
 */
public interface AuthService {

    /**
     * 登录：校验口令并签发令牌。
     *
     * @param username 入参：用户名
     * @param password 入参：口令明文
     * @return 出参：令牌与身份
     */
    LoginData login(String username, String password);

    /**
     * 登出：吊销令牌。
     *
     * @param token 入参：当前令牌
     */
    void logout(String token);

    /**
     * 查询当前登录身份。
     *
     * @param userId 入参：用户 ID
     * @return 出参：身份信息
     */
    UserData me(Long userId);

    /**
     * 修改密码：校验当前口令与新密码策略，成功后吊销该用户全部会话。
     *
     * @param userId         入参：用户 ID
     * @param currentPassword 入参：当前口令
     * @param newPassword    入参：新口令
     */
    void changePassword(Long userId, String currentPassword, String newPassword);

}
