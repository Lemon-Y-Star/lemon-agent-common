package com.lemon.lemonagent.model.runtime;

import lombok.Data;

/** 运行列表条目（契约 4.10，必须 user_id 过滤）。 */
@Data
public class RunListItem {

    /** 运行 ID */
    private Long runId;

    /** 挂载的 Agent ID（null = 未选择，运行时取用户默认） */
    private Long agentId;

    /** Agent 名称（列表标注用；null = 平台默认） */
    private String agentName;

    /** 状态 */
    private String status;

    /** 策略 */
    private String strategy;

    /** 失败归因 */
    private String errorCode;

    /** 全链路 ID */
    private String traceId;

    /** 实际使用的模型名（LLM 观测；可能为 null = 旧数据/未执行到模型） */
    private String modelName;

    /** 输入 tokens */
    private Integer inputTokens;

    /** 输出 tokens */
    private Integer outputTokens;

    /** 端到端时长毫秒（ended - started；未结束为 null） */
    private Long latencyMs;

    /** 事件序号游标 */
    private long lastSeq;

    /** 开始时间 */
    private String startedAt;

    /** 结束时间 */
    private String endedAt;

}
