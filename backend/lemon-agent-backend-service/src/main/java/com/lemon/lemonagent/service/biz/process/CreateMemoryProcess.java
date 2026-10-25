package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.CreateMemoryReq;
import com.lemon.lemonagent.model.runtime.CreateMemoryResp;
import com.lemon.lemonagent.servicecore.runtime.MemoryService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 新增长期记忆处理器：路由三元组 {@code ANY / ANY / MEMORY_CREATE}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "MEMORY_CREATE")
public class CreateMemoryProcess extends Handler<CreateMemoryReq, CreateMemoryResp> {

    @Resource
    private MemoryService memoryService;

    @Override
    protected void doExecute(CreateMemoryReq request, CreateMemoryResp response) {
        response.setData(memoryService.create(request.getUserId(), request.getContent()));
    }

}
