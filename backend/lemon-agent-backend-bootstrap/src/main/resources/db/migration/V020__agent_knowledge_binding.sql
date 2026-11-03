-- v4.5.2 Agent 级知识库绑定（Dify「AGENT 知识库」同款）：
-- 绑定后 search_docs 只在指定文档范围内检索；NULL/空 = 保持现状搜全部（向后兼容）。
ALTER TABLE agent_config
    ADD COLUMN knowledge_doc_ids VARCHAR(1000) NULL COMMENT '绑定的知识库文档 ID（JSON 数组）；NULL/空 = 检索不限定范围' AFTER suggested_questions;
