-- v3.6 知识库 + 长期记忆（最小可用）
-- 知识库：文档存 MySQL，检索先走关键词（LIKE）降级；embedding 配好后可再叠加向量召回
-- 长期记忆：用户级备忘，运行时拼进 system prompt（限条数与长度，防止把人格撑爆）

CREATE TABLE kb_document (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '文档 ID',
    user_id    BIGINT       NOT NULL COMMENT '归属用户',
    title      VARCHAR(200) NOT NULL COMMENT '标题',
    content    TEXT         NOT NULL COMMENT '正文（纯文本/Markdown）',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_user (user_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识库文档';

CREATE TABLE agent_memory (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '记忆 ID',
    user_id    BIGINT       NOT NULL COMMENT '归属用户',
    content    VARCHAR(500) NOT NULL COMMENT '记忆内容（一句话事实/偏好）',
    enabled    TINYINT      NOT NULL DEFAULT 1 COMMENT '1 生效 / 0 停用',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_user (user_id, id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '长期记忆（用户级）';
