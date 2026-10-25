package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/** 工作流保存新版本请求：从某个 Agent 的当前执行图另存为该工作流的下一版本。 */
@Getter
@Setter
public class SaveWorkflowVersionReq extends BaseReq {

    /** 归属用户（服务端回填） */
    private Long userId;

    /** 工作流 ID（路径参数回填） */
    private Long workflowId;

    /** 来源 Agent（读取它的当前执行图） */
    private Long sourceAgentId;

    /** 版本说明 */
    private String note;

    /** 填充路由三元组。 */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.WORKFLOW_VERSION_SAVE);
    }

}
