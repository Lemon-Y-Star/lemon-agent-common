package com.lemon.lemonagent.servicecore.auth;

/**
 * 访问令牌的签发与校验端口。
 *
 * <p>认证是平台级基础设施：v1 用进程内存实现（单实例工作台够用），
 * 多实例部署时替换为 Redis 实现即可，认证链路其余代码不动。</p>
 */
public interface TokenStore {

    /**
     * 为用户签发新令牌。
     *
     * @param userId 入参：用户 ID
     * @return 出参：不透明令牌（Authorization: Bearer 携带）
     */
    String issue(Long userId);

    /**
     * 解析令牌对应的用户。
     *
     * @param token 入参：令牌
     * @return 出参：用户 ID；令牌无效或已过期返回 null
     */
    Long resolve(String token);

    /**
     * 吊销令牌（登出 / 改密后强制下线）。
     *
     * @param token 入参：令牌
     */
    void revoke(String token);

    /**
     * 吊销某用户的全部令牌（改密成功后强制重新登录）。
     *
     * @param userId 入参：用户 ID
     */
    void revokeByUser(Long userId);

}
