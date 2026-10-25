-- v3.2.0 执行图（PRD FR-7.1 最小落地）
-- agent_graph：每个 Agent 的图定义，按 version 递增（保存即新版本，旧版本保留可追溯）
-- agent_run 增两列：graph_json（提交时的图快照，改图不影响在跑的运行）、graph_state_json（节点进度与产出）

CREATE TABLE agent_graph (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    agent_id   BIGINT      NOT NULL COMMENT '所属 Agent',
    version    INT         NOT NULL DEFAULT 1 COMMENT '图版本（保存 +1）',
    graph_json TEXT        NOT NULL COMMENT '图定义 {nodes:[], edges:[]}',
    status     TINYINT     NOT NULL DEFAULT 1 COMMENT '1=当前生效版本 / 0=历史版本',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_version (agent_id, version),
    KEY idx_agent_status (agent_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Agent 执行图（版本化）';

ALTER TABLE agent_run
    ADD COLUMN graph_json TEXT NULL COMMENT '执行图快照（custom 策略）' AFTER plan_step,
    ADD COLUMN graph_state_json TEXT NULL COMMENT '执行图进度 {done:[], outputs:{}, current}' AFTER graph_json;
