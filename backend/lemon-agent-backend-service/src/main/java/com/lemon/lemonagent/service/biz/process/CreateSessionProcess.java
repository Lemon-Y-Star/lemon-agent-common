package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.CreateSessionReq;
import com.lemon.lemonagent.model.runtime.CreateSessionResp;
import com.lemon.lemonagent.model.runtime.SessionData;
import com.lemon.lemonagent.servicecore.runtime.AgentRuntimeService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/**
 * 创建会话处理器：路由三元组 {@code ANY / ANY / SESSION_CREATE}。
 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "SESSION_CREATE")
public class CreateSessionProcess extends Handler<CreateSessionReq, CreateSessionResp> {

    @Resource
    private AgentRuntimeService agentRuntimeService;

    /**
     * 创建会话（userId 由服务端填充）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(CreateSessionReq request, CreateSessionResp response) {
        SessionData data = agentRuntimeService.createSession(request.getUserId(), request.getTitle());
        response.setData(data);
        log.info("会话已创建 userId:{} sessionId:{}", request.getUserId(), data.getSessionId());
    }

}
