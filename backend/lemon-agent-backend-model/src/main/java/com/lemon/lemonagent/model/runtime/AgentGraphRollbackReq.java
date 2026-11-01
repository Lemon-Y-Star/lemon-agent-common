package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/** Agent 执行图版本回滚请求（v4.1：历史版本另存为新的生效版本，userId/agentId 由服务端回填）。 */
@Getter
@Setter
public class AgentGraphRollbackReq extends BaseReq {

    /** 归属用户（服务端回填） */
    private Long userId;

    /** Agent ID（路径参数回填） */
    private Long agentId;

    /** 要回滚到的历史版本（路径参数回填） */
    private Integer version;

    /** 填充路由三元组。 */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.AGENT_GRAPH_ROLLBACK);
    }

}
