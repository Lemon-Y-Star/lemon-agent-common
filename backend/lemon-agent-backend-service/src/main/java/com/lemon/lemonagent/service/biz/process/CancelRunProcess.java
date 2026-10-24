package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.CancelRunReq;
import com.lemon.lemonagent.model.runtime.CancelRunResp;
import com.lemon.lemonagent.servicecore.runtime.AgentRuntimeService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/**
 * 取消运行处理器：路由三元组 {@code ANY / ANY / RUN_CANCEL}。
 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "RUN_CANCEL")
public class CancelRunProcess extends Handler<CancelRunReq, CancelRunResp> {

    @Resource
    private AgentRuntimeService agentRuntimeService;

    /**
     * 取消运行（userId 由服务端填充）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(CancelRunReq request, CancelRunResp response) {
        response.setData(agentRuntimeService.cancel(request.getUserId(), request.getRunId()));
        log.info("运行已取消 userId:{} runId:{}", request.getUserId(), request.getRunId());
    }

}
