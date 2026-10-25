-- v3.8 收尾三件套：产物与反馈 / API 分发 / LLM 观测
-- run_feedback：运行级反馈（点赞点踩 + 评论），一人一运行一条（upsert）
-- api_key：API 分发密钥（只存 SHA-256 哈希，明文只在创建响应里出现一次）
-- agent_run 补模型来源两列：LLM 观测按模型聚合用

CREATE TABLE run_feedback (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    run_id     BIGINT       NOT NULL COMMENT '运行 ID',
    user_id    BIGINT       NOT NULL COMMENT '反馈人',
    rating     VARCHAR(8)   NOT NULL COMMENT 'UP / DOWN',
    comment    VARCHAR(1000) NULL    COMMENT '文字反馈',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_run_user (run_id, user_id),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '运行反馈（产物与反馈）';

CREATE TABLE api_key (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id      BIGINT       NOT NULL COMMENT '归属用户',
    name         VARCHAR(128) NOT NULL COMMENT '密钥名称',
    key_prefix   VARCHAR(16)  NOT NULL COMMENT '明文前缀（sk- + 前 8 位），列表展示用',
    key_hash     CHAR(64)     NOT NULL COMMENT 'SHA-256 hex（整串密钥）',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 / 0=已吊销',
    last_used_at TIMESTAMP(6) NULL COMMENT '最近一次调用时间',
    created_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_hash (key_hash),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'API 分发密钥';

ALTER TABLE agent_run
    ADD COLUMN model_name   VARCHAR(128) NULL COMMENT '实际使用的模型名（执行器解析连接后回填）',
    ADD COLUMN model_conn_id BIGINT      NULL COMMENT '模型连接 ID';
