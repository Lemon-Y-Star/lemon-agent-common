package com.lemon.lemonagent.model.runtime;

import lombok.Data;

/** 执行图边：from → to。route 预留给条件路由（v3.2.0 只支持空 = 恒走）。 */
@Data
public class GraphEdge {

    /** 起点节点 ID */
    private String from;

    /** 终点节点 ID */
    private String to;

    /** 条件路由名（预注册谓词，v3.2.0 恒空） */
    private String route;

}
