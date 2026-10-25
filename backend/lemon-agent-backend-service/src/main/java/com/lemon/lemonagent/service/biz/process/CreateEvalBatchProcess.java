package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.CreateEvalBatchReq;
import com.lemon.lemonagent.model.runtime.CreateEvalBatchResp;
import com.lemon.lemonagent.servicecore.runtime.EvalService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 发起评测批次处理器：路由三元组 {@code ANY / ANY / EVAL_CREATE}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "EVAL_CREATE")
public class CreateEvalBatchProcess extends Handler<CreateEvalBatchReq, CreateEvalBatchResp> {

    @Resource
    private EvalService evalService;

    /**
     * 发起评测批次。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(CreateEvalBatchReq request, CreateEvalBatchResp response) {
        response.setData(evalService.create(request.getUserId(), request.getAgentId(),
                request.getInputs(), request.getNote()));
    }

}
