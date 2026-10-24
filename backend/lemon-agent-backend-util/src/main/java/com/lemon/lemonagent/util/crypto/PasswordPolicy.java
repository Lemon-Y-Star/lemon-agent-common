package com.lemon.lemonagent.util.crypto;

import java.nio.charset.StandardCharsets;

/**
 * 新密码策略校验（PRD FR-2.4 校验链的服务端同款实现）。
 *
 * <p>规则与判定顺序：① 至少 10 个字符；② UTF-8 字节数不超过 72（与前端
 * {@code TextEncoder().encode(...).length} 同口径——25 个汉字字符数达标但 75 字节超限，
 * 必须拦下）。客户端已按 FR-2.4 的顺序做本地拦截，服务端是最后一道防线。</p>
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    /**
     * 校验新密码是否合规。
     *
     * @param newPassword 入参：新密码明文
     * @return 出参：null 表示通过，否则为不通过的原因文案
     */
    public static String validateNew(String newPassword) {
        if (newPassword == null || newPassword.length() < 10) {
            return "新密码至少 10 位，且 UTF-8 长度不超过 72 字节。";
        }
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            return "新密码至少 10 位，且 UTF-8 长度不超过 72 字节。";
        }
        return null;
    }

}
