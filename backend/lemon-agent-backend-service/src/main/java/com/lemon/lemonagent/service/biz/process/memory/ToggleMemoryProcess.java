package com.lemon.lemonagent.service.biz.process.memory;

import com.lemon.lemonagent.model.runtime.ToggleMemoryReq;
import com.lemon.lemonagent.model.runtime.ToggleMemoryResp;
import com.lemon.lemonagent.servicecore.runtime.memory.MemoryService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 启停长期记忆处理器：路由三元组 {@code ANY / ANY / MEMORY_TOGGLE}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "MEMORY_TOGGLE")
public class ToggleMemoryProcess extends Handler<ToggleMemoryReq, ToggleMemoryResp> {

    @Resource
    /** 长期记忆领域服务。 */
    private MemoryService memoryService;
    /**
     * 启停长期记忆条目（停用后不再参与提示词聚合，不删除内容）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(ToggleMemoryReq request, ToggleMemoryResp response) {
        memoryService.toggle(request.getUserId(), request.getMemoryId(), request.getEnabled());
    }

}
