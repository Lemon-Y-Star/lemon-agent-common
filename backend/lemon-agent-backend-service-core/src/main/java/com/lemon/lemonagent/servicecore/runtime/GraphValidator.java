package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.model.runtime.AgentGraphData;
import com.lemon.lemonagent.model.runtime.GraphEdge;
import com.lemon.lemonagent.model.runtime.GraphNode;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 执行图校验与拓扑排序（v3.2.0，PRD FR-7.1 §⑦）。
 *
 * <p><b>校验在保存与提交两处都跑</b>：画布能存下的图，运行时一定能调度；
 * 反过来，运行时不接受"半张图"——创建 run 时若图非法直接 400，不分配事件序号。</p>
 *
 * <p>规则：节点 1..20 个、ID 唯一、类型白名单（START/LLM/TOOL/APPROVAL/END）、
 * 有且仅有一个 START、至少一个 END、边两端存在、START 无入边、非 START 必须有入边、
 * TOOL 节点的工具必须已注册、LLM 节点必须有指令、无环（Kahn）。</p>
 */
final class GraphValidator {

    /** 节点类型白名单。 */
    static final Set<String> TYPES = Set.of("START", "LLM", "TOOL", "APPROVAL", "END");

    /** 节点上限：再多就不是"图"而是运维脚本，且串行执行成本线性增长。 */
    private static final int MAX_NODES = 20;

    private GraphValidator() {
    }

    /**
     * 校验图（不通过抛 BusinessException）。
     *
     * @param graph    入参：图
     * @param registry 入参：工具注册表（校验 TOOL 节点）
     */
    static void validate(AgentGraphData graph, ToolRegistry registry) {
        if (graph == null || graph.getNodes() == null || graph.getNodes().isEmpty()) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "执行图不能为空");
        }
        List<GraphNode> nodes = graph.getNodes();
        if (nodes.size() > MAX_NODES) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "节点不能超过 " + MAX_NODES + " 个");
        }
        Map<String, GraphNode> byId = new HashMap<>();
        int starts = 0;
        int ends = 0;
        for (GraphNode node : nodes) {
            if (node.getId() == null || node.getId().isBlank()) {
                throw new BusinessException(ResultCodes.VALIDATION_ERROR, "节点 ID 不能为空");
            }
            if (byId.put(node.getId(), node) != null) {
                throw new BusinessException(ResultCodes.VALIDATION_ERROR, "节点 ID 重复：" + node.getId());
            }
            String type = node.getType() == null ? "" : node.getType().toUpperCase();
            if (!TYPES.contains(type)) {
                throw new BusinessException(ResultCodes.VALIDATION_ERROR,
                        "未知节点类型：" + node.getType() + "（支持 START/LLM/TOOL/APPROVAL/END）");
            }
            node.setType(type);
            if ("START".equals(type)) {
                starts++;
            } else if ("END".equals(type)) {
                ends++;
            } else if ("TOOL".equals(type)) {
                if (node.getTool() == null || node.getTool().isBlank()) {
                    throw new BusinessException(ResultCodes.VALIDATION_ERROR,
                            "TOOL 节点 " + node.getId() + " 必须指定工具");
                }
                if (!registry.names().contains(node.getTool())) {
                    throw new BusinessException(ResultCodes.VALIDATION_ERROR,
                            "TOOL 节点 " + node.getId() + " 引用了未注册的工具：" + node.getTool());
                }
            } else if ("LLM".equals(type)
                    && (node.getPrompt() == null || node.getPrompt().isBlank())
                    && (node.getTitle() == null || node.getTitle().isBlank())) {
                throw new BusinessException(ResultCodes.VALIDATION_ERROR,
                        "LLM 节点 " + node.getId() + " 需要指令（prompt）");
            }
        }
        if (starts != 1) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "执行图必须有且只有一个 START 节点");
        }
        if (ends < 1) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "执行图至少需要一个 END 节点");
        }

        List<GraphEdge> edges = graph.getEdges() == null ? List.of() : graph.getEdges();
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> next = new HashMap<>();
        for (GraphNode node : nodes) {
            inDegree.put(node.getId(), 0);
            next.put(node.getId(), new ArrayList<>());
        }
        for (GraphEdge edge : edges) {
            if (edge.getFrom() == null || edge.getTo() == null
                    || !byId.containsKey(edge.getFrom()) || !byId.containsKey(edge.getTo())) {
                throw new BusinessException(ResultCodes.VALIDATION_ERROR,
                        "边引用了不存在的节点：" + edge.getFrom() + " → " + edge.getTo());
            }
            // 条件路由只认预注册谓词（v3.3）：校验不过直接拒绝，运行期不会再解释表达式
            String normalizedRoute = RoutePredicates.validate(edge.getRoute());
            edge.setRoute(RoutePredicates.ALWAYS.equals(normalizedRoute) ? null : normalizedRoute);
            if ("START".equals(byId.get(edge.getFrom()).getType())
                    && edge.getRoute() != null) {
                throw new BusinessException(ResultCodes.VALIDATION_ERROR,
                        "START 没有产出，出边不支持条件路由");
            }
            next.get(edge.getFrom()).add(edge.getTo());
            inDegree.merge(edge.getTo(), 1, Integer::sum);
        }
        for (GraphNode node : nodes) {
            boolean isStart = "START".equals(node.getType());
            if (isStart && inDegree.get(node.getId()) > 0) {
                throw new BusinessException(ResultCodes.VALIDATION_ERROR, "START 节点不能有入边");
            }
            if (!isStart && inDegree.get(node.getId()) == 0) {
                throw new BusinessException(ResultCodes.VALIDATION_ERROR,
                        "节点 " + node.getId() + " 没有入边（除 START 外都必须可达）");
            }
        }
        // Kahn：拓扑序数量不足 = 有环
        if (topoOrder(graph).size() != nodes.size()) {
            throw new BusinessException("CYCLIC_GRAPH", "执行图存在环，无法调度");
        }
    }

    /**
     * Kahn 拓扑排序（同层按图内声明顺序，保证结果稳定可复现）。
     *
     * @param graph 入参：已校验的图
     * @return 出参：拓扑序节点列表（有环时不足全量）
     */
    static List<GraphNode> topoOrder(AgentGraphData graph) {
        List<GraphNode> nodes = graph.getNodes() == null ? List.of() : graph.getNodes();
        Map<String, GraphNode> byId = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> next = new HashMap<>();
        for (GraphNode node : nodes) {
            byId.put(node.getId(), node);
            inDegree.put(node.getId(), 0);
            next.put(node.getId(), new ArrayList<>());
        }
        for (GraphEdge edge : graph.getEdges() == null ? List.<GraphEdge>of() : graph.getEdges()) {
            if (!byId.containsKey(edge.getFrom()) || !byId.containsKey(edge.getTo())) {
                continue;
            }
            next.get(edge.getFrom()).add(edge.getTo());
            inDegree.merge(edge.getTo(), 1, Integer::sum);
        }
        List<GraphNode> ordered = new ArrayList<>();
        Set<String> ready = new LinkedHashSet<>();
        for (GraphNode node : nodes) {
            if (inDegree.get(node.getId()) == 0) {
                ready.add(node.getId());
            }
        }
        while (!ready.isEmpty()) {
            String id = ready.iterator().next();
            ready.remove(id);
            ordered.add(byId.get(id));
            for (String to : next.get(id)) {
                int left = inDegree.merge(to, -1, Integer::sum);
                if (left == 0) {
                    ready.add(to);
                }
            }
        }
        return ordered;
    }

}
