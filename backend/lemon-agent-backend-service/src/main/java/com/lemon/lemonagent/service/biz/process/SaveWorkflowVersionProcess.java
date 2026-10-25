package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.SaveWorkflowVersionReq;
import com.lemon.lemonagent.model.runtime.SaveWorkflowVersionResp;
import com.lemon.lemonagent.servicecore.runtime.WorkflowService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 工作流保存新版本处理器：路由三元组 {@code ANY / ANY / WORKFLOW_VERSION_SAVE}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "WORKFLOW_VERSION_SAVE")
public class SaveWorkflowVersionProcess extends Handler<SaveWorkflowVersionReq, SaveWorkflowVersionResp> {

    @Resource
    private WorkflowService workflowService;

    /**
     * 从来源 Agent 的当前执行图另存为工作流下一版本。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(SaveWorkflowVersionReq request, SaveWorkflowVersionResp response) {
        response.setData(workflowService.saveVersion(request.getUserId(), request.getWorkflowId(),
                request.getSourceAgentId(), request.getNote()));
    }

}
