package com.lemon.lemonagent.web;

import com.lemon.lemonagent.model.runtime.AgentGraphData;
import com.lemon.lemonagent.model.runtime.SaveAgentGraphReq;
import com.lemon.lemonagent.model.runtime.SaveAgentGraphResp;
import com.lemon.lemonagent.servicecore.runtime.AgentGraphService;
import com.lemon.architecture.model.BaseResult;
import com.lemon.architecture.service.util.opt.OptUtil;
import com.lemon.architecture.util.validation.AssertUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 执行图接口（v3.2.0）：读取当前版本 + 保存新版本。
 *
 * <p>与 {@link AgentConfigController} 同前缀，靠子路径区分（{@code /{agentId}/graph}）。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/agents")
public class AgentGraphController {

    /** 框架操作入口。 */
    @Resource
    private OptUtil optUtil;

    /** 执行图领域服务。 */
    @Resource
    private AgentGraphService agentGraphService;

    /**
     * 读取当前执行图（没有图时 version=0、nodes 为空）。
     *
     * @param agentId 路径参数：Agent ID
     * @param userId  当前用户
     * @return 图
     */
    @GetMapping("/{agentId}/graph")
    public BaseResult<AgentGraphData> get(@PathVariable Long agentId,
                                          @RequestAttribute("lemon.userId") Long userId) {
        return BaseResult.success(agentGraphService.get(userId, agentId));
    }

    /**
     * 保存执行图（整体覆盖，version+1）。
     *
     * @param agentId 路径参数：Agent ID
     * @param request 图定义
     * @param userId  当前用户
     * @param source  调用来源
     * @return 带新版本的图
     */
    @PutMapping("/{agentId}/graph")
    public SaveAgentGraphResp save(@PathVariable Long agentId,
                                   @RequestBody SaveAgentGraphReq request,
                                   @RequestAttribute("lemon.userId") Long userId,
                                   @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        request.setAgentId(agentId);
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new SaveAgentGraphResp(), req -> {
            AssertUtils.notNull(req.getAgentId(), "Agent ID 不能为空");
            AssertUtils.isTrue(req.getNodes() != null && !req.getNodes().isEmpty(), "执行图不能为空");
        });
    }

}
