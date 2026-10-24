package com.lemon.lemonagent.model.mcpconn;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/**
 * 新建用户自建 MCP 连接（PRD FR-12：「用户自建走 POST /api/v1/workspace/mcps」）。
 *
 * <p>参数形态随 transportType 变化：SSE / STREAMABLE 必填 {@code url}，STDIO 必填 {@code command}。</p>
 */
@Getter
@Setter
public class CreateMcpConnectionReq extends BaseReq {

    /** 归属用户（服务端由认证拦截器解析回填，客户端不可传） */
    private Long userId;

    /** 连接名称 */
    private String name;

    /** 传输类型：SSE / STDIO / STREAMABLE */
    private String transportType;

    /** HTTP 端点（SSE / STREAMABLE 必填） */
    private String url;

    /** 本地子进程命令（STDIO 必填） */
    private String command;

    /** 密钥（可选；明文入参，AES-GCM 加密落库） */
    private String secret;

    /**
     * 填充路由三元组。
     *
     * @param source 调用来源（X-Source 请求头）
     */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.MCP_CONN_CREATE);
    }

}
