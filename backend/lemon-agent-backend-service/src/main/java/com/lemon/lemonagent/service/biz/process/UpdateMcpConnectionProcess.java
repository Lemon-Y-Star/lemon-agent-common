package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.mcpconn.McpConnectionData;
import com.lemon.lemonagent.model.mcpconn.UpdateMcpConnectionReq;
import com.lemon.lemonagent.model.mcpconn.UpdateMcpConnectionResp;
import com.lemon.lemonagent.servicecore.conn.McpConnectionService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/**
 * 更新 MCP 连接处理器：路由三元组 {@code ANY / ANY / MCP_CONN_UPDATE}。
 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "MCP_CONN_UPDATE")
public class UpdateMcpConnectionProcess extends Handler<UpdateMcpConnectionReq, UpdateMcpConnectionResp> {

    @Resource
    private McpConnectionService mcpConnectionService;

    /**
     * 更新自建连接（secret 为空表示保留原密钥）。
     *
     * @param request  已通过校验的请求（userId 由服务端填充）
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(UpdateMcpConnectionReq request, UpdateMcpConnectionResp response) {
        McpConnectionData data = mcpConnectionService.update(request.getUserId(), request.getId(),
                request.getName(), request.getTransportType(), request.getUrl(),
                request.getCommand(), request.getSecret(), request.getEnabled());
        response.setData(data);
        log.info("MCP 连接已更新 userId:{} id:{}", request.getUserId(), request.getId());
    }

}
