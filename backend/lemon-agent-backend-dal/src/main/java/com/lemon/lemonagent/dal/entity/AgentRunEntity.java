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

    /** 挂载的 Agent（跟随当前已启用版本） */
    private Long agentId;

    /** 归属用户 */
    private Long userId;

    /** 幂等键（前端 UUID） */
    private String clientRequestId;

    /** 六态（RunStatus.name()） */
    private String status;

    /** 策略 */
    private String strategy;

    /** 深度思考开关：1=开启（执行器追加 CoT 指令，正文含 <think> 标签） */
    private Integer thinking;

    /** 原始输入（重试 / 溯源用） */
    private String input;

    /** 32 位 hex 全链路 ID */
    private String traceId;

    /** 实际使用的模型名（执行器解析连接后回填；LLM 观测聚合用） */
    private String modelName;

    /** 模型连接 ID */
    private Long modelConnId;

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

    /** 计划快照 JSON（plan 策略；步骤数组，含 title/detail/status/output） */
    private String planJson;

    /** 当前计划步骤下标（0 起；等于步骤数表示计划已跑完） */
    private Integer planStep;

    /** 执行图快照 JSON（custom 策略；提交时冻结，改图不影响在跑的运行） */
    private String graphJson;

    /** 执行图进度 JSON（done / outputs / current） */
    private String graphStateJson;

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
