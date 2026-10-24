package com.lemon.lemonagent.dal.entity;

import lombok.Data;

import java.time.Instant;

/**
 * Agent 配置（表 agent_config，PRD FR-6 最小充分版）。
 *
 * <p>{@code userId} 为 NULL 即平台公共 Agent（所有用户可见、不可编辑）；
 * 非 NULL 为用户自建。运行挂载跟随当前已启用版本（PRD S2 的工具语义）。</p>
 */
@Data
public class AgentConfigEntity {

    /** Agent ID */
    private Long id;

    /** 归属用户；NULL 表示平台公共 */
    private Long userId;

    /** 名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 系统提示词 */
    private String instruction;

    /** 可用工具白名单（JSON 数组字符串）；NULL = 全部已注册工具 */
    private String toolsJson;

    /** 绑定模型连接；NULL = 用户最新启用连接 */
    private Long modelConnectionId;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 版本（配置变更 +1） */
    private Integer version;

    /** 创建时间 */
    private Instant createdAt;

    /** 更新时间 */
    private Instant updatedAt;

}
