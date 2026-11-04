-- v4.9 对话图片输入（多模态）：run 与消息各存图片 data URL 数组（JSON）
-- base64 直存口径：单张 ≤2MB base64、≤4 张，个人平台量级可接受；调模型时 data URL 可直接喂 image_url
ALTER TABLE agent_run
    ADD COLUMN images_json MEDIUMTEXT NULL COMMENT '本轮用户输入附图（data URL JSON 数组，≤4 张）' AFTER form_values_json;

ALTER TABLE agent_message
    ADD COLUMN images_json MEDIUMTEXT NULL COMMENT '消息附图（data URL JSON 数组）' AFTER content;
