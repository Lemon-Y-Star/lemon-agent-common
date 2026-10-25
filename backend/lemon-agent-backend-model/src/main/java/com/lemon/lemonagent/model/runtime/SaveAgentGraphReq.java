package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 保存执行图请求（整体覆盖：nodes + edges）。 */
@Getter
@Setter
public class SaveAgentGraphReq extends BaseReq {

    /** 归属用户（服务端回填） */
    private Long userId;

    /** Agent ID（路径参数回填） */
    private Long agentId;

    /** 节点 */
    private List<GraphNode> nodes;

    /** 边 */
    private List<GraphEdge> edges;

    /** 填充路由三元组。 */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.GRAPH_SAVE);
    }

}
