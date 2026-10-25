package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/** 重试失败运行请求：按原运行记录的输入 / 策略 / 深度思考重放。 */
@Getter
@Setter
public class RetryRunReq extends BaseReq {

    /** 归属用户（服务端回填） */
    private Long userId;

    /** 运行 ID（路径参数回填） */
    private Long runId;

    /** 填充路由三元组。 */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.RUN_RETRY);
    }

}
