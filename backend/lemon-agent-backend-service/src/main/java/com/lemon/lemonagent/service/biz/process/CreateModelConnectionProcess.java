package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.modelconn.CreateModelConnectionReq;
import com.lemon.lemonagent.model.modelconn.CreateModelConnectionResp;
import com.lemon.lemonagent.model.modelconn.ModelConnectionData;
import com.lemon.lemonagent.servicecore.conn.ModelConnectionService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

import java.util.List;

/**
 * 新建模型连接处理器：路由三元组 {@code ANY / ANY / MODEL_CONN_CREATE}。
 *
 * <p>主机白名单与必填校验在领域服务里（{@code ModelConnectionServiceImpl#validate}），
 * 这里只做转交。</p>
 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "MODEL_CONN_CREATE")
public class CreateModelConnectionProcess extends Handler<CreateModelConnectionReq, CreateModelConnectionResp> {

    @Resource
    private ModelConnectionService modelConnectionService;

    /**
     * 新建连接。
     *
     * @param request  已通过校验的请求（userId 由服务端填充）
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(CreateModelConnectionReq request, CreateModelConnectionResp response) {
        List<String> models = request.getModels();
        ModelConnectionData data = modelConnectionService.create(request.getUserId(),
                request.getName(), request.getProvider(), request.getBaseUrl(),
                request.getApiKey(), models, Boolean.TRUE.equals(request.getAllowAuxiliary()));
        response.setData(data);
        log.info("模型连接已创建 userId:{} name:{}", request.getUserId(), request.getName());
    }

}
