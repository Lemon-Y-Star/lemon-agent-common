package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.CreateWorkflowReq;
import com.lemon.lemonagent.model.runtime.CreateWorkflowResp;
import com.lemon.lemonagent.servicecore.runtime.WorkflowService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 创建工作流处理器：路由三元组 {@code ANY / ANY / WORKFLOW_CREATE}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "WORKFLOW_CREATE")
public class CreateWorkflowProcess extends Handler<CreateWorkflowReq, CreateWorkflowResp> {

    @Resource
    private WorkflowService workflowService;

    /**
     * 创建工作流（sourceAgentId 非空则把该 Agent 的当前执行图另存为 v1）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(CreateWorkflowReq request, CreateWorkflowResp response) {
        response.setData(workflowService.create(request.getUserId(), request.getName(),
                request.getDescription(), request.getSourceAgentId()));
    }

}
