package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/** 发起运行请求（契约 4.5）：sessionId 缺省 = 懒创建；clientRequestId 为幂等键。 */
@Getter
@Setter
public class CreateRunReq extends BaseReq {

    /** 归属用户（服务端回填） */
    private Long userId;

    /** 会话 ID（空 = 懒创建） */
    private Long sessionId;

    /** 用户输入（1..4000 字符） */
    private String input;

    /** 策略（默认 auto） */
    private String strategy;

    /** 幂等键（前端生成的 UUID） */
    private String clientRequestId;

    /** 填充路由三元组。 */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.RUN_CREATE);
    }

}
