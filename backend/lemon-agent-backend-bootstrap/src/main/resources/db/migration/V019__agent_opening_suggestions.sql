-- v4.5 对话开场白与建议问题（Dify Opening Statement / Suggested Questions 同款能力）：
-- 开场白在会话无消息时渲染，建议问题渲染为可点击 chips（点击即作为用户输入发起运行）。
ALTER TABLE agent_config
    ADD COLUMN opening_statement  VARCHAR(500) NULL COMMENT '对话开场白；NULL/空 = 不展示' AFTER history_turns,
    ADD COLUMN suggested_questions VARCHAR(1000) NULL COMMENT '建议问题（JSON 数组字符串，至多 5 条）；NULL = 不展示' AFTER opening_statement;
