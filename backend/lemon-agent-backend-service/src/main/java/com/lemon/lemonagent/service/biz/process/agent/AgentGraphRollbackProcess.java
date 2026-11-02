package com.lemon.lemonagent.service.biz.process.agent;

import com.lemon.lemonagent.model.runtime.AgentGraphRollbackReq;
import com.lemon.lemonagent.model.runtime.AgentGraphRollbackResp;
import com.lemon.lemonagent.servicecore.runtime.AgentGraphService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** Agent 执行图版本回滚处理器：路由三元组 {@code ANY / ANY / AGENT_GRAPH_ROLLBACK}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "AGENT_GRAPH_ROLLBACK")
public class AgentGraphRollbackProcess extends Handler<AgentGraphRollbackReq, AgentGraphRollbackResp> {

    @Resource
    private AgentGraphService agentGraphService;

    /**
     * 版本回滚（userId/agentId/version 由服务端填充，校验在领域服务里做）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(AgentGraphRollbackReq request, AgentGraphRollbackResp response) {
        response.setData(agentGraphService.rollback(request.getUserId(), request.getAgentId(),
                request.getVersion()));
    }

}
