package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.model.runtime.PlanStepData;

import java.util.List;

/**
 * 计划快照的编解码（v3.1 plan-execute）：落库列 {@code agent_run.plan_json}。
 *
 * <p>形如 {@code [{"index":0,"title":"...","detail":"...","status":"DONE","output":"...","inputTokens":0,"outputTokens":0}]}。
 * 存的是"带进度的计划"，因此每步完成后覆盖写一次即可同时完成进度与产出的持久化——
 * 审批挂起、进程重启后都能从库里恢复，执行器不持有计划内存态。</p>
 */
final class PlanCodec {

    private static final tools.jackson.databind.ObjectMapper MAPPER =
            new tools.jackson.databind.ObjectMapper();

    private PlanCodec() {
    }

    /**
     * 解码：JSON → 步骤列表。
     *
     * @param json 入参：库里的 plan_json（可空）
     * @return 出参：步骤列表；null/空/脏数据返回 null（调用方据此认为「还没有计划」）
     */
    static List<PlanStepData> decode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            PlanStepData[] steps = MAPPER.readValue(json, PlanStepData[].class);
            return steps.length == 0 ? null : List.of(steps);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 编码：步骤列表 → JSON。
     *
     * @param steps 入参：步骤列表
     * @return 出参：落库字符串
     */
    static String encode(List<PlanStepData> steps) {
        try {
            return MAPPER.writeValueAsString(steps);
        } catch (Exception e) {
            throw new IllegalArgumentException("计划快照序列化失败", e);
        }
    }

}
