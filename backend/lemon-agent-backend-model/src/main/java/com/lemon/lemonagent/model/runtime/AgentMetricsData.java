package com.lemon.lemonagent.model.runtime;

import java.util.List;

/** Agent 运营监控（v4.5.2，Dify「监控」页同款）：按天聚合指标 + 汇总。 */
public class AgentMetricsData {

    /** 按天指标（升序）。 */
    private List<DayMetric> days;

    /** 运行总数（≈消息数） */
    private long totalRuns;

    /** 会话总数 */
    private long totalSessions;

    /** token 总量 */
    private long totalTokens;

    /** 成功率（百分比 0-100） */
    private double successRate;

    /** 好评率（有评分中的 UP 占比，百分比；无评分 = 0） */
    private double likeRate;

    public List<DayMetric> getDays() {
        return days;
    }

    public void setDays(List<DayMetric> days) {
        this.days = days;
    }

    public long getTotalRuns() {
        return totalRuns;
    }

    public void setTotalRuns(long totalRuns) {
        this.totalRuns = totalRuns;
    }

    public long getTotalSessions() {
        return totalSessions;
    }

    public void setTotalSessions(long totalSessions) {
        this.totalSessions = totalSessions;
    }

    public long getTotalTokens() {
        return totalTokens;
    }

    public void setTotalTokens(long totalTokens) {
        this.totalTokens = totalTokens;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }

    public double getLikeRate() {
        return likeRate;
    }

    public void setLikeRate(double likeRate) {
        this.likeRate = likeRate;
    }

    /** 单日指标。 */
    public static class DayMetric {

        /** 日期（yyyy-MM-dd） */
        private String date;

        /** 运行数 */
        private long runs;

        /** 会话数 */
        private long sessions;

        /** token 用量 */
        private long tokens;

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public long getRuns() {
            return runs;
        }

        public void setRuns(long runs) {
            this.runs = runs;
        }

        public long getSessions() {
            return sessions;
        }

        public void setSessions(long sessions) {
            this.sessions = sessions;
        }

        public long getTokens() {
            return tokens;
        }

        public void setTokens(long tokens) {
            this.tokens = tokens;
        }
    }
}
