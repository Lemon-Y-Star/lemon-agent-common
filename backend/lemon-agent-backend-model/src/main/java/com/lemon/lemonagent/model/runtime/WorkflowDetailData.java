package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.List;

/** 工作流详情（含版本列表）。 */
@Data
public class WorkflowDetailData {

    /** 工作流 ID */
    private Long id;

    /** 名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 启用状态 */
    private boolean enabled;

    /** 当前生效版本；0 = 还没有版本 */
    private Integer currentVersion;

    /** 版本列表（倒序） */
    private List<WorkflowVersionData> versions;

    /** 创建时间 */
    private String createdAt;

    /** 更新时间 */
    private String updatedAt;

}
