-- v4.6.0 Agent 分享链接：免登录公开对话页（发布渠道，Dify Share App 同款最小版）
-- 每 Agent 一条分享配置（upsert）；share_code 是 URL 里的公开标识，access_code 可选访问口令
CREATE TABLE agent_share (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '分享人（=Agent 归属）',
    agent_id    BIGINT       NOT NULL COMMENT 'Agent',
    share_code  VARCHAR(16)  NOT NULL COMMENT '公开标识（URL 用）',
    access_code VARCHAR(12)  NULL COMMENT '访问口令（空 = 无需口令）',
    enabled     TINYINT      NOT NULL DEFAULT 1 COMMENT '1=开启 / 0=关闭',
    created_at  TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent (agent_id),
    UNIQUE KEY uk_code (share_code),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Agent 分享（免登录公开对话页）';

-- 分享会话标记：公开页发起的会话挂在 Agent 主人名下（配额/运营可见），share_id 关联分享配置
ALTER TABLE agent_session
    ADD COLUMN share_id BIGINT NULL COMMENT '分享会话（关联 agent_share；NULL = 普通会话）';
