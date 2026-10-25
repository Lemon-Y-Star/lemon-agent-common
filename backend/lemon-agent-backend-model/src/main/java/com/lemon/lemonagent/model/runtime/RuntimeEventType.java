package com.lemon.lemonagent.model.runtime;

/**
 * SSE 事件类型（契约 §5.2 的 13 种；本增量实现纯对话子集，其余随工具/执行图增量启用）。
 */
public enum RuntimeEventType {

    /** 运行开始：{runId, sessionId, traceId, strategy, ts} */
    RUN_STARTED("run_started"),

    /** 模型增量输出：{delta, index, stepIndex}（攒批后 delta 变长） */
    TOKEN("token"),

    /** 决定调用工具：{callId, toolName, args, riskLevel, stepIndex, ts} */
    TOOL_CALL("tool_call"),

    /** 计划已生成（plan 策略）：{steps:[{index,title}], total, ts} */
    PLAN_CREATED("plan_created"),

    /** 执行图开始（custom 策略）：{nodes:[{id,type,title}], total, ts} */
    GRAPH_STARTED("graph_started"),

    /** 执行图完成：{completed:[nodeId], total, ts} */
    GRAPH_COMPLETED("graph_completed"),

    /** 计划步骤开始：{stepIndex, title, total, ts}（执行图复用，额外带 nodeId/nodeType） */
    STEP_STARTED("step_started"),

    /** 计划步骤完成：{stepIndex, title, outputText, usage:{inputTokens,outputTokens}, ts} */
    STEP_COMPLETED("step_completed"),

    /** 工具返回：{callId, ok, durationMs, resultBrief, resultChars, ts} */
    TOOL_RESULT("tool_result"),

    /** 高风险工具/审批节点需要审批：{approvalId, kind, toolName?, args?, riskLevel, expiresAt, ts} */
    APPROVAL_REQUIRED("approval_required"),

    /** 审批有结果：{approvalId, decision, operator, ts} */
    APPROVAL_RESOLVED("approval_resolved"),

    /** 正常结束：{runId, outputText, usage:{inputTokens, outputTokens}, ts}——outputText 是权威正文 */
    RUN_COMPLETED("run_completed"),

    /** 失败：{runId, errorCode, errorMsg, ts}——errorMsg 是用户能看懂的一句话 */
    RUN_FAILED("run_failed"),

    /** 取消：{runId, cancelledBy, ts} */
    RUN_CANCELLED("run_cancelled");

    private final String wire;

    RuntimeEventType(String wire) {
        this.wire = wire;
    }

    /**
     * SSE event: 帧上使用的类型名。
     *
     * @return 出参：线格式名称（小写蛇形）
     */
    public String wireName() {
        return wire;
    }

}
