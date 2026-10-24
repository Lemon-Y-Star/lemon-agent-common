-- v2.1 工具审批：demo_ticket 补 body 字段（BUILD-SPEC §1.3 的 {title, body, priority} 参数）

ALTER TABLE demo_ticket
    ADD COLUMN body VARCHAR(1000) NULL COMMENT '工单内容' AFTER title;
