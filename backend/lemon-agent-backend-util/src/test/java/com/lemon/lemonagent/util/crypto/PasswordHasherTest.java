package com.lemon.lemonagent.util.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 口令哈希的往返测试。
 */
class PasswordHasherTest {

    @Test
    void hash_verify_roundtrip() {
        String salt = PasswordHasher.newSalt();
        String stored = PasswordHasher.hash("lemon123456", salt);
        assertTrue(stored.startsWith("pbkdf2$210000$"));
        assertTrue(PasswordHasher.verify("lemon123456", stored));
    }

    @Test
    void verify_wrong_password_fails() {
        String stored = PasswordHasher.hash("lemon123456", PasswordHasher.newSalt());
        assertFalse(PasswordHasher.verify("lemon1234567", stored));
    }

    @Test
    void verify_malformed_stored_fails_silently() {
        // 存储串损坏一律返回不匹配（登录失败统一口径），不抛异常
        assertFalse(PasswordHasher.verify("anything", "garbage"));
        assertFalse(PasswordHasher.verify("anything", "pbkdf2$abc$x$y"));
    }

    @Test
    void same_password_different_salt_different_hash() {
        String s1 = PasswordHasher.hash("lemon123456", PasswordHasher.newSalt());
        String s2 = PasswordHasher.hash("lemon123456", PasswordHasher.newSalt());
        assertFalse(s1.equals(s2));
        assertTrue(PasswordHasher.verify("lemon123456", s1));
        assertTrue(PasswordHasher.verify("lemon123456", s2));
    }

}
