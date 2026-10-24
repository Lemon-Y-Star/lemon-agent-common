package com.lemon.lemonagent.dal.entity;

import lombok.Data;

import java.time.Instant;

/**
 * MCP 连接（表 {@code mcp_connection}，PRD FR-12）。
 *
 * <p>{@code userId} 为 NULL 即平台公共连接（预置 11 个，editable=0，所有用户可见不可改）；
 * 非 NULL 为用户自建连接。</p>
 */
@Data
public class McpConnectionEntity {

    /** 主键 */
    private Long id;

    /** 归属用户；NULL 表示平台公共 */
    private Long userId;

    /** 连接名称 */
    private String name;

    /** 传输类型：SSE / STDIO / STREAMABLE */
    private String transportType;

    /** HTTP 端点（SSE / STREAMABLE） */
    private String url;

    /** 本地子进程命令（STDIO） */
    private String command;

    /** 密钥密文（可选） */
    private String secretCipher;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 是否可编辑：平台公共恒 0 */
    private Integer editable;

    /** 是否平台公共 */
    private Integer platformPublic;

    /** 不可用原因（可用时为 NULL） */
    private String unavailableReason;

    /** 版本（配置变更 +1） */
    private Integer version;

    /** 创建时间 */
    private Instant createdAt;

    /** 更新时间 */
    private Instant updatedAt;

}
