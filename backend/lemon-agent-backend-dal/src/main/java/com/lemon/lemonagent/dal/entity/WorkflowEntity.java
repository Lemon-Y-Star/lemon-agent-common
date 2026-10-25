package com.lemon.lemonagent.dal.entity;

import lombok.Data;

import java.time.Instant;

/** 工作流本体（表 workflow）：用户级独立实体，可被多个 Agent 挂载。 */
@Data
public class WorkflowEntity {

    /** 主键 */
    private Long id;

    /** 归属用户 */
    private Long userId;

    /** 名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 1=启用 / 0=停用 */
    private Integer status;

    /** 创建时间 */
    private Instant createdAt;

    /** 更新时间 */
    private Instant updatedAt;

}
