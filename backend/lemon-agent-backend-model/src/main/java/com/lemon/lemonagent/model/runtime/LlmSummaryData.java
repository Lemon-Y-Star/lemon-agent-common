package com.lemon.lemonagent.model.runtime;

import lombok.Data;

/** LLM 观测时间窗汇总。 */
@Data
public class LlmSummaryData {

    /** 调用次数（运行数） */
    private long calls;

    /** 失败数（FAILED / TIMEOUT） */
    private long failed;

    /** 输入 tokens 合计 */
    private long inputTokens;

    /** 输出 tokens 合计 */
    private long outputTokens;

    /** 平均端到端时长毫秒 */
    private long avgLatencyMs;

}
