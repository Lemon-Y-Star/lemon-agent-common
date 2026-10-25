package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.runtime.RevokeApiKeyReq;
import com.lemon.lemonagent.model.runtime.RevokeApiKeyResp;
import com.lemon.lemonagent.servicecore.runtime.ApiKeyService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/** 吊销 API 密钥处理器：路由三元组 {@code ANY / ANY / APIKEY_REVOKE}。 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "APIKEY_REVOKE")
public class RevokeApiKeyProcess extends Handler<RevokeApiKeyReq, RevokeApiKeyResp> {

    @Resource
    private ApiKeyService apiKeyService;

    /**
     * 吊销（本人密钥置 0，即时失效）。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(RevokeApiKeyReq request, RevokeApiKeyResp response) {
        apiKeyService.revoke(request.getUserId(), request.getKeyId());
    }

}
