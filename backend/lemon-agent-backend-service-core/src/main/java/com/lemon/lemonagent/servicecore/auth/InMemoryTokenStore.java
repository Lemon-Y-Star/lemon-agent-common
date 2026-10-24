package com.lemon.lemonagent.servicecore.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 令牌的进程内存实现。
 *
 * <p>签发用 {@code SecureRandom} 32 字节十六进制串（不透明令牌，无自包含信息，天然防篡改——
 * 有效期与归属都在服务端这一张表里）。过期清理采用惰性策略：resolve 时判过期即删，
 * issue 时顺带全表清一次（登录频次低，全表扫描成本可忽略）。</p>
 */
@Service
public class InMemoryTokenStore implements TokenStore {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    private final long ttlMillis;

    private final SecureRandom random = new SecureRandom();

    public InMemoryTokenStore(@Value("${lemon.auth.token-ttl-hours:8}") long ttlHours) {
        this.ttlMillis = ttlHours * 3600_000L;
    }

    @Override
    public String issue(Long userId) {
        evictExpired();
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = HexFormat.of().formatHex(raw);
        sessions.put(token, new Session(userId, Instant.now().plusMillis(ttlMillis)));
        return token;
    }

    @Override
    public Long resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Session session = sessions.get(token);
        if (session == null) {
            return null;
        }
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(token);
            return null;
        }
        return session.userId();
    }

    @Override
    public void revoke(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    @Override
    public void revokeByUser(Long userId) {
        sessions.entrySet().removeIf(entry -> entry.getValue().userId().equals(userId));
    }

    private void evictExpired() {
        Instant now = Instant.now();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private record Session(Long userId, Instant expiresAt) {
    }

}
