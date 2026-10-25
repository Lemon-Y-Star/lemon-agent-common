package com.lemon.lemonagent.model.runtime;

import lombok.Data;

/** 工作流版本摘要（不含完整图，避免列表过大）。 */
@Data
public class WorkflowVersionData {

    /** 版本号 */
    private Integer version;

    /** 版本说明（如来源 Agent） */
    private String note;

    /** 是否当前生效版本 */
    private boolean active;

    /** 节点数（便于前端一眼看出图的规模） */
    private Integer nodeCount;

    /** 创建时间 */
    private String createdAt;

}
