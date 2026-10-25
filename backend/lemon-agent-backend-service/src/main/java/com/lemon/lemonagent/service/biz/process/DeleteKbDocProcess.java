package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.DeleteKbDocReq;
import com.lemon.lemonagent.model.runtime.DeleteKbDocResp;
import com.lemon.lemonagent.servicecore.runtime.KnowledgeService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 删除知识库文档处理器：路由三元组 {@code ANY / ANY / KB_DOC_DELETE}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "KB_DOC_DELETE")
public class DeleteKbDocProcess extends Handler<DeleteKbDocReq, DeleteKbDocResp> {

    @Resource
    private KnowledgeService knowledgeService;

    @Override
    protected void doExecute(DeleteKbDocReq request, DeleteKbDocResp response) {
        knowledgeService.delete(request.getUserId(), request.getDocId());
    }

}
