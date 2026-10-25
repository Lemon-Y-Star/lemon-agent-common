package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.ToggleMemoryReq;
import com.lemon.lemonagent.model.runtime.ToggleMemoryResp;
import com.lemon.lemonagent.servicecore.runtime.MemoryService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 启停长期记忆处理器：路由三元组 {@code ANY / ANY / MEMORY_TOGGLE}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "MEMORY_TOGGLE")
public class ToggleMemoryProcess extends Handler<ToggleMemoryReq, ToggleMemoryResp> {

    @Resource
    private MemoryService memoryService;

    @Override
    protected void doExecute(ToggleMemoryReq request, ToggleMemoryResp response) {
        memoryService.toggle(request.getUserId(), request.getMemoryId(), request.getEnabled());
    }

}
