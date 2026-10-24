package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.AgentConfigData;
import com.lemon.lemonagent.model.runtime.CreateAgentReq;
import com.lemon.lemonagent.model.runtime.CreateAgentResp;
import com.lemon.lemonagent.servicecore.runtime.AgentConfigService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 创建个人 Agent 处理器：路由三元组 {@code ANY / ANY / AGENT_CREATE}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "AGENT_CREATE")
public class CreateAgentProcess extends Handler<CreateAgentReq, CreateAgentResp> {

    @Resource
    private AgentConfigService agentConfigService;

    /**
     * 创建个人 Agent（userId 由服务端填充）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(CreateAgentReq request, CreateAgentResp response) {
        response.setData(agentConfigService.create(request.getUserId(), request.getName(),
                request.getDescription(), request.getInstruction(), request.getModelConnectionId()));
    }

}
