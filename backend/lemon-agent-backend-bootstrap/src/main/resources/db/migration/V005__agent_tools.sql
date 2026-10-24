-- v3.0 Agent 三要素补齐（PRD FR-6）：可用工具授权白名单
-- 语义：NULL = 该 Agent 使用全部已注册工具（向后兼容旧数据）；
--       显式数组 = 白名单，空数组 = 不装配任何工具。
-- 工具名必须与 ToolRegistry 注册名一致（current_time / search_docs / create_ticket）。

ALTER TABLE agent_config
    ADD COLUMN tools_json VARCHAR(500) NULL COMMENT '可用工具名 JSON 数组；NULL=全部工具' AFTER instruction;
