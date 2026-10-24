package com.lemon.lemonagent.model.runtime;

/**
 * SSE 事件类型（契约 §5.2 的 13 种；本增量实现纯对话子集，其余随工具/执行图增量启用）。
 */
public enum RuntimeEventType {

    /** 运行开始：{runId, sessionId, traceId, strategy, ts} */
    RUN_STARTED("run_started"),

    /** 模型增量输出：{delta, index, stepIndex}（攒批后 delta 变长） */
    TOKEN("token"),

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
