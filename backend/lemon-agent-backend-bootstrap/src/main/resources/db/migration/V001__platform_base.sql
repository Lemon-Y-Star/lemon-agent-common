-- LemonAgent 平台基座（v1）：账号、模型连接、MCP 连接
-- 运行时相关表（agent_session / agent_run / ...）由后续运行时切片的迁移脚本新增

CREATE TABLE user_account (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(64)  NOT NULL COMMENT '用户名（唯一）',
    password_hash VARCHAR(256) NOT NULL COMMENT 'PBKDF2 存储串 pbkdf2$iters$salt$hash',
    role          VARCHAR(32)  NOT NULL DEFAULT 'admin' COMMENT '角色（v1 仅 admin）',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 禁用',
    created_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '平台用户';

CREATE TABLE model_connection (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id        BIGINT       NOT NULL COMMENT '归属用户',
    name           VARCHAR(128) NOT NULL COMMENT '连接名称',
    provider       VARCHAR(64)  NOT NULL COMMENT '提供方标识',
    base_url       VARCHAR(512) NOT NULL COMMENT 'OpenAI 兼容端点',
    api_key_cipher VARCHAR(512) NOT NULL COMMENT 'API 密钥密文（AES-256-GCM）',
    models_json    VARCHAR(1024) NOT NULL DEFAULT '[]' COMMENT '可用模型名 JSON 数组',
    allow_auxiliary TINYINT     NOT NULL DEFAULT 0 COMMENT '是否允许辅助功能使用：1/0',
    status         TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    created_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_model_conn_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '个人模型连接（BYOK）';

CREATE TABLE mcp_connection (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id            BIGINT       NULL COMMENT '归属用户；NULL=平台公共连接',
    name               VARCHAR(128) NOT NULL COMMENT '连接名称',
    transport_type     VARCHAR(16)  NOT NULL COMMENT 'SSE / STDIO / STREAMABLE',
    url                VARCHAR(512) NULL COMMENT 'HTTP 端点（SSE / STREAMABLE）',
    command            VARCHAR(512) NULL COMMENT '本地子进程命令（STDIO）',
    secret_cipher      VARCHAR(512) NULL COMMENT '密钥密文（AES-256-GCM）',
    status             TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    editable           TINYINT      NOT NULL DEFAULT 1 COMMENT '是否可编辑：平台公共恒 0',
    platform_public    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否平台公共',
    unavailable_reason VARCHAR(256) NULL COMMENT '不可用原因',
    version            INT          NOT NULL DEFAULT 1 COMMENT '版本（配置变更 +1）',
    created_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_mcp_conn_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'MCP 连接（平台公共 + 用户自建）';

-- 种子账号：admin / lemon123456（PBKDF2-SHA256，210000 轮；首次登录后请立即改密）
INSERT INTO user_account (username, password_hash, role, status) VALUES
('admin', 'pbkdf2$210000$bGVtb24tYWdlbnQtc2VlZDE=$jJrCseAJm+wLouOvR2rwpwMUqKFWoe5K87QcygGa7jM=', 'admin', 1);

-- 平台公共 MCP 连接（PRD FR-12 预置 11 个，不可编辑；stdio 命令为占位，按实际部署修改）
INSERT INTO mcp_connection (user_id, name, transport_type, url, command, status, editable, platform_public, version) VALUES
(NULL, '高德地图',                        'sse',       'https://mcp.amap.com/sse?key=your-amap-key', NULL, 1, 0, 1, 1),
(NULL, 'baidu-search',                    'sse',       'http://appbuilder.baidu.com/v2/ai_search/mcp/', NULL, 1, 0, 1, 1),
(NULL, 'weather-mcp',                     'stdio',     NULL, 'uvx weather-mcp', 1, 0, 1, 1),
(NULL, 'cookwith-recipe',                 'stdio',     NULL, 'uvx cookwith-recipe', 1, 0, 1, 1),
(NULL, 'nutrition-mcp',                   'stdio',     NULL, 'uvx nutrition-mcp', 1, 0, 1, 1),
(NULL, 'calculator-mcp',                  'stdio',     NULL, 'uvx calculator-mcp', 1, 0, 1, 1),
(NULL, 'arxiv-mcp',                       'stdio',     NULL, 'uvx arxiv-mcp', 1, 0, 1, 1),
(NULL, 'baidu-translate',                 'stdio',     NULL, 'uvx baidu-translate', 1, 0, 1, 1),
(NULL, 'languagetool',                    'stdio',     NULL, 'uvx languagetool', 1, 0, 1, 1),
(NULL, '12306-mcp',                       'stdio',     NULL, 'uvx 12306-mcp', 1, 0, 1, 1),
(NULL, '百度千帆实时联网搜索(web-search)', 'streamable', 'https://qianfan.baidubce.com/v2/mcp?api_key=your-qianfan-key', NULL, 1, 0, 1, 1);
