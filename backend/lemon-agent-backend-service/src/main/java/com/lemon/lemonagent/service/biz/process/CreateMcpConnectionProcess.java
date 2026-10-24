package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.mcpconn.CreateMcpConnectionReq;
import com.lemon.lemonagent.model.mcpconn.CreateMcpConnectionResp;
import com.lemon.lemonagent.model.mcpconn.McpConnectionData;
import com.lemon.lemonagent.servicecore.conn.McpConnectionService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/**
 * 新建 MCP 连接处理器：路由三元组 {@code ANY / ANY / MCP_CONN_CREATE}。
 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "MCP_CONN_CREATE")
public class CreateMcpConnectionProcess extends Handler<CreateMcpConnectionReq, CreateMcpConnectionResp> {

    @Resource
    private McpConnectionService mcpConnectionService;

    /**
     * 新建自建连接。
     *
     * @param request  已通过校验的请求（userId 由服务端填充）
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(CreateMcpConnectionReq request, CreateMcpConnectionResp response) {
        McpConnectionData data = mcpConnectionService.create(request.getUserId(),
                request.getName(), request.getTransportType(), request.getUrl(),
                request.getCommand(), request.getSecret());
        response.setData(data);
        log.info("MCP 连接已创建 userId:{} name:{}", request.getUserId(), request.getName());
    }

}
