package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.RetryRunReq;
import com.lemon.lemonagent.model.runtime.RetryRunResp;
import com.lemon.lemonagent.servicecore.runtime.AgentRuntimeService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 重试失败运行处理器：路由三元组 {@code ANY / ANY / RUN_RETRY}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "RUN_RETRY")
public class RetryRunProcess extends Handler<RetryRunReq, RetryRunResp> {

    @Resource
    private AgentRuntimeService agentRuntimeService;

    /**
     * 重试（userId/runId 由服务端填充，状态与输入校验在领域服务里做）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(RetryRunReq request, RetryRunResp response) {
        response.setData(agentRuntimeService.retryRun(request.getUserId(), request.getRunId()));
    }

}
