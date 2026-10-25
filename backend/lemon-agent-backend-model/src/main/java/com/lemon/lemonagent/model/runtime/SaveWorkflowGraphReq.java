package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 工作流独立画布保存请求（整体覆盖：nodes + edges，保存 = 新版本）。 */
@Getter
@Setter
public class SaveWorkflowGraphReq extends BaseReq {

    /** 归属用户（服务端回填） */
    private Long userId;

    /** 工作流 ID（路径参数回填） */
    private Long workflowId;

    /** 节点 */
    private List<GraphNode> nodes;

    /** 边 */
    private List<GraphEdge> edges;

    /** 填充路由三元组。 */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.WORKFLOW_GRAPH_SAVE);
    }

}
