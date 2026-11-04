package com.lemon.lemonagent.model.modelconn;

import lombok.Data;

/**
 * 多模型对比单项结果（v4.8，每个被选连接一条）。
 *
 * <p>单个连接失败不影响其他连接——失败时 {@code error} 带原因，其余字段尽量填。</p>
 */
@Data
public class ModelCompareItemData {

    /** 连接 ID */
    private Long connectionId;

    /** 连接名称 */
    private String name;

    /** 本次对比使用的模型名（modelsJson 第一个） */
    private String model;

    /** 回答正文（已剥 think 块与围栏；失败时为 null） */
    private String content;

    /** 端到端耗时 ms（从发起到完成/失败） */
    private Long latencyMs;

    /** 输入 tokens（渠道未上报时为 0） */
    private long inputTokens;

    /** 输出 tokens（渠道未上报时为 0） */
    private long outputTokens;

    /** 失败原因（成功时为 null） */
    private String error;

}
