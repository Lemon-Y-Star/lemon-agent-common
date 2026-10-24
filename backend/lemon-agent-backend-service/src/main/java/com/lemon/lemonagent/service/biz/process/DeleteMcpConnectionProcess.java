package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.mcpconn.DeleteMcpConnectionReq;
import com.lemon.lemonagent.model.mcpconn.DeleteMcpConnectionResp;
import com.lemon.lemonagent.servicecore.conn.McpConnectionService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/**
 * 删除 MCP 连接处理器：路由三元组 {@code ANY / ANY / MCP_CONN_DELETE}。
 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "MCP_CONN_DELETE")
public class DeleteMcpConnectionProcess extends Handler<DeleteMcpConnectionReq, DeleteMcpConnectionResp> {

    @Resource
    private McpConnectionService mcpConnectionService;

    /**
     * 删除自建连接。
     *
     * @param request  已通过校验的请求（userId 由服务端填充）
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(DeleteMcpConnectionReq request, DeleteMcpConnectionResp response) {
        mcpConnectionService.delete(request.getUserId(), request.getId());
        log.info("MCP 连接已删除 userId:{} id:{}", request.getUserId(), request.getId());
    }

}
