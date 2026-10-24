package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.DeleteSessionReq;
import com.lemon.lemonagent.model.runtime.DeleteSessionResp;
import com.lemon.lemonagent.servicecore.runtime.AgentRuntimeService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 删除会话处理器：路由三元组 {@code ANY / ANY / SESSION_DELETE}（级联删除消息/事件/运行）。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "SESSION_DELETE")
public class DeleteSessionProcess extends Handler<DeleteSessionReq, DeleteSessionResp> {

    @Resource
    private AgentRuntimeService agentRuntimeService;

    /**
     * 删除会话（userId 由服务端填充）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(DeleteSessionReq request, DeleteSessionResp response) {
        agentRuntimeService.delete(request.getUserId(), request.getSessionId());
    }

}
