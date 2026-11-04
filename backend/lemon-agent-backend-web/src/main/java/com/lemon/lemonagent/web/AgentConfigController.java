package com.lemon.lemonagent.web;

import com.lemon.lemonagent.model.runtime.AgentConfigData;
import com.lemon.lemonagent.model.runtime.AgentExportData;
import com.lemon.lemonagent.model.runtime.AgentConfigsData;
import com.lemon.lemonagent.model.runtime.AgentLogsData;
import com.lemon.lemonagent.model.runtime.AgentMetricsData;
import com.lemon.lemonagent.model.runtime.CreateAgentReq;
import com.lemon.lemonagent.model.runtime.CreateAgentResp;
import com.lemon.lemonagent.model.runtime.DeleteAgentReq;
import com.lemon.lemonagent.model.runtime.DeleteAgentResp;
import com.lemon.lemonagent.model.runtime.UpdateAgentReq;
import com.lemon.lemonagent.model.runtime.UpdateAgentResp;
import com.lemon.lemonagent.servicecore.runtime.agent.AgentConfigService;
import com.lemon.lemonagent.servicecore.runtime.agent.AgentOpsService;
import com.lemon.lemonagent.servicecore.runtime.agent.PromptOptimizeService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
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

    /** 提示词 AI 优化器（v4.5）。 */
    @Resource
    private PromptOptimizeService promptOptimizeService;

    /** Agent 运营数据（v4.5.2 日志/监控）。 */
    @Resource
    private AgentOpsService agentOpsService;

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
     * 克隆 Agent（v4.6.0）：全字段复制，绑定原样保留（同用户合法）。
     *
     * @param agentId 源 Agent
     * @param body    入参：{"name": "新名称（可空 = 原名副本）"}
     * @param userId  归属用户
     * @return 新 Agent
     */
    @PostMapping("/{agentId}/clone")
    public BaseResult<AgentConfigData> clone(@PathVariable Long agentId,
                                             @RequestBody(required = false) Map<String, String> body,
                                             @RequestAttribute("lemon.userId") Long userId) {
        String newName = body == null ? null : body.get("name");
        return BaseResult.success(agentConfigService.cloneAgent(userId, agentId, newName));
    }

    /**
     * 导出 Agent 配置（v4.6.0）：返回 JSON 数据（前端落盘下载）。
     *
     * @param agentId Agent
     * @param userId  归属用户
     * @return 导出数据
     */
    @GetMapping("/{agentId}/export")
    public BaseResult<AgentExportData> export(@PathVariable Long agentId,
                                              @RequestAttribute("lemon.userId") Long userId) {
        return BaseResult.success(agentConfigService.exportAgent(userId, agentId));
    }

    /**
     * 导入 Agent 配置（v4.6.0）：绑定类 ID（模型连接/工作流/知识库）不迁移，导入后手动重绑。
     *
     * @param data   导出数据
     * @param userId 归属用户
     * @return 新 Agent
     */
    @PostMapping("/import")
    public BaseResult<AgentConfigData> importAgent(@RequestBody AgentExportData data,
                                                   @RequestAttribute("lemon.userId") Long userId) {
        return BaseResult.success(agentConfigService.importAgent(userId, data));
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
     * 提示词 AI 优化（v4.5，Dify Prompt Generator 同款）：模型改写 instruction。
     *
     * @param body   入参：{"instruction": "原始提示词"}
     * @param userId 归属用户
     * @return 出参：{"optimized": "优化后提示词"}
     */
    @PostMapping("/optimize-prompt")
    public BaseResult<Map<String, String>> optimizePrompt(@RequestBody Map<String, String> body,
                                                          @RequestAttribute("lemon.userId") Long userId) {
        String optimized = promptOptimizeService.optimize(userId,
                body == null ? null : body.get("instruction"));
        return BaseResult.success(Map.of("optimized", optimized));
    }

    /**
     * Agent 会话日志（v4.5.2，Dify「日志」页同款）：按会话聚合该 Agent 的对话。
     *
     * @param agentId Agent ID
     * @param userId  归属用户
     * @param days    时间窗（天，默认 7）
     * @return 会话日志列表
     */
    @GetMapping("/{agentId}/logs")
    public BaseResult<AgentLogsData> logs(@PathVariable Long agentId,
                                          @RequestAttribute("lemon.userId") Long userId,
                                          @RequestParam(name = "days", defaultValue = "7") int days) {
        return BaseResult.success(agentOpsService.logs(userId, agentId, days));
    }

    /**
     * Agent 监控指标（v4.5.2，Dify「监控」页同款）：按天聚合。
     *
     * @param agentId Agent ID
     * @param userId  归属用户
     * @param days    时间窗（天，默认 7）
     * @return 日指标 + 汇总
     */
    @GetMapping("/{agentId}/metrics")
    public BaseResult<AgentMetricsData> metrics(@PathVariable Long agentId,
                                                @RequestAttribute("lemon.userId") Long userId,
                                                @RequestParam(name = "days", defaultValue = "7") int days) {
        return BaseResult.success(agentOpsService.metrics(userId, agentId, days));
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
