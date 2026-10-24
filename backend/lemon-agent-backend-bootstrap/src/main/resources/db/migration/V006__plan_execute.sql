-- v3.1 策略模板 plan-execute：计划快照与步骤游标落库（执行器无内存态，重启/审批恢复后语义不变）
-- plan_json：步骤数组 [{"title","detail","status","output"}]，status = PENDING/RUNNING/DONE/FAILED
-- plan_step：当前步骤下标（0 起）；计划跑完等于 steps.length

ALTER TABLE agent_run
    ADD COLUMN plan_json TEXT NULL COMMENT '计划快照（plan 策略）' AFTER pending_approval_json,
    ADD COLUMN plan_step INT NOT NULL DEFAULT 0 COMMENT '当前计划步骤下标（0 起）' AFTER plan_json;
