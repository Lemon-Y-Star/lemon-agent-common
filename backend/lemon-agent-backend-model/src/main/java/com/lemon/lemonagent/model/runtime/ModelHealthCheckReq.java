package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/** 模型连接健康立即检测请求（v4.1：对当前用户全部启用连接探活一次）。 */
@Getter
@Setter
public class ModelHealthCheckReq extends BaseReq {

    /** 归属用户（服务端回填） */
    private Long userId;

    /** 填充路由三元组。 */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.MODEL_HEALTH_CHECK);
    }

}
