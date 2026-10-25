-- v3.5 图运行接评测回归：一批用例 = 一次按当前图版本发起的多次运行
-- 只存"用例 → 运行"的绑定关系；输出/状态从 agent_run 读，避免同一份事实两处存

CREATE TABLE eval_batch (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '批次 ID',
    user_id       BIGINT       NOT NULL COMMENT '归属用户',
    agent_id      BIGINT       NOT NULL COMMENT '被测 Agent',
    graph_version INT          NULL COMMENT '发起时的图版本（快照版本号）',
    note          VARCHAR(200) NOT NULL DEFAULT '' COMMENT '批次备注',
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_agent (agent_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '评测批次';

CREATE TABLE eval_item (
    id      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    batch_id BIGINT       NOT NULL COMMENT '批次 ID',
    seq     INT           NOT NULL COMMENT '用例序号（0 起）',
    run_id  BIGINT        NOT NULL COMMENT '对应的运行 ID',
    input   VARCHAR(1000) NOT NULL COMMENT '用例输入',
    PRIMARY KEY (id),
    KEY idx_batch (batch_id, seq)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '评测用例项';
