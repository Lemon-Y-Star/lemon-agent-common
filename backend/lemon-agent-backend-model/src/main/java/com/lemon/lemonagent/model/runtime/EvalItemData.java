package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.List;

/** 评测用例项的对外数据（输出/状态实时取自 agent_run，不重复存）。 */
@Data
public class EvalItemData {

    /** 用例序号（0 起） */
    private int seq;

    /** 用例输入 */
    private String input;

    /** 对应的运行 ID */
    private Long runId;

    /** 运行状态（RUNNING / BLOCKED / COMPLETED / FAILED / CANCELLED） */
    private String status;

    /** 运行正文（COMPLETED 后为权威输出） */
    private String output;

    /** 被条件路由跳过的节点 ID（图运行才有） */
    private List<String> skippedNodes;

    /** 输入 tokens */
    private long inputTokens;

    /** 输出 tokens */
    private long outputTokens;

}
