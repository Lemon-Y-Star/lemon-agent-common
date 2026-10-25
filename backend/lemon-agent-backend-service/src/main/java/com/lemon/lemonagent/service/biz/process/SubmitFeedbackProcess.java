package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.FeedbackData;
import com.lemon.lemonagent.model.runtime.SubmitFeedbackReq;
import com.lemon.lemonagent.model.runtime.SubmitFeedbackResp;
import com.lemon.lemonagent.servicecore.runtime.ArtifactService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 运行反馈处理器：路由三元组 {@code ANY / ANY / RUN_FEEDBACK}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "RUN_FEEDBACK")
public class SubmitFeedbackProcess extends Handler<SubmitFeedbackReq, SubmitFeedbackResp> {

    @Resource
    private ArtifactService artifactService;

    /**
     * 提交反馈（upsert；userId/runId 由服务端填充，归属校验在领域服务里做）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(SubmitFeedbackReq request, SubmitFeedbackResp response) {
        FeedbackData data = artifactService.submit(request.getUserId(), request.getRunId(),
                request.getRating(), request.getComment());
        response.setData(data);
    }

}
