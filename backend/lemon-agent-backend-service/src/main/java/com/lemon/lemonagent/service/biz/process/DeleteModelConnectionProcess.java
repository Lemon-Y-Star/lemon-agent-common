package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.modelconn.DeleteModelConnectionReq;
import com.lemon.lemonagent.model.modelconn.DeleteModelConnectionResp;
import com.lemon.lemonagent.servicecore.conn.ModelConnectionService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/**
 * 删除模型连接处理器：路由三元组 {@code ANY / ANY / MODEL_CONN_DELETE}。
 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "MODEL_CONN_DELETE")
public class DeleteModelConnectionProcess extends Handler<DeleteModelConnectionReq, DeleteModelConnectionResp> {

    @Resource
    private ModelConnectionService modelConnectionService;

    /**
     * 删除连接。
     *
     * @param request  已通过校验的请求（userId 由服务端填充）
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(DeleteModelConnectionReq request, DeleteModelConnectionResp response) {
        modelConnectionService.delete(request.getUserId(), request.getId());
        log.info("模型连接已删除 userId:{} id:{}", request.getUserId(), request.getId());
    }

}
