package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.List;

/** 执行图（PRD FR-7.1 §② 最小模型）：节点 + 边 + 版本。 */
@Data
public class AgentGraphData {

    /** 图版本（保存 +1；运行快照按版本落库） */
    private Integer version;

    /** 节点 */
    private List<GraphNode> nodes;

    /** 边（from → to；无环，Kahn 拓扑序执行） */
    private List<GraphEdge> edges;

    /** 平台/用户可编辑标记（平台 Agent 的图只读） */
    private boolean editable;

}
