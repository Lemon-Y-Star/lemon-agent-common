package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.ApiKeyCreatedData;
import com.lemon.lemonagent.model.runtime.CreateApiKeyReq;
import com.lemon.lemonagent.model.runtime.CreateApiKeyResp;
import com.lemon.lemonagent.servicecore.runtime.ApiKeyService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 创建 API 密钥处理器：路由三元组 {@code ANY / ANY / APIKEY_CREATE}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "APIKEY_CREATE")
public class CreateApiKeyProcess extends Handler<CreateApiKeyReq, CreateApiKeyResp> {

    @Resource
    private ApiKeyService apiKeyService;

    /**
     * 创建密钥（明文只在本响应出现一次）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(CreateApiKeyReq request, CreateApiKeyResp response) {
        response.setData(apiKeyService.create(request.getUserId(), request.getName()));
    }

}
