package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.AgentGraphData;
import com.lemon.lemonagent.model.runtime.SaveWorkflowGraphReq;
import com.lemon.lemonagent.model.runtime.SaveWorkflowGraphResp;
import com.lemon.lemonagent.servicecore.runtime.WorkflowService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 工作流独立画布保存处理器：路由三元组 {@code ANY / ANY / WORKFLOW_GRAPH_SAVE}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "WORKFLOW_GRAPH_SAVE")
public class SaveWorkflowGraphProcess extends Handler<SaveWorkflowGraphReq, SaveWorkflowGraphResp> {

    @Resource
    private WorkflowService workflowService;

    /**
     * 画布保存 = 工作流新版本（userId/workflowId 由服务端填充，校验在领域服务里做）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(SaveWorkflowGraphReq request, SaveWorkflowGraphResp response) {
        AgentGraphData graph = new AgentGraphData();
        graph.setNodes(request.getNodes());
        graph.setEdges(request.getEdges());
        response.setData(workflowService.saveGraph(request.getUserId(), request.getWorkflowId(), graph));
    }

}
