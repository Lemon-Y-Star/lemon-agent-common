package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/** 取消运行请求（契约 4.9 无 body，runId 由路径参数回填）。 */
@Getter
@Setter
public class CancelRunReq extends BaseReq {

    /** 归属用户（服务端由认证拦截器回填） */
    private Long userId;

    /** 运行 ID */
    private Long runId;

    /** 填充路由三元组。 */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.RUN_CANCEL);
    }

}
