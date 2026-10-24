package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.List;

/** 用量总览（PRD FR-16 简化版：按天聚合 + 状态分布）。 */
@Data
public class UsageOverviewData {

    /** 统计窗口（天） */
    private int days;

    /** 总运行数（窗口内） */
    private long totalRuns;

    /** 总输入 tokens */
    private long totalInputTokens;

    /** 总输出 tokens */
    private long totalOutputTokens;

    /** 状态分布 */
    private List<StatusCount> statusCounts;

    /** 按天序列（缺失天补零，前端直接画柱状图） */
    private List<DailyUsage> daily;

    /** 状态分布条目。 */
    @Data
    public static class StatusCount {

        /** 状态 */
        private String status;

        /** 数量 */
        private long count;
    }

    /** 单日用量。 */
    @Data
    public static class DailyUsage {

        /** 日期（yyyy-MM-dd） */
        private String date;

        /** 运行数 */
        private long runs;

        /** 输入 tokens */
        private long inputTokens;

        /** 输出 tokens */
        private long outputTokens;
    }

}
