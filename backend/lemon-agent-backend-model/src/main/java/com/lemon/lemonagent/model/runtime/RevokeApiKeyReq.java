package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/** 吊销 API 密钥请求。 */
@Getter
@Setter
public class RevokeApiKeyReq extends BaseReq {

    /** 归属用户（服务端回填） */
    private Long userId;

    /** 密钥 ID（路径参数回填） */
    private Long keyId;

    /** 填充路由三元组。 */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.APIKEY_REVOKE);
    }

}
