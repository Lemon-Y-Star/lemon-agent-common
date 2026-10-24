package com.lemon.lemonagent.model.runtime;

import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;

/**
 * 执行策略（契约 §4.5）。
 *
 * <p>已实现：{@code auto}（单 Agent 自主迭代）、{@code plan}（plan-execute：先出计划再逐步执行，
 * v3.1）。{@code fixed} / {@code flow} 按契约返回 UNSUPPORTED_STRATEGY（执行图落地后才允许它们进入）。</p>
 */
public enum Strategy {

    AUTO("auto"),
    PLAN("plan"),
    FIXED("fixed"),
    FLOW("flow"),
    CUSTOM("custom");

    private final String wire;

    Strategy(String wire) {
        this.wire = wire;
    }

    /**
     * 线格式名称。
     *
     * @return 出参：小写名称
     */
    public String wireName() {
        return wire;
    }

    /**
     * 本增量是否支持该策略。
     *
     * @return 出参：auto 与 plan 支持
     */
    public boolean supported() {
        return this == AUTO || this == PLAN;
    }

    /**
     * 从请求字符串解析并校验支持性。
     *
     * @param raw 入参：请求里的 strategy（可空 = 默认 auto）
     * @return 出参：Strategy
     * @throws BusinessException 未知取值 → INVALID_STRATEGY；已知但未实现 → UNSUPPORTED_STRATEGY
     */
    public static Strategy parse(String raw) {
        String value = raw == null || raw.isBlank() ? "auto" : raw.trim().toLowerCase();
        for (Strategy s : values()) {
            if (s.wire.equals(value)) {
                if (!s.supported()) {
                    throw new BusinessException(ResultCodes.BAD_REQUEST, "策略 " + s.wire + " 尚未实现，当前支持 auto / plan");
                }
                return s;
            }
        }
        throw new BusinessException(ResultCodes.BAD_REQUEST, "未知策略：" + value);
    }

}
