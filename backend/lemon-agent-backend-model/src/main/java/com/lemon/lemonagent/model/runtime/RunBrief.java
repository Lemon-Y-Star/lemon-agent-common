package com.lemon.lemonagent.model.runtime;

import lombok.Data;

/** 会话详情里的运行摘要（契约 4.4：status + lastSeq 是刷新恢复的两个关键）。 */
@Data
public class RunBrief {

    /** 运行 ID */
    private Long runId;

    /** 六态之一 */
    private String status;

    /** 续流起点 */
    private long lastSeq;

    /** 全链路 ID */
    private String traceId;

    /** 策略 */
    private String strategy;

    /** 运行耗时（毫秒，startedAt → endedAt）；未结束为 null。对话页回答尾注展示用 */
    private Long durationMs;

}
