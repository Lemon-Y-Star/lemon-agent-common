package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.RenameSessionReq;
import com.lemon.lemonagent.model.runtime.RenameSessionResp;
import com.lemon.lemonagent.model.runtime.SessionData;
import com.lemon.lemonagent.servicecore.runtime.AgentRuntimeService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 重命名会话处理器：路由三元组 {@code ANY / ANY / SESSION_RENAME}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "SESSION_RENAME")
public class RenameSessionProcess extends Handler<RenameSessionReq, RenameSessionResp> {

    @Resource
    private AgentRuntimeService agentRuntimeService;

    /**
     * 重命名会话（userId 由服务端填充）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(RenameSessionReq request, RenameSessionResp response) {
        response.setData(agentRuntimeService.rename(request.getUserId(), request.getSessionId(), request.getTitle()));
    }

}
