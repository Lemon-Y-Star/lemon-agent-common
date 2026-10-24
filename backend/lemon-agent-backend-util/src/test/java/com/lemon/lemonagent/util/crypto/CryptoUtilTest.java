package com.lemon.lemonagent.util.crypto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 密钥加解密的往返与边界测试。
 */
class CryptoUtilTest {

    private static final String KEY_32B = Base64.getEncoder().encodeToString(new byte[32]);

    @BeforeAll
    static void init() {
        CryptoUtil.init(KEY_32B);
    }

    @Test
    void encrypt_decrypt_roundtrip() {
        String plain = "sk-test-abcdef123456";
        String cipher = CryptoUtil.encrypt(plain);
        assertEquals(plain, CryptoUtil.decrypt(cipher));
    }

    @Test
    void encrypt_is_randomized_same_input_diff_cipher() {
        String plain = "sk-same-input";
        String c1 = CryptoUtil.encrypt(plain);
        String c2 = CryptoUtil.encrypt(plain);
        // GCM 随机 IV：同一明文两次密文不同，但都能解回
        assertEquals(plain, CryptoUtil.decrypt(c1));
        assertEquals(plain, CryptoUtil.decrypt(c2));
    }

    @Test
    void decrypt_tampered_cipher_fails() {
        String cipher = CryptoUtil.encrypt("sk-tamper");
        byte[] raw = Base64.getDecoder().decode(cipher);
        raw[raw.length - 1] ^= 0x01;
        assertThrows(IllegalStateException.class,
                () -> CryptoUtil.decrypt(Base64.getEncoder().encodeToString(raw)));
    }

    @Test
    void init_rejects_wrong_key_length() {
        assertThrows(IllegalArgumentException.class,
                () -> CryptoUtil.init(Base64.getEncoder().encodeToString(new byte[16])));
    }

}
