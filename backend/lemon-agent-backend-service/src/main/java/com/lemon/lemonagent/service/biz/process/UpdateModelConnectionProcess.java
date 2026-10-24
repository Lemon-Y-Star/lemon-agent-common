package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.modelconn.ModelConnectionData;
import com.lemon.lemonagent.model.modelconn.UpdateModelConnectionReq;
import com.lemon.lemonagent.model.modelconn.UpdateModelConnectionResp;
import com.lemon.lemonagent.servicecore.conn.ModelConnectionService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/**
 * 更新模型连接处理器：路由三元组 {@code ANY / ANY / MODEL_CONN_UPDATE}。
 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "MODEL_CONN_UPDATE")
public class UpdateModelConnectionProcess extends Handler<UpdateModelConnectionReq, UpdateModelConnectionResp> {

    @Resource
    private ModelConnectionService modelConnectionService;

    /**
     * 更新连接（apiKey 为空表示保留原密钥）。
     *
     * @param request  已通过校验的请求（userId 由服务端填充）
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(UpdateModelConnectionReq request, UpdateModelConnectionResp response) {
        ModelConnectionData data = modelConnectionService.update(request.getUserId(), request.getId(),
                request.getName(), request.getProvider(), request.getBaseUrl(), request.getApiKey(),
                request.getModels(), request.getAllowAuxiliary(), request.getEnabled());
        response.setData(data);
        log.info("模型连接已更新 userId:{} id:{}", request.getUserId(), request.getId());
    }

}
