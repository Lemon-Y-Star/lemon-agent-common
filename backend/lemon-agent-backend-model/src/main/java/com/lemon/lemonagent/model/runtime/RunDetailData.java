package com.lemon.lemonagent.model.runtime;

import lombok.Data;

/**
 * 运行快照（契约 4.6）。v2 增量 1 无工具审批：pendingApproval / graph 恒为 null（结构预留）。
 */
@Data
public class RunDetailData {

    /** 运行 ID */
    private Long runId;

    /** 会话 ID */
    private Long sessionId;

    /** 挂载的 Agent ID（null = 未选择，运行时取用户默认） */
    private Long agentId;

    /** Agent 名称（null = 平台默认） */
    private String agentName;

    /** 六态之一 */
    private String status;

    /** 策略 */
    private String strategy;

    /** 全链路 ID */
    private String traceId;

    /** 原始输入（v3.9.1 起落库；旧运行为 null） */
    private String input;

    /** 最终产出（run_completed 权威正文，含 <think> 原文；未完成为 null） */
    private String outputText;

    /** 事件序号游标（前端轮询比较用） */
    private long lastSeq;

    /** 终态失败归因 */
    private String errorCode;

    /** 用户可读错误 */
    private String errorMsg;

    /** 待审批快照（v2.1 工具审批启用；本增量恒 null） */
    private Object pendingApproval;

    /** 执行图快照（custom 策略；本增量恒 null） */
    private Object graph;

    /** 计划快照（plan 策略；auto 策略恒 null） */
    private java.util.List<PlanStepData> plan;

    /** 当前计划步骤下标（0 起；等于步骤数表示已完成） */
    private Integer planStep;

    /** 用量（run_completed 后有值） */
    private Usage usage;

    /** 开始时间 */
    private String startedAt;

    /** 结束时间 */
    private String endedAt;

    /** token 用量。 */
    @Data
    public static class Usage {

        /** 输入 tokens */
        private long inputTokens;

        /** 输出 tokens */
        private long outputTokens;

    }

}
