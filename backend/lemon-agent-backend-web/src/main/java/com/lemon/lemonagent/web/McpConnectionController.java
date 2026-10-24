package com.lemon.lemonagent.web;

import com.lemon.lemonagent.model.mcpconn.CreateMcpConnectionReq;
import com.lemon.lemonagent.model.mcpconn.CreateMcpConnectionResp;
import com.lemon.lemonagent.model.mcpconn.DeleteMcpConnectionReq;
import com.lemon.lemonagent.model.mcpconn.DeleteMcpConnectionResp;
import com.lemon.lemonagent.model.mcpconn.McpConnectionData;
import com.lemon.lemonagent.model.mcpconn.UpdateMcpConnectionReq;
import com.lemon.lemonagent.model.mcpconn.UpdateMcpConnectionResp;
import com.lemon.lemonagent.servicecore.conn.McpConnectionService;
import com.lemon.architecture.model.BaseResult;
import com.lemon.architecture.service.util.opt.OptUtil;
import com.lemon.architecture.util.validation.AssertUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * MCP 连接接口（PRD FR-12）：GET 返回平台公共 + 用户自建，命令只作用于自建连接。
 */
@RestController
@RequestMapping("/api/v1/workspace/mcps")
public class McpConnectionController {

    /** 框架操作入口。 */
    @Resource
    private OptUtil optUtil;

    /** MCP 连接领域服务。 */
    @Resource
    private McpConnectionService mcpConnectionService;

    /**
     * 连接列表（平台公共在前，自建在后；editable 标记驱动前端禁用编辑入口）。
     *
     * @param userId 归属用户
     * @return 全部可见连接
     */
    @GetMapping
    public BaseResult<List<McpConnectionData>> list(@RequestAttribute("lemon.userId") Long userId) {
        return BaseResult.success(mcpConnectionService.list(userId));
    }

    /**
     * 新建自建连接。
     *
     * @param request 连接配置
     * @param userId  归属用户
     * @param source  调用来源
     * @return 创建后的连接
     */
    @PostMapping
    public CreateMcpConnectionResp create(@RequestBody CreateMcpConnectionReq request,
                                          @RequestAttribute("lemon.userId") Long userId,
                                          @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new CreateMcpConnectionResp(), req -> {
            AssertUtils.notBlank(req.getName(), "连接名称不能为空");
            AssertUtils.notBlank(req.getTransportType(), "传输类型不能为空");
        });
    }

    /**
     * 更新自建连接（secret 为空表示保留原密钥）。
     *
     * @param id      连接 ID
     * @param request 连接配置
     * @param userId  归属用户
     * @param source  调用来源
     * @return 更新后的连接
     */
    @PutMapping("/{id}")
    public UpdateMcpConnectionResp update(@PathVariable Long id,
                                          @RequestBody UpdateMcpConnectionReq request,
                                          @RequestAttribute("lemon.userId") Long userId,
                                          @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        request.setId(id);
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new UpdateMcpConnectionResp(), req -> {
            AssertUtils.notNull(req.getId(), "连接 ID 不能为空");
            AssertUtils.notBlank(req.getName(), "连接名称不能为空");
        });
    }

    /**
     * 删除自建连接。
     *
     * @param id      连接 ID
     * @param userId  归属用户
     * @param source  调用来源
     * @return 空数据成功响应
     */
    @DeleteMapping("/{id}")
    public DeleteMcpConnectionResp delete(@PathVariable Long id,
                                          @RequestAttribute("lemon.userId") Long userId,
                                          @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        DeleteMcpConnectionReq request = new DeleteMcpConnectionReq();
        request.setId(id);
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new DeleteMcpConnectionResp(), req ->
                AssertUtils.notNull(req.getId(), "连接 ID 不能为空"));
    }

}
