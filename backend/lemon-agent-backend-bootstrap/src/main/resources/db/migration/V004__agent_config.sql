-- v2.5 Agent 管理（PRD FR-6 最小充分版）：Agent 配置表 + 运行挂载
-- 策略边界（PRD S2）：对话 run 的 Agent 挂载跟随当前已启用版本（工具语义）；
-- 执行图与工作流的固定快照语义在 v3 执行图切片落地。

CREATE TABLE agent_config (
    id                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT 'Agent ID',
    user_id             BIGINT       NULL COMMENT '归属用户；NULL=平台公共（不可编辑）',
    name                VARCHAR(100) NOT NULL COMMENT 'Agent 名称',
    description         VARCHAR(500) NOT NULL DEFAULT '' COMMENT '描述',
    instruction         TEXT         NOT NULL COMMENT '系统提示词（Agent 人格与任务说明）',
    model_connection_id BIGINT       NULL COMMENT '绑定模型连接；NULL=用户最新启用连接',
    status              TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 / 0 停用',
    version             INT          NOT NULL DEFAULT 1 COMMENT '版本（配置变更 +1）',
    created_at          TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_agent_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Agent 配置（平台公共 + 用户自建）';

ALTER TABLE agent_run
    ADD COLUMN agent_id BIGINT NULL COMMENT '挂载的 Agent（跟随当前已启用版本）' AFTER session_id;

-- 平台公共 Agent 种子（不可编辑；用户可见可选用）
INSERT INTO agent_config (user_id, name, description, instruction, status, version) VALUES
(NULL, 'LemonAgent 通用助手',
 '平台默认助手：回答问题、调用工具完成简单任务。',
 '你是 LemonAgent 平台的通用智能助手。请用中文简洁准确地回答；需要创建工单时调用 create_ticket；涉及平台知识时先调用 search_docs 检索，hits 为空时直说文档中没有相关内容，不得编造。', 1, 1),
(NULL, '演示工单助手',
 '专注演示工单创建与审批流程的助手。',
 '你是工单办理助手。当用户表达出需要登记、跟踪或反馈的事项时，主动调用 create_ticket 创建工单（合理拟定标题与优先级），创建前先向用户复述工单要素。', 1, 1);
