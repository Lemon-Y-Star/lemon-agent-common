package com.lemon.lemonagent.web;

import com.lemon.lemonagent.dal.entity.ApiKeyEntity;
import com.lemon.lemonagent.dal.entity.AgentRunEntity;
import com.lemon.lemonagent.dal.repository.AgentRunRepository;
import com.lemon.lemonagent.model.runtime.RunStatus;
import com.lemon.lemonagent.servicecore.runtime.ApiKeyService;
import com.lemon.lemonagent.servicecore.runtime.AgentRuntimeService;
import com.lemon.architecture.model.BaseResult;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 开放端点（v3.8，PRD FR-24 最小版）：API 密钥鉴权，供外部系统调用 Agent。
 *
 * <p>鉴权不走 lemon 认证拦截器（WebConfig 已放行 {@code /api/v1/open/**}），
 * 用 {@code Authorization: Bearer sk-…} 或 {@code X-Api-Key}——哈希查库 + 启用状态，
 * 命中后以密钥归属用户的身份发起运行（平台公共 Agent 与本人 Agent 可用，越权口径不变）。</p>
 *
 * <p>invoke 同步等待：提交后轮询运行状态直到终态或超时（默认 60s，上限 120s）；
 * 超时也返回 runId，可用 GET /open/runs/{runId} 继续查。</p>
 */
@RestController
@RequestMapping("/api/v1/open")
public class OpenInvokeController {

    /** 密钥领域服务。 */
    @Resource
    private ApiKeyService apiKeyService;

    /** 运行时领域服务（createRun 的越权/互斥/幂等口径全部复用）。 */
    @Resource
    private AgentRuntimeService agentRuntimeService;

    /** 运行仓储（轮询状态用）。 */
    @Resource
    private AgentRunRepository runRepository;

    /** 调用请求体。 */
    public record OpenInvokeReq(String input, String strategy, Integer waitSeconds, Boolean thinking) {
    }

    /**
     * 调用 Agent（同步等待产物）。
     *
     * @param agentId      路径参数：Agent ID
     * @param request      输入与可选策略
     * @param authorization Bearer 密钥（可空）
     * @param apiKeyHeader X-Api-Key（可空）
     * @return 运行结果
     */
    @PostMapping("/agents/{agentId}/invoke")
    public BaseResult<Map<String, Object>> invoke(@PathVariable Long agentId,
                                                  @RequestBody OpenInvokeReq request,
                                                  @RequestHeader(value = "Authorization", required = false) String authorization,
                                                  @RequestHeader(value = "X-Api-Key", required = false) String apiKeyHeader) {
        ApiKeyEntity key = apiKeyService.authenticate(extractKey(authorization, apiKeyHeader));
        String input = request == null ? null : request.input();
        if (input == null || input.isBlank()) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "input 不能为空");
        }
        String strategy = request.strategy() == null || request.strategy().isBlank()
                ? "auto" : request.strategy();
        int waitSeconds = request.waitSeconds() == null ? 60 : Math.min(Math.max(request.waitSeconds(), 1), 120);

        var run = agentRuntimeService.createRun(key.getUserId(), null, agentId, input, strategy,
                "open_" + UUID.randomUUID(), request.thinking());
        AgentRunEntity entity = waitTerminal(key.getUserId(), run.getRunId(), waitSeconds);
        return BaseResult.success(toResult(entity));
    }

    /**
     * 查询调用结果（invoke 超时后的续查入口）。
     *
     * @param runId         路径参数：运行 ID
     * @param authorization Bearer 密钥（可空）
     * @param apiKeyHeader  X-Api-Key（可空）
     * @return 运行结果
     */
    @GetMapping("/runs/{runId}")
    public BaseResult<Map<String, Object>> run(@PathVariable Long runId,
                                               @RequestHeader(value = "Authorization", required = false) String authorization,
                                               @RequestHeader(value = "X-Api-Key", required = false) String apiKeyHeader) {
        ApiKeyEntity key = apiKeyService.authenticate(extractKey(authorization, apiKeyHeader));
        AgentRunEntity entity = runRepository.findByIdAndUser(runId, key.getUserId());
        if (entity == null) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "运行不存在");
        }
        return BaseResult.success(toResult(entity));
    }

    private AgentRunEntity waitTerminal(Long userId, Long runId, int waitSeconds) {
        long deadline = System.currentTimeMillis() + waitSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            AgentRunEntity entity = runRepository.findByIdAndUser(runId, userId);
            if (entity != null && RunStatus.valueOf(entity.getStatus()).terminal()) {
                return entity;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return runRepository.findByIdAndUser(runId, userId);
    }

    private Map<String, Object> toResult(AgentRunEntity entity) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("runId", entity.getId());
        result.put("sessionId", entity.getSessionId());
        result.put("status", entity.getStatus());
        result.put("traceId", entity.getTraceId());
        result.put("output", entity.getOutputText());
        result.put("errorCode", entity.getErrorCode());
        if (entity.getInputTokens() != null || entity.getOutputTokens() != null) {
            result.put("usage", Map.of(
                    "inputTokens", entity.getInputTokens() == null ? 0 : entity.getInputTokens(),
                    "outputTokens", entity.getOutputTokens() == null ? 0 : entity.getOutputTokens()));
        }
        return result;
    }

    private String extractKey(String authorization, String apiKeyHeader) {
        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            return apiKeyHeader.trim();
        }
        if (authorization != null && !authorization.isBlank()) {
            String value = authorization.trim();
            return value.startsWith("Bearer ") ? value.substring(7).trim() : value;
        }
        return null;
    }

}
