package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.AgentGraphData;
import com.lemon.lemonagent.model.runtime.SaveAgentGraphReq;
import com.lemon.lemonagent.model.runtime.SaveAgentGraphResp;
import com.lemon.lemonagent.servicecore.runtime.AgentGraphService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 保存执行图处理器：路由三元组 {@code ANY / ANY / GRAPH_SAVE}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "GRAPH_SAVE")
public class SaveAgentGraphProcess extends Handler<SaveAgentGraphReq, SaveAgentGraphResp> {

    @Resource
    private AgentGraphService agentGraphService;

    /**
     * 保存执行图（userId 由服务端填充，校验在领域服务里做）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(SaveAgentGraphReq request, SaveAgentGraphResp response) {
        AgentGraphData graph = new AgentGraphData();
        graph.setNodes(request.getNodes());
        graph.setEdges(request.getEdges());
        response.setData(agentGraphService.save(request.getUserId(), request.getAgentId(), graph));
    }

}
