-- v3.9 深度思考（CoT 模式）：运行级开关；推理内容不单独落库，
-- 而是作为 <think> 标签随正文流式输出，前端负责拆分「思考 / 结论」与展开收起。

ALTER TABLE agent_run
    ADD COLUMN thinking TINYINT NULL COMMENT '深度思考开关：1=开启（执行器追加 CoT 指令）';
