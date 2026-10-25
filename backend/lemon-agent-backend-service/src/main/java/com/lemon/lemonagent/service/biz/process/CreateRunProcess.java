package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.CreateRunReq;
import com.lemon.lemonagent.model.runtime.CreateRunResp;
import com.lemon.lemonagent.model.runtime.RunData;
import com.lemon.lemonagent.servicecore.runtime.AgentRuntimeService;
import com.lemon.lemonagent.servicecore.runtime.AgentRuntimeService.SessionBusyException;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

import java.util.Map;

/**
 * 发起运行处理器：路由三元组 {@code ANY / ANY / RUN_CREATE}。
 *
 * <p>幂等与互斥在领域服务里；会话互斥（SESSION_BUSY）在这里转成契约的
 * 错误包络（data.activeRunId 带出），其余错误交给框架异常收口。</p>
 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "RUN_CREATE")
public class CreateRunProcess extends Handler<CreateRunReq, CreateRunResp> {

    @Resource
    private AgentRuntimeService agentRuntimeService;

    /**
     * 发起运行（userId 由服务端填充）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(CreateRunReq request, CreateRunResp response) {
        try {
            RunData data = agentRuntimeService.createRun(request.getUserId(), request.getSessionId(),
                    request.getAgentId(), request.getInput(), request.getStrategy(),
                    request.getClientRequestId(), request.getThinking());
            response.setData(data);
            log.info("运行已创建 userId:{} runId:{} traceId:{}",
                    request.getUserId(), data.getRunId(), data.getTraceId());
        } catch (SessionBusyException e) {
            // 409 SESSION_BUSY：data.activeRunId 带出（契约 4.5）
            response.setCode(e.getCode());
            response.setMessage(e.getMessage());
            // CreateRunResp 的 data 为 Object：成功时 RunData，互斥时 activeRunId 包络
            ((com.lemon.architecture.model.BaseResult) response).setData(Map.of("activeRunId", e.getActiveRunId()));
        }
    }

}
