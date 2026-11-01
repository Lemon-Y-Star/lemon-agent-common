package com.lemon.lemonagent.model.mcp;

import java.util.List;
import java.util.Map;

/**
 * MCP 工具端口（PRD FR-12 桥接：把 mcp_connection 的远端工具接入本平台工具体系）。
 *
 * <p>防腐层边界：MCP JSON-RPC 协议细节（initialize 握手、session 管理、
 * stdio 子进程生命周期）只允许出现在 integration 实现里；service-core 的
 * ToolRegistry 只面向本接口，把远端工具当作普通 ToolHandler 装配。</p>
 */
public interface McpToolPort {

    /**
     * 拉取连接提供的工具清单（tools/list）。
     *
     * @param conn 入参：连接描述（含解密后的密钥）
     * @return 出参：工具定义列表
     * @throws Exception 网络失败、握手失败或协议错误（由调用方决定如何标注连接不可用）
     */
    List<ToolDef> listTools(ConnSpec conn) throws Exception;

    /**
     * 调用远端工具（tools/call）。
     *
     * @param conn    入参：连接描述（含解密后的密钥）
     * @param toolName 入参：远端工具名
     * @param args    入参：工具入参（JSON 对象）
     * @return 出参：工具产出（text 内容合并为 result；结构化产出原样透出；isError 折算为 error 键）
     * @throws Exception 网络失败、握手失败或协议错误
     */
    Map<String, Object> callTool(ConnSpec conn, String toolName, Map<String, Object> args) throws Exception;

    /** 连接描述（实体 → 端口的投影，避免 integration 依赖 dal）。 */
    record ConnSpec(long id, String name, String transportType, String url, String command, String secret) {
    }

    /** 远端工具定义（tools/list 条目投影）。 */
    record ToolDef(String name, String description, String inputSchemaJson) {
    }

}
