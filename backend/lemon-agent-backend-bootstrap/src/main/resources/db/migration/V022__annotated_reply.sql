-- v4.5.10 标注回复闭环：差评时可附标准答案，自动沉淀进知识库（【标注】前缀文档），
-- 与未命中队列、评测回归串成完整运营闭环
ALTER TABLE run_feedback
    ADD COLUMN expected_answer TEXT NULL COMMENT '标准答案（差评标注，自动沉淀知识库）' AFTER comment;
