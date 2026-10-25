package com.lemon.lemonagent.model.runtime;

import lombok.Data;

/** 单个模型的用量聚合。 */
@Data
public class ModelUsageData {

    /** 模型名 */
    private String modelName;

    /** 调用次数 */
    private long calls;

    /** 失败数 */
    private long failed;

    /** 输入 tokens 合计 */
    private long inputTokens;

    /** 输出 tokens 合计 */
    private long outputTokens;

    /** 平均时长毫秒 */
    private long avgLatencyMs;

}
