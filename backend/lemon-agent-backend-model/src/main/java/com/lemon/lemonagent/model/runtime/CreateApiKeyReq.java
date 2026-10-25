package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/** 创建 API 密钥请求。 */
@Getter
@Setter
public class CreateApiKeyReq extends BaseReq {

    /** 归属用户（服务端回填） */
    private Long userId;

    /** 名称 */
    private String name;

    /** 填充路由三元组。 */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.APIKEY_CREATE);
    }

}
