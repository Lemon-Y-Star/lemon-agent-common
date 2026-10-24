package com.lemon.lemonagent.util.crypto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 新密码策略测试——覆盖 PRD FR-2.4 强调的两个边界：
 * ① 字符数达标但 UTF-8 字节数超限（25 个汉字 = 75 字节）必须拦下；
 * ② 长度不足 10 位必须拦下。
 */
class PasswordPolicyTest {

    @Test
    void ok_at_10_chars_ascii() {
        assertNull(PasswordPolicy.validateNew("lemon12345"));
    }

    @Test
    void ok_at_72_bytes_boundary() {
        // 18 个汉字 = 54 字节 + 18 个 ASCII = 72 字节，恰好达上限
        assertNull(PasswordPolicy.validateNew("一".repeat(18) + "a".repeat(18)));
    }

    @Test
    void reject_below_10_chars() {
        assertEquals("新密码至少 10 位，且 UTF-8 长度不超过 72 字节。",
                PasswordPolicy.validateNew("lemon123"));
    }

    @Test
    void reject_null_and_empty() {
        assertEquals("新密码至少 10 位，且 UTF-8 长度不超过 72 字节。",
                PasswordPolicy.validateNew(null));
        assertEquals("新密码至少 10 位，且 UTF-8 长度不超过 72 字节。",
                PasswordPolicy.validateNew(""));
    }

    @Test
    void reject_25_chinese_chars_75_bytes() {
        // 字符数 25 ≥ 10 达标，但 UTF-8 字节数 75 > 72 超限——最容易实现错的边界
        assertEquals("新密码至少 10 位，且 UTF-8 长度不超过 72 字节。",
                PasswordPolicy.validateNew("一".repeat(25)));
    }

    @Test
    void reject_over_72_bytes_ascii() {
        assertEquals("新密码至少 10 位，且 UTF-8 长度不超过 72 字节。",
                PasswordPolicy.validateNew("a".repeat(73)));
    }

}
