package com.lemon.lemonagent.model.runtime;

import lombok.Data;

/**
 * 计划步骤（plan 策略，v3.1）。
 *
 * <p>既用于 SSE 事件（plan_created / step_started / step_completed），也用于运行快照回放，
 * 因此字段是"超集"：列表页只需要 title，详情页会带 status 与已产出的 output。</p>
 */
@Data
public class PlanStepData {

    /** 步骤下标（0 起） */
    private int index;

    /** 步骤标题（一句话，动词开头） */
    private String title;

    /** 步骤说明（可选，规划时给出的执行要点） */
    private String detail;

    /** 状态：PENDING / RUNNING / DONE / FAILED */
    private String status;

    /** 该步骤产出的文本（未执行时为空） */
    private String output;

    /** 该步骤消耗的输入 tokens（汇总 run 用量用） */
    private long inputTokens;

    /** 该步骤消耗的输出 tokens */
    private long outputTokens;

}
