package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/** 删除个人 Agent 请求。 */
@Getter
@Setter
public class DeleteAgentReq extends BaseReq {

    /** 归属用户（服务端回填） */
    private Long userId;

    /** Agent ID */
    private Long agentId;

    /** 填充路由三元组。 */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.AGENT_DELETE);
    }

}
