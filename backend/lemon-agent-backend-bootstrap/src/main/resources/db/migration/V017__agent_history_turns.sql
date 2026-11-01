-- 短期记忆窗口（v4.2）：Agent 级可配置的会话历史轮数。
-- 1 轮 = 用户 + 助手各一条消息；NULL = 默认 10 轮（与原 HISTORY_WINDOW=20 条等价，存量 Agent 行为不变）。
-- 0 = 不携带历史（每次运行只看本轮输入）；上限 50 轮，防 prompt 无界膨胀。
ALTER TABLE agent_config
    ADD COLUMN history_turns INT NULL COMMENT '短期记忆窗口轮数（1 轮=用户+助手各 1 条；NULL=默认 10；0=不带历史；上限 50）';
