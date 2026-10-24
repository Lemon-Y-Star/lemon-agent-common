package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.DeleteAgentReq;
import com.lemon.lemonagent.model.runtime.DeleteAgentResp;
import com.lemon.lemonagent.servicecore.runtime.AgentConfigService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 删除个人 Agent 处理器：路由三元组 {@code ANY / ANY / AGENT_DELETE}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "AGENT_DELETE")
public class DeleteAgentProcess extends Handler<DeleteAgentReq, DeleteAgentResp> {

    @Resource
    private AgentConfigService agentConfigService;

    /**
     * 删除个人 Agent（userId 由服务端填充）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(DeleteAgentReq request, DeleteAgentResp response) {
        agentConfigService.delete(request.getUserId(), request.getAgentId());
    }

}
