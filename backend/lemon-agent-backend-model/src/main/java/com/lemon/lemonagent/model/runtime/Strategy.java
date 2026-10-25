package com.lemon.lemonagent.model.runtime;

import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;

/**
 * 执行策略（契约 §4.5）。
 *
 * <p>已实现：{@code auto}（单 Agent 自主迭代）、{@code plan}（plan-execute：先出计划再逐步执行，v3.1）、
 * {@code custom}（执行图：按 Agent 的图定义做就绪队列调度，可挂载工作流，v3.2.0 / v3.7）、
 * {@code fixed}（直接问答：不装配工具、单轮生成，v3.7 补齐 PRD 三模式）。
 * {@code flow} 仍返回 UNSUPPORTED_STRATEGY——原型的固定三段流水线语义已被 {@code plan}（模型自主出计划）
 * 与 {@code custom}（用户自编排）覆盖，等有「模板库」需求再降级实现。</p>
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
     * @return 出参：auto / plan / custom（执行图）/ fixed（直接问答）支持
     */
    public boolean supported() {
        return this == AUTO || this == PLAN || this == CUSTOM || this == FIXED;
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
                    throw new BusinessException(ResultCodes.BAD_REQUEST, "策略 " + s.wire + " 尚未实现，当前支持 auto / plan / custom / fixed");
                }
                return s;
            }
        }
        throw new BusinessException(ResultCodes.BAD_REQUEST, "未知策略：" + value);
    }

}
