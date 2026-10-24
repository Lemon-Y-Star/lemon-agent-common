package com.lemon.lemonagent.model.enums;

/**
 * MCP 连接的传输类型（PRD FR-12：平台公共 11 个连接覆盖三种）。
 *
 * <p>类型决定连接参数的形态：SSE / STREAMABLE 用 {@code url}（HTTP 端点），
 * STDIO 用 {@code command}（本地子进程命令行），三者互斥。</p>
 */
public enum TransportType {

    /** HTTP SSE 端点 */
    SSE,

    /** 本地子进程（stdin/stdout） */
    STDIO,

    /** Streamable HTTP 单端点协议 */
    STREAMABLE;

    /**
     * 该类型是否要求 HTTP 端点。
     *
     * @return 出参：SSE / STREAMABLE 返回 true
     */
    public boolean needsUrl() {
        return this == SSE || this == STREAMABLE;
    }

    /**
     * 该类型是否要求本地命令。
     *
     * @return 出参：STDIO 返回 true
     */
    public boolean needsCommand() {
        return this == STDIO;
    }

}
