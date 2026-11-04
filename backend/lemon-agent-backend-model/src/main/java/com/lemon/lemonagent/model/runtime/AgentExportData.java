package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.List;

/** Agent 导出/导入配置（v4.6.0）：跨环境迁移载体。绑定类 ID（模型连接/工作流/知识库文档）
 *  只在导出时留档参考，导入时一律置空——由使用者在目标环境手动重绑。 */
@Data
public class AgentExportData {
    /** 导出格式版本 */
    private String format = "lemon-agent-export/v1";
    /** 导出时间（ISO） */
    private String exportedAt;
    /** 名称 */
    private String name;
    /** 描述 */
    private String description;
    /** 系统提示词 */
    private String instruction;
    /** 工具白名单 */
    private List<String> tools;
    /** 运行策略 */
    private String strategy;
    /** 短期记忆轮数 */
    private Integer historyTurns;
    /** 开场白 */
    private String openingStatement;
    /** 建议问题 */
    private List<String> suggestedQuestions;
    /** 绑定的知识库文档 ID（仅留档，导入置空） */
    private List<Long> knowledgeDocIds;
    /** 绑定的模型连接 ID（仅留档，导入置空） */
    private Long modelConnectionId;
    /** 挂载的工作流 ID（仅留档，导入置空） */
    private Long workflowId;
    /** 挂载的工作流版本（仅留档，导入置空） */
    private Integer workflowVersion;
}
