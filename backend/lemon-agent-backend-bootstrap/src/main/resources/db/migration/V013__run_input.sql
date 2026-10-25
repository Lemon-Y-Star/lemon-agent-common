-- v3.9.1 失败重试：运行落原始输入，供「重试」端点按原样重放。

ALTER TABLE agent_run
    ADD COLUMN input VARCHAR(4000) NULL COMMENT '原始输入（重试 / 溯源用）';
