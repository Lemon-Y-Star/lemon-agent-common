package com.lemon.lemonagent.dal.entity;

import lombok.Data;

import java.time.Instant;

/** 工作流版本（表 workflow_version）：版本化图快照，status=1 为当前生效版本。 */
@Data
public class WorkflowVersionEntity {

    /** 主键 */
    private Long id;

    /** 所属工作流 */
    private Long workflowId;

    /** 版本（保存 +1） */
    private Integer version;

    /** 图定义 JSON {nodes:[], edges:[]} */
    private String graphJson;

    /** 版本说明（如来源 Agent） */
    private String note;

    /** 1=当前生效 / 0=历史版本 */
    private Integer status;

    /** 创建时间 */
    private Instant createdAt;

    /** 更新时间 */
    private Instant updatedAt;

}
