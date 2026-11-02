package com.lemon.lemonagent.service.biz.process.memory;

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
    /** 长期记忆领域服务。 */
    private MemoryService memoryService;
    /**
     * 新增长期记忆条目（默认启用；启用条目由 MemoryService 聚合进系统提示）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(CreateMemoryReq request, CreateMemoryResp response) {
        response.setData(memoryService.create(request.getUserId(), request.getContent()));
    }

}
