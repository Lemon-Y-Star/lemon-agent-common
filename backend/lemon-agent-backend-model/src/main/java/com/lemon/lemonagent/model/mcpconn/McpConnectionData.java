package com.lemon.lemonagent.model.mcpconn;

import lombok.Data;

/**
 * 单条 MCP 连接的对外数据（字段对齐 PRD FR-12）。
 */
@Data
public class McpConnectionData {

    /** 连接 ID */
    private Long id;

    /** 归属用户 ID；null 表示平台公共连接 */
    private Long userId;

    /** 连接名称 */
    private String name;

    /** 传输类型：SSE / STDIO / STREAMABLE */
    private String transportType;

    /** HTTP 端点（SSE / STREAMABLE 使用） */
    private String url;

    /** 本地子进程命令（STDIO 使用） */
    private String command;

    /** 是否已配置密钥（明文与密文均不回传） */
    private boolean hasSecret;

    /** 启用状态：true 启用 / false 停用 */
    private boolean enabled;

    /** 是否可编辑（平台公共连接恒为 false） */
    private boolean editable;

    /** 是否平台公共连接 */
    private boolean platformPublic;

    /** 是否可用（停用或有不可用原因时为 false） */
    private boolean usable;

    /** 不可用原因（可用时为 null） */
    private String unavailableReason;

    /** 版本（连接配置变更 +1，v1 固定 1） */
    private Integer version;

    /** 创建时间（ISO-8601） */
    private String createdAt;

    /** 更新时间（ISO-8601） */
    private String updatedAt;

}
