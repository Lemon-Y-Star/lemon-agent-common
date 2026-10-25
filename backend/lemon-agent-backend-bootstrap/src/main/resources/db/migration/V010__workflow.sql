-- v3.7 工作流（PRD FR-25 最小闭环）：独立实体 + 版本化 + Agent 挂载
-- workflow：工作流本体（用户级，可被多个 Agent 挂载，与「每 Agent 一张」的 agent_graph 区分）
-- workflow_version：版本化图快照（保存即新版本，旧版本保留可追溯）
-- agent_config 增两列：workflow_id / workflow_version（挂载；NULL = 用 Agent 自己的执行图）

CREATE TABLE workflow (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '归属用户',
    name        VARCHAR(128) NOT NULL COMMENT '工作流名称',
    description VARCHAR(512) NULL     COMMENT '描述',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 / 0=停用',
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '工作流（独立实体，可被多个 Agent 挂载）';

CREATE TABLE workflow_version (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    workflow_id BIGINT       NOT NULL COMMENT '所属工作流',
    version     INT          NOT NULL DEFAULT 1 COMMENT '版本号（保存 +1）',
    graph_json  TEXT         NOT NULL COMMENT '图定义 {nodes:[], edges:[]}',
    note        VARCHAR(512) NULL     COMMENT '版本说明（如来源 Agent）',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1=当前生效版本 / 0=历史版本',
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_wf_version (workflow_id, version),
    KEY idx_wf_status (workflow_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '工作流版本（版本化图快照）';

ALTER TABLE agent_config
    ADD COLUMN workflow_id      BIGINT NULL COMMENT '挂载的工作流；NULL=用 Agent 自己的执行图',
    ADD COLUMN workflow_version INT    NULL COMMENT '挂载的工作流版本；NULL=跟随工作流当前生效版本';
