package com.lemon.lemonagent.model.runtime;

/**
 * 运行六态（契约 §4.4 / BUILD-SPEC §4.1）。
 *
 * <p>迁移规则（非法迁移必须拒绝）：RUNNING → COMPLETED/FAILED/CANCELLED；
 * BLOCKED → RUNNING（审批通过继续）/ FAILED（审批拒绝走 TOOL_REJECTED 归因）/ CANCELLED；
 * 终态（COMPLETED/FAILED/CANCELLED/TIMEOUT）不再迁移。v2 增量 1 无工具审批，
 * BLOCKED 与 TIMEOUT 预留给 v2.1。</p>
 */
public enum RunStatus {

    RUNNING,
    BLOCKED,
    COMPLETED,
    FAILED,
    CANCELLED,
    TIMEOUT;

    /**
     * 是否终态（终态事件必是最后一个 seq，之后不再追加）。
     *
     * @return 出参：true 表示终态
     */
    public boolean terminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == TIMEOUT;
    }

    /**
     * 是否可取消（仅 RUNNING / BLOCKED 可取消，终态 → 409 ILLEGAL_STATE）。
     *
     * @return 出参：true 表示可取消
     */
    public boolean cancellable() {
        return this == RUNNING || this == BLOCKED;
    }

    /**
     * 是否属于"会话互斥"的活跃态（同会话同时只允许一个活跃 run）。
     *
     * @return 出参：true 表示活跃
     */
    public boolean active() {
        return this == RUNNING || this == BLOCKED;
    }

}
