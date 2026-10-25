package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.List;

/** 工作流列表。 */
@Data
public class WorkflowsData {

    /** 工作流 */
    private List<WorkflowData> workflows;

}
