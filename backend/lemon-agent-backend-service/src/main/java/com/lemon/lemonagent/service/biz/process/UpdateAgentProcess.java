package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.AgentConfigData;
import com.lemon.lemonagent.model.runtime.UpdateAgentReq;
import com.lemon.lemonagent.model.runtime.UpdateAgentResp;
import com.lemon.lemonagent.servicecore.runtime.AgentConfigService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 更新个人 Agent 处理器：路由三元组 {@code ANY / ANY / AGENT_UPDATE}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "AGENT_UPDATE")
public class UpdateAgentProcess extends Handler<UpdateAgentReq, UpdateAgentResp> {

    @Resource
    private AgentConfigService agentConfigService;

    /**
     * 更新个人 Agent（userId 由服务端填充）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(UpdateAgentReq request, UpdateAgentResp response) {
        response.setData(agentConfigService.update(request.getUserId(), request.getAgentId(),
                request.getName(), request.getDescription(), request.getInstruction(), request.getTools(),
                request.getModelConnectionId(), request.getEnabled()));
    }

}
