package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.Map;

/** 管理端统计（PRD FR-22.1 简化版）。 */
@Data
public class AdminStatsData {

    /** 用户数 */
    private long users;

    /** 会话数 */
    private long sessions;

    /** 运行数 */
    private long runs;

    /** 消息数 */
    private long messages;

    /** 工单数 */
    private long tickets;

    /** 运行状态分布 */
    private Map<String, Long> runsByStatus;

    /** 总 token 用量 */
    private long totalTokens;

}
