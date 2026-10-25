package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.DeleteWorkflowReq;
import com.lemon.lemonagent.model.runtime.DeleteWorkflowResp;
import com.lemon.lemonagent.servicecore.runtime.WorkflowService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 删除工作流处理器：路由三元组 {@code ANY / ANY / WORKFLOW_DELETE}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "WORKFLOW_DELETE")
public class DeleteWorkflowProcess extends Handler<DeleteWorkflowReq, DeleteWorkflowResp> {

    @Resource
    private WorkflowService workflowService;

    /**
     * 删除工作流（userId/workflowId 由服务端填充，归属与挂载检查在领域服务里做）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(DeleteWorkflowReq request, DeleteWorkflowResp response) {
        workflowService.delete(request.getUserId(), request.getWorkflowId());
    }

}
