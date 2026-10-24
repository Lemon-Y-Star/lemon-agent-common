package com.lemon.lemonagent.util.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 连接密钥的对称加密工具（AES-256-GCM），供 apiKey / secret 落库前加密、读取后解密使用。
 *
 * <p>静态初始化式：密钥由组合根在启动时通过 {@link #init(String)} 注入（application.yml 的
 * {@code lemon.crypto.secret}，Base64 编码的 32 字节 AES key）。在 init 之前调用加解密会直接抛
 * {@link IllegalStateException}——密钥没配好属于装配错误，宁可启动失败也不能静默落明文。</p>
 *
 * <p>密文格式：{@code base64(iv(12B) || ciphertext+tag)}，IV 每次加密随机生成（GCM 要求同一 key
 * 不复用 IV，随机 12B 在本项目的写入频次下碰撞概率可忽略）。</p>
 */
public final class CryptoUtil {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private static volatile SecretKeySpec key;

    private CryptoUtil() {
    }

    /**
     * 启动时注入密钥。
     *
     * @param base64Key 入参：Base64 编码的 32 字节 AES 密钥
     */
    public static void init(String base64Key) {
        byte[] raw = Base64.getDecoder().decode(base64Key);
        if (raw.length != 32) {
            throw new IllegalArgumentException("AES 密钥必须为 32 字节，当前 " + raw.length + " 字节");
        }
        key = new SecretKeySpec(raw, "AES");
    }

    /**
     * 加密明文，返回 base64(iv || cipher)。
     *
     * @param plain 入参：明文密钥（如 apiKey）
     * @return 出参：可落库的密文
     */
    public static String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(packed);
        } catch (Exception e) {
            throw new IllegalStateException("密钥加密失败", e);
        }
    }

    /**
     * 解密密文。
     *
     * @param cipherBase64 入参：{@link #encrypt(String)} 产出的密文
     * @return 出参：明文
     */
    public static String decrypt(String cipherBase64) {
        try {
            byte[] packed = Base64.getDecoder().decode(cipherBase64);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, requireKey(),
                    new GCMParameterSpec(TAG_BITS, packed, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(packed, IV_LENGTH, packed.length - IV_LENGTH);
            return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("密钥解密失败", e);
        }
    }

    private static SecretKeySpec requireKey() {
        SecretKeySpec k = key;
        if (k == null) {
            throw new IllegalStateException("CryptoUtil 未初始化：请先在组合根调用 init 注入 AES 密钥");
        }
        return k;
    }

}
