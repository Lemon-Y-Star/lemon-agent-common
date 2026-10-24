package com.lemon.lemonagent.util.crypto;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 登录口令的 PBKDF2 哈希工具（JDK 自带 PBKDF2WithHmacSHA256，零第三方依赖）。
 *
 * <p>存储格式：{@code pbkdf2$<iterations>$<saltB64>$<hashB64>}。盐每次哈希随机生成（16 字节），
 * 校验时从存储串里读回盐与迭代数，规避"换迭代数要全量重哈希"的迁移问题。</p>
 *
 * <p>参数取值：迭代 21 万次、输出 256 位——2026 年 OWASP 对 PBKDF2-HMAC-SHA256 的建议区间，
 * 单次校验耗时约百毫秒级，登录场景可接受。</p>
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final String PREFIX = "pbkdf2";

    private PasswordHasher() {
    }

    /**
     * 生成随机盐（Base64）。
     *
     * @return 出参：16 字节随机盐
     */
    public static String newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * 计算口令哈希。
     *
     * @param password 入参：明文口令
     * @param saltB64  入参：Base64 盐
     * @return 出参：{@code pbkdf2$iterations$salt$hash} 存储串
     */
    public static String hash(String password, String saltB64) {
        try {
            byte[] salt = Base64.getDecoder().decode(saltB64);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
            byte[] hash = SecretKeyFactory.getInstance(ALGORITHM)
                    .generateSecret(spec).getEncoded();
            return PREFIX + "$" + ITERATIONS + "$" + saltB64 + "$"
                    + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("口令哈希失败", e);
        }
    }

    /**
     * 校验口令与存储串是否匹配；格式不合法一律视为不匹配，不抛异常（登录失败统一口径，避免泄露存储格式问题）。
     *
     * @param password 入参：待校验明文口令
     * @param stored   入参：库里的存储串
     * @return 出参：匹配返回 true
     */
    public static boolean verify(String password, String stored) {
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, expected.length * 8);
            byte[] actual = SecretKeyFactory.getInstance(ALGORITHM)
                    .generateSecret(spec).getEncoded();
            return MessageDigest.isEqual(expected, actual);
        } catch (Exception e) {
            return false;
        }
    }

}
