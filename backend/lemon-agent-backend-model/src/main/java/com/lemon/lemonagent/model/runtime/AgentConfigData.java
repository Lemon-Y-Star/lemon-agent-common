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
