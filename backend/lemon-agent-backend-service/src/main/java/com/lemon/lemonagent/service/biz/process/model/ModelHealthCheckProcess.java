package com.lemon.lemonagent.service.biz.process.model;

import com.lemon.lemonagent.model.runtime.ModelHealthCheckReq;
import com.lemon.lemonagent.model.runtime.ModelHealthCheckResp;
import com.lemon.lemonagent.servicecore.conn.ModelHealthProbe;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 模型连接健康立即检测处理器：路由三元组 {@code ANY / ANY / MODEL_HEALTH_CHECK}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "MODEL_HEALTH_CHECK")
public class ModelHealthCheckProcess extends Handler<ModelHealthCheckReq, ModelHealthCheckResp> {

    @Resource
    private ModelHealthProbe modelHealthProbe;

    /**
     * 立即检测（userId 由服务端填充；探测本身是尽力而为的探活，不抛业务异常）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(ModelHealthCheckReq request, ModelHealthCheckResp response) {
        response.setData(modelHealthProbe.probeUser(request.getUserId()));
    }

}
