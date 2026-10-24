-- 对话运行时（v2）：会话 / 运行 / 事件 / 消息（BUILD-SPEC §3.1 五张表）
-- demo_ticket 为工具审批演示表（本增量建表，工具链路 v2.1 接入）

CREATE TABLE agent_session (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '会话 ID',
    user_id        BIGINT       NOT NULL COMMENT '归属用户',
    title          VARCHAR(128) NOT NULL COMMENT '标题（首条消息前 20 字）',
    last_active_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '最近活跃（发消息/建 run 刷新）',
    created_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_session_user (user_id, last_active_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '对话会话';

CREATE TABLE agent_run (
    id                    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '运行 ID',
    session_id            BIGINT       NOT NULL COMMENT '所属会话',
    user_id               BIGINT       NOT NULL COMMENT '归属用户（越权过滤主键）',
    client_request_id     VARCHAR(64)  NOT NULL COMMENT '前端幂等键 UUID',
    status                VARCHAR(16)  NOT NULL COMMENT '六态：RUNNING/BLOCKED/COMPLETED/FAILED/CANCELLED/TIMEOUT',
    strategy              VARCHAR(16)  NOT NULL DEFAULT 'auto' COMMENT 'auto/fixed/flow/custom',
    trace_id              CHAR(32)     NOT NULL COMMENT '32 位 hex 全链路 ID',
    last_seq              BIGINT       NOT NULL DEFAULT 0 COMMENT '事件序号唯一分配源（run 内连续递增）',
    output_text           MEDIUMTEXT   NULL COMMENT '最终正文（run_completed 权威值，token 攒批的兜底）',
    error_code            VARCHAR(32)  NULL COMMENT '终态失败归因（CANCELLED/MODEL_ERROR/...）',
    error_msg             VARCHAR(512) NULL COMMENT '用户能看懂的一句话错误',
    pending_approval_json JSON         NULL COMMENT 'BLOCKED 态的审批快照（v2.1 工具审批用）',
    graph_json            JSON         NULL COMMENT '本次执行图快照（custom 策略；auto 留空）',
    input_tokens          INT          NULL COMMENT '模型输入 tokens',
    output_tokens         INT          NULL COMMENT '模型输出 tokens',
    started_at            TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ended_at              TIMESTAMP(6) NULL,
    created_at            TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at            TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_run_client_req (user_id, client_request_id),
    KEY idx_run_session (session_id),
    KEY idx_run_user (user_id, status),
    KEY idx_run_trace (trace_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '一次执行（状态机宿主）';

CREATE TABLE agent_run_event (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自增（非 seq）',
    run_id     BIGINT      NOT NULL COMMENT '所属运行',
    seq        BIGINT      NOT NULL COMMENT 'run 内序号，从 1 连续递增',
    event_type VARCHAR(32) NOT NULL COMMENT 'run_started/token/run_completed/...',
    payload    JSON        NOT NULL COMMENT '事件负载（契约 §5.2 各 type 的 schema）',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_run_seq (run_id, seq)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'append-only 事件流（先落库再推送）';

CREATE TABLE agent_message (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '消息 ID',
    session_id BIGINT      NOT NULL COMMENT '所属会话',
    user_id    BIGINT      NOT NULL COMMENT '归属用户',
    role       VARCHAR(16) NOT NULL COMMENT 'USER / ASSISTANT',
    content    MEDIUMTEXT  NOT NULL COMMENT '消息正文',
    run_id     BIGINT      NULL COMMENT 'ASSISTANT 消息关联的运行',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_message_session (session_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '对话消息（给人看，与事件流解耦）';

CREATE TABLE demo_ticket (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '工单 ID',
    user_id    BIGINT       NOT NULL COMMENT '创建者',
    run_id     BIGINT       NOT NULL COMMENT '来源运行（审批通过后写入）',
    title      VARCHAR(200) NOT NULL COMMENT '工单标题',
    priority   VARCHAR(8)   NOT NULL DEFAULT 'P2' COMMENT 'P0/P1/P2',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_ticket_run (run_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '演示工单（审批可见效果的落点，v2.1 工具链路使用）';
