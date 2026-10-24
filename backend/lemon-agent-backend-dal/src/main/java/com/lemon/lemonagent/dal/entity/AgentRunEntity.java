package com.lemon.lemonagent.dal.entity;

import lombok.Data;

import java.time.Instant;

/** 一次运行（表 agent_run）：状态机宿主，last_seq 是事件序号唯一分配源。 */
@Data
public class AgentRunEntity {

    /** 运行 ID */
    private Long id;

    /** 所属会话 */
    private Long sessionId;

    /** 归属用户 */
    private Long userId;

    /** 幂等键（前端 UUID） */
    private String clientRequestId;

    /** 六态（RunStatus.name()） */
    private String status;

    /** 策略 */
    private String strategy;

    /** 32 位 hex 全链路 ID */
    private String traceId;

    /** 事件序号游标 */
    private long lastSeq;

    /** 最终正文（run_completed 权威值） */
    private String outputText;

    /** 终态失败归因 */
    private String errorCode;

    /** 用户可读错误 */
    private String errorMsg;

    /** BLOCKED 态审批快照 JSON（kind/approvalId/toolName/args/riskLevel/expiresAt） */
    private String pendingApprovalJson;

    /** 输入 tokens */
    private Integer inputTokens;

    /** 输出 tokens */
    private Integer outputTokens;

    /** 开始时间 */
    private Instant startedAt;

    /** 结束时间 */
    private Instant endedAt;

    /** 创建时间 */
    private Instant createdAt;

    /** 更新时间 */
    private Instant updatedAt;

}
