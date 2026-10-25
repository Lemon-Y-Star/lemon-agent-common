package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.model.runtime.AgentGraphData;
import com.lemon.lemonagent.model.runtime.GraphStateData;

/**
 * 执行图的编解码（v3.2.0）：{@code agent_graph.graph_json} 与 {@code agent_run.graph_state_json}。
 *
 * <p>与 {@link PlanCodec} 同思路：存"带进度的执行事实"，因此节点审批挂起、进程重启后都能从库恢复。</p>
 */
final class GraphCodec {

    private static final tools.jackson.databind.ObjectMapper MAPPER =
            new tools.jackson.databind.ObjectMapper();

    private GraphCodec() {
    }

    /**
     * 解码图定义。
     *
     * @param json 入参：图 JSON（可空）
     * @return 出参：图；空/脏数据返回 null
     */
    static AgentGraphData decodeGraph(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, AgentGraphData.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 编码图定义。
     *
     * @param graph 入参：图
     * @return 出参：落库字符串
     */
    static String encodeGraph(AgentGraphData graph) {
        try {
            return MAPPER.writeValueAsString(graph);
        } catch (Exception e) {
            throw new IllegalArgumentException("执行图序列化失败", e);
        }
    }

    /**
     * 解码执行进度。
     *
     * @param json 入参：进度 JSON（可空）
     * @return 出参：进度；空/脏数据返回新建的空进度
     */
    static GraphStateData decodeState(String json) {
        if (json == null || json.isBlank()) {
            return new GraphStateData();
        }
        try {
            GraphStateData state = MAPPER.readValue(json, GraphStateData.class);
            return state == null ? new GraphStateData() : state;
        } catch (Exception e) {
            return new GraphStateData();
        }
    }

    /**
     * 编码执行进度。
     *
     * @param state 入参：进度
     * @return 出参：落库字符串
     */
    static String encodeState(GraphStateData state) {
        try {
            return MAPPER.writeValueAsString(state);
        } catch (Exception e) {
            throw new IllegalArgumentException("执行进度序列化失败", e);
        }
    }

}
