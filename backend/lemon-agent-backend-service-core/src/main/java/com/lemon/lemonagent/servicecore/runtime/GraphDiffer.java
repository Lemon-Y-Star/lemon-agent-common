package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.model.runtime.AgentGraphData;
import com.lemon.lemonagent.model.runtime.GraphEdge;
import com.lemon.lemonagent.model.runtime.GraphNode;
import com.lemon.lemonagent.model.runtime.GraphDiffData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行图版本 diff（v4.1）：两版图的节点/边结构化对比。
 *
 * <p>节点变更只比语义字段（title / prompt / tool / args / approvalPrompt / tools），
 * 画布坐标 x/y 不参与——拖个位置不算改图。输出是人类可读字符串，前端直接列表渲染。</p>
 */
public final class GraphDiffer {

    private GraphDiffer() {
    }

    /**
     * 对比两版图。
     *
     * @param fromVersion 入参：基线版本
     * @param toVersion   入参：对比版本
     * @param fromJson    入参：基线图 JSON
     * @param toJson      入参：对比图 JSON
     * @return 出参：结构化 diff
     */
    public static GraphDiffData diff(int fromVersion, int toVersion, String fromJson, String toJson) {
        AgentGraphData a = GraphCodec.decodeGraph(fromJson);
        AgentGraphData b = GraphCodec.decodeGraph(toJson);

        Map<String, GraphNode> am = index(a);
        Map<String, GraphNode> bm = index(b);

        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        for (var e : bm.entrySet()) {
            GraphNode old = am.get(e.getKey());
            if (old == null) {
                added.add(label(e.getValue()));
            } else if (semanticChanged(old, e.getValue())) {
                changed.add(label(e.getValue()));
            }
        }
        for (var e : am.entrySet()) {
            if (!bm.containsKey(e.getKey())) {
                removed.add(label(e.getValue()));
            }
        }

        Map<String, String> em = edgeIndex(a);
        Map<String, String> bm2 = edgeIndex(b);
        List<String> edgesAdded = new ArrayList<>();
        List<String> edgesRemoved = new ArrayList<>();
        for (var e : bm2.entrySet()) {
            if (!em.containsKey(e.getKey())) {
                edgesAdded.add(e.getValue());
            }
        }
        for (var e : em.entrySet()) {
            if (!bm2.containsKey(e.getKey())) {
                edgesRemoved.add(e.getValue());
            }
        }
        return new GraphDiffData(fromVersion, toVersion, added, removed, changed, edgesAdded, edgesRemoved);
    }

    private static Map<String, GraphNode> index(AgentGraphData g) {
        Map<String, GraphNode> m = new LinkedHashMap<>();
        if (g != null && g.getNodes() != null) {
            for (GraphNode n : g.getNodes()) {
                m.put(n.getId(), n);
            }
        }
        return m;
    }

    private static Map<String, String> edgeIndex(AgentGraphData g) {
        Map<String, String> m = new LinkedHashMap<>();
        if (g != null && g.getEdges() != null) {
            for (GraphEdge e : g.getEdges()) {
                m.put(e.getFrom() + "->" + e.getTo(),
                        (e.getRoute() == null || e.getRoute().isBlank())
                                ? e.getFrom() + " → " + e.getTo()
                                : e.getFrom() + " →[" + e.getRoute() + "] " + e.getTo());
            }
        }
        return m;
    }

    private static boolean semanticChanged(GraphNode a, GraphNode b) {
        return !java.util.Objects.equals(a.getTitle(), b.getTitle())
                || !java.util.Objects.equals(a.getPrompt(), b.getPrompt())
                || !java.util.Objects.equals(a.getTool(), b.getTool())
                || !java.util.Objects.equals(a.getArgs(), b.getArgs())
                || !java.util.Objects.equals(a.getApprovalPrompt(), b.getApprovalPrompt())
                || !java.util.Objects.equals(a.getTools(), b.getTools())
                || !java.util.Objects.equals(a.getType(), b.getType());
    }

    private static String label(GraphNode n) {
        String type = n.getType() == null ? "?" : n.getType();
        String title = n.getTitle() == null || n.getTitle().isBlank() ? "" : " " + n.getTitle();
        return n.getId() + "(" + type + ")" + title;
    }

}
