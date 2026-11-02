package com.lemon.lemonagent.service.biz.process.memory;

import com.lemon.lemonagent.model.runtime.DeleteMemoryReq;
import com.lemon.lemonagent.model.runtime.DeleteMemoryResp;
import com.lemon.lemonagent.servicecore.runtime.MemoryService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 删除长期记忆处理器：路由三元组 {@code ANY / ANY / MEMORY_DELETE}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "MEMORY_DELETE")
public class DeleteMemoryProcess extends Handler<DeleteMemoryReq, DeleteMemoryResp> {

    @Resource
    /** 长期记忆领域服务。 */
    private MemoryService memoryService;
    /**
     * 删除长期记忆条目（userId 由服务端填充）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(DeleteMemoryReq request, DeleteMemoryResp response) {
        memoryService.delete(request.getUserId(), request.getMemoryId());
    }

}
