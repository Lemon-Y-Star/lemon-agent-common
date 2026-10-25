package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/** 创建工作流请求；{@code sourceAgentId} 非空时把该 Agent 的当前执行图另存为工作流 v1。 */
@Getter
@Setter
public class CreateWorkflowReq extends BaseReq {

    /** 归属用户（服务端回填） */
    private Long userId;

    /** 名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 来源 Agent（从它的当前执行图另存为 v1）；null = 建空工作流 */
    private Long sourceAgentId;

    /** 填充路由三元组。 */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.WORKFLOW_CREATE);
    }

}
