package com.lemon.lemonagent.model.runtime;

import lombok.Data;

/** 工作流列表项。 */
@Data
public class WorkflowData {

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

    /** 版本总数 */
    private Integer versionCount;

    /** 创建时间 */
    private String createdAt;

    /** 更新时间 */
    private String updatedAt;

}
