-- v4.5.4 开始节点输入表单（Dify Workflow 表单同款）：
-- 图 START 节点定义 formSchema（字段/名称/注释/必填/默认值/类型），
-- 触发运行时前端渲染表单收集，提交值序列化落库到本列；图执行时 START 节点产出=变量表文本。
ALTER TABLE agent_run
    ADD COLUMN form_values_json JSON NULL COMMENT '开始节点表单值（{字段: 值}）；无表单运行时为 NULL' AFTER graph_state_json;
