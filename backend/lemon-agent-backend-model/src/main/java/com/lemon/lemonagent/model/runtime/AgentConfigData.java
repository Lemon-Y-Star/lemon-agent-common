package com.lemon.lemonagent.model.runtime;

import lombok.Data;

/** 单个 Agent 配置的对外数据（契约 FR-6 最小充分版）。 */
@Data
public class AgentConfigData {

    /** Agent ID */
    private Long id;

    /** 归属用户；null 表示平台公共 */
    private Long userId;

    /** 名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 系统提示词（个人 Agent 可见；平台公共不下发明文） */
    private String instruction;

    /** 可用工具白名单；null = 全部已注册工具 */
    private java.util.List<String> tools;

    /** 当前执行图版本；0 = 还没有图（前端据此决定是否显示「执行图」入口） */
    private Integer graphVersion;

    /** 挂载的工作流；null = 用 Agent 自己的执行图 */
    private Long workflowId;

    /** 挂载的工作流版本；null = 跟随工作流当前生效版本 */
    private Integer workflowVersion;

    /** 挂载的工作流名称（列表/表单直接显示用；null = 未挂载） */
    private String workflowName;

    /** 绑定模型连接；null = 运行时取用户最新启用连接 */
    private Long modelConnectionId;

    /** 启用状态 */
    private boolean enabled;

    /** 是否平台公共（不可编辑） */
    private boolean platformPublic;

    /** 是否可用（停用则不可被对话选择） */
    private boolean usable;

    /** 版本 */
    private Integer version;

    /** 创建时间 */
    private String createdAt;

    /** 更新时间 */
    private String updatedAt;

}
