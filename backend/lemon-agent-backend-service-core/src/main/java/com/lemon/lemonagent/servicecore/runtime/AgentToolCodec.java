package com.lemon.lemonagent.servicecore.runtime;

import java.util.List;

/**
 * Agent 工具白名单的编解码（v3.0）：落库列 {@code agent_config.tools_json}。
 *
 * <p><b>NULL 语义</b>：null / 空串 = 该 Agent 使用全部已注册工具（向后兼容 v2.5 及更早数据）；
 * 显式数组 = 白名单，空数组 = 不装配任何工具。编解码集中在装配域，服务层与执行器共用一份口径。</p>
 */
final class AgentToolCodec {

    private static final tools.jackson.databind.ObjectMapper MAPPER =
            new tools.jackson.databind.ObjectMapper();

    private AgentToolCodec() {
    }

    /**
     * 编码：list → JSON 数组字符串。
     *
     * @param tools 入参：工具名（null 表示「全部」，直接返回 null）
     * @return 出参：落库字符串
     */
    static String encode(List<String> tools) {
        if (tools == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(tools);
        } catch (Exception e) {
            throw new IllegalArgumentException("工具清单序列化失败", e);
        }
    }

    /**
     * 解码：JSON 数组字符串 → list。
     *
     * @param json 入参：库里的值（可空）
     * @return 出参：工具名清单；null/空串/脏数据均返回 null（= 全部工具，宁可放开也不误伤老数据）
     */
    static List<String> decode(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            String[] names = MAPPER.readValue(json, String[].class);
            return List.of(names);
        } catch (Exception e) {
            return null;
        }
    }

}
