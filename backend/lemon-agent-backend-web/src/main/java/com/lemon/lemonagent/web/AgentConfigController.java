package com.lemon.lemonagent.web;

import com.lemon.lemonagent.model.runtime.AgentConfigData;
import com.lemon.lemonagent.model.runtime.AgentConfigsData;
import com.lemon.lemonagent.model.runtime.CreateAgentReq;
import com.lemon.lemonagent.model.runtime.CreateAgentResp;
import com.lemon.lemonagent.model.runtime.DeleteAgentReq;
import com.lemon.lemonagent.model.runtime.DeleteAgentResp;
import com.lemon.lemonagent.model.runtime.UpdateAgentReq;
import com.lemon.lemonagent.model.runtime.UpdateAgentResp;
import com.lemon.lemonagent.servicecore.runtime.AgentConfigService;
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

/**
 * 我的 Agent 接口（PRD FR-6 最小充分版）：平台公共只读 + 个人自建增删改。
 */
@RestController
@RequestMapping("/api/v1/workspace/agents")
public class AgentConfigController {

    /** 框架操作入口。 */
    @Resource
    private OptUtil optUtil;

    /** Agent 配置领域服务。 */
    @Resource
    private AgentConfigService agentConfigService;

    /**
     * Agent 列表（平台公共在前，个人自建在后）。
     *
     * @param userId 归属用户
     * @return 列表数据
     */
    @GetMapping
    public BaseResult<AgentConfigsData> list(@RequestAttribute("lemon.userId") Long userId) {
        return BaseResult.success(agentConfigService.list(userId));
    }

    /**
     * 创建个人 Agent。
     *
     * @param request Agent 配置
     * @param userId  归属用户
     * @param source  调用来源
     * @return 创建后的 Agent
     */
    @PostMapping
    public CreateAgentResp create(@RequestBody CreateAgentReq request,
                                  @RequestAttribute("lemon.userId") Long userId,
                                  @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new CreateAgentResp(), req -> {
            AssertUtils.notBlank(req.getName(), "名称不能为空");
            AssertUtils.notBlank(req.getInstruction(), "系统提示词不能为空");
        });
    }

    /**
     * 更新个人 Agent。
     *
     * @param agentId Agent ID
     * @param request Agent 配置
     * @param userId  归属用户
     * @param source  调用来源
     * @return 更新后的 Agent
     */
    @PutMapping("/{agentId}")
    public UpdateAgentResp update(@PathVariable Long agentId,
                                  @RequestBody UpdateAgentReq request,
                                  @RequestAttribute("lemon.userId") Long userId,
                                  @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        request.setAgentId(agentId);
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new UpdateAgentResp(), req -> {
            AssertUtils.notNull(req.getAgentId(), "Agent ID 不能为空");
            AssertUtils.notBlank(req.getName(), "名称不能为空");
            AssertUtils.notBlank(req.getInstruction(), "系统提示词不能为空");
        });
    }

    /**
     * 删除个人 Agent。
     *
     * @param agentId Agent ID
     * @param userId  归属用户
     * @param source  调用来源
     * @return 空数据成功响应
     */
    @DeleteMapping("/{agentId}")
    public DeleteAgentResp delete(@PathVariable Long agentId,
                                  @RequestAttribute("lemon.userId") Long userId,
                                  @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        DeleteAgentReq request = new DeleteAgentReq();
        request.setAgentId(agentId);
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new DeleteAgentResp(), req ->
                AssertUtils.notNull(req.getAgentId(), "Agent ID 不能为空"));
    }

}
