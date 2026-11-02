package com.lemon.lemonagent.service.biz.process.workflow;

import com.lemon.lemonagent.model.runtime.RollbackWorkflowReq;
import com.lemon.lemonagent.model.runtime.RollbackWorkflowResp;
import com.lemon.lemonagent.servicecore.runtime.workflow.WorkflowService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 工作流版本回滚处理器：路由三元组 {@code ANY / ANY / WORKFLOW_ROLLBACK}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "WORKFLOW_ROLLBACK")
public class RollbackWorkflowProcess extends Handler<RollbackWorkflowReq, RollbackWorkflowResp> {

    @Resource
    /** 工作流领域服务。 */
    private WorkflowService workflowService;

    /**
     * 版本回滚（userId/workflowId/version 由服务端填充，校验在领域服务里做）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(RollbackWorkflowReq request, RollbackWorkflowResp response) {
        response.setData(workflowService.rollback(request.getUserId(), request.getWorkflowId(),
                request.getVersion()));
    }

}
