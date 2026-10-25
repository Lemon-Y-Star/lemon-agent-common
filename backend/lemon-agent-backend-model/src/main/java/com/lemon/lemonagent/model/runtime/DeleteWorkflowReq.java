package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/** 删除工作流请求（被 Agent 挂载时禁止删除）。 */
@Getter
@Setter
public class DeleteWorkflowReq extends BaseReq {

    /** 归属用户（服务端回填） */
    private Long userId;

    /** 工作流 ID（路径参数回填） */
    private Long workflowId;

    /** 填充路由三元组。 */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.WORKFLOW_DELETE);
    }

}
