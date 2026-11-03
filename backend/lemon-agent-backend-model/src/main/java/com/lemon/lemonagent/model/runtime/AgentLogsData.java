package com.lemon.lemonagent.model.runtime;

import java.util.List;

/** Agent 运营日志（v4.5.2，Dify「日志」页同款）：按会话聚合该 Agent 的对话。 */
public class AgentLogsData {

    /** 会话维度日志行。 */
    private List<SessionLog> sessions;

    public List<SessionLog> getSessions() {
        return sessions;
    }

    public void setSessions(List<SessionLog> sessions) {
        this.sessions = sessions;
    }

    /** 会话日志行。 */
    public static class SessionLog {

        /** 会话 ID */
        private Long sessionId;

        /** 会话标题 */
        private String title;

        /** 运行次数（≈消息数） */
        private int runCount;

        /** 成功次数 */
        private int okCount;

        /** 首次运行时间 */
        private String firstAt;

        /** 最近运行时间 */
        private String lastAt;

        /** token 合计 */
        private long totalTokens;

        public Long getSessionId() {
            return sessionId;
        }

        public void setSessionId(Long sessionId) {
            this.sessionId = sessionId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public int getRunCount() {
            return runCount;
        }

        public void setRunCount(int runCount) {
            this.runCount = runCount;
        }

        public int getOkCount() {
            return okCount;
        }

        public void setOkCount(int okCount) {
            this.okCount = okCount;
        }

        public String getFirstAt() {
            return firstAt;
        }

        public void setFirstAt(String firstAt) {
            this.firstAt = firstAt;
        }

        public String getLastAt() {
            return lastAt;
        }

        public void setLastAt(String lastAt) {
            this.lastAt = lastAt;
        }

        public long getTotalTokens() {
            return totalTokens;
        }

        public void setTotalTokens(long totalTokens) {
            this.totalTokens = totalTokens;
        }
    }
}
