package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.ApprovalReq;
import com.lemon.lemonagent.model.runtime.ApprovalResp;
import com.lemon.lemonagent.model.runtime.RunStatusData;
import com.lemon.lemonagent.servicecore.runtime.AgentRuntimeService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import com.lemon.architecture.util.validation.AssertUtils;
import jakarta.annotation.Resource;

/**
 * 提交审批决定处理器：路由三元组 {@code ANY / ANY / APPROVAL_SUBMIT}。
 *
 * <p>工具审批与节点审批共用本接口（契约 4.8）：恢复动作由服务端读
 * pending_approval_json.kind 决定，请求体无差别。</p>
 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "APPROVAL_SUBMIT")
public class SubmitApprovalProcess extends Handler<ApprovalReq, ApprovalResp> {

    @Resource
    private AgentRuntimeService agentRuntimeService;

    /**
     * 提交审批决定（userId 由服务端填充）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(ApprovalReq request, ApprovalResp response) {
        RunStatusData data = agentRuntimeService.approval(request.getUserId(), request.getRunId(),
                request.getApprovalId(), request.getDecision(), request.getComment());
        response.setData(data);
        log.info("审批已提交 userId:{} runId:{} decision:{}", request.getUserId(), request.getRunId(), request.getDecision());
    }

}
