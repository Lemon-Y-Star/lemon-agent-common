-- v4.0 运行策略前置到 Agent：默认策略成为 Agent 的属性（对话页不再每次手选）。
-- NULL = auto（兼容存量行，含平台公共 Agent）；请求显式指定策略时仍以请求为准（API 分发 /open/** 同口径）。
ALTER TABLE agent_config
    ADD COLUMN strategy VARCHAR(16) NULL COMMENT '默认运行策略：auto/plan/custom/fixed；NULL = auto'
    AFTER tools_json;
