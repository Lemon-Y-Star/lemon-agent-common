package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.CreateKbDocReq;
import com.lemon.lemonagent.model.runtime.CreateKbDocResp;
import com.lemon.lemonagent.servicecore.runtime.KnowledgeService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 新增知识库文档处理器：路由三元组 {@code ANY / ANY / KB_DOC_CREATE}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "KB_DOC_CREATE")
public class CreateKbDocProcess extends Handler<CreateKbDocReq, CreateKbDocResp> {

    @Resource
    private KnowledgeService knowledgeService;

    @Override
    protected void doExecute(CreateKbDocReq request, CreateKbDocResp response) {
        response.setData(knowledgeService.create(request.getUserId(), request.getTitle(), request.getContent()));
    }

}
