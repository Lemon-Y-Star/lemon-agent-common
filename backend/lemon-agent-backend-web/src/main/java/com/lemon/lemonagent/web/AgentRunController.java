package com.lemon.lemonagent.web;

import com.lemon.lemonagent.model.runtime.ApprovalReq;
import com.lemon.lemonagent.model.runtime.ApprovalResp;
import com.lemon.lemonagent.model.runtime.CancelRunReq;
import com.lemon.lemonagent.model.runtime.RetryRunReq;
import com.lemon.lemonagent.model.runtime.RetryRunResp;
import com.lemon.lemonagent.model.runtime.CancelRunResp;
import com.lemon.lemonagent.model.runtime.CreateRunReq;
import com.lemon.lemonagent.model.runtime.CreateRunResp;
import com.lemon.lemonagent.model.runtime.RunDetailData;
import com.lemon.lemonagent.model.runtime.RunListData;
import com.lemon.lemonagent.servicecore.runtime.AgentRuntimeService;
import com.lemon.architecture.model.BaseResult;
import com.lemon.architecture.service.util.opt.OptUtil;
import com.lemon.architecture.util.validation.AssertUtils;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 运行接口（契约 4.5 ~ 4.10）：发起 / 快照 / SSE 事件流（含 afterEventId 重连）/ 取消 / 列表。
 *
 * <p>SSE 端点是唯一不以 BaseResult 包络返回的端点（传输层是事件流而非 JSON 包络），
 * 鉴权仍走 AuthInterceptor。</p>
 */
@RestController
@RequestMapping("/api/v1/agent/runs")
public class AgentRunController {

    /** 框架操作入口。 */
    @Resource
    private OptUtil optUtil;

    /** 运行时领域服务。 */
    @Resource
    private AgentRuntimeService agentRuntimeService;

    /**
     * 发起运行（幂等键 clientRequestId；懒创建允许 sessionId 为空）。
     *
     * @param request 运行入参
     * @param userId  归属用户
     * @param source  调用来源
     * @return 运行数据（含 traceId 与 lastSeq）
     */
    @PostMapping
    public CreateRunResp create(@RequestBody CreateRunReq request,
                                @RequestAttribute("lemon.userId") Long userId,
                                @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        request.setUserId(userId);
        request.fillRoute(source);
        CreateRunResp resp = optUtil.exec(request, new CreateRunResp(), req -> {
            AssertUtils.notBlank(req.getInput(), "输入不能为空");
            AssertUtils.isTrue(req.getInput().length() <= 4000, "输入不能超过 4000 字符");
            AssertUtils.notBlank(req.getClientRequestId(), "clientRequestId 不能为空");
        });
        // traceId 同时带响应头（契约 4.5）
        if (resp.getData() instanceof com.lemon.lemonagent.model.runtime.RunData data) {
            var attrs = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
            if (attrs instanceof org.springframework.web.context.request.ServletRequestAttributes servlet) {
                var response = servlet.getResponse();
                if (response != null) {
                    response.setHeader("X-Trace-Id", data.getTraceId());
                }
            }
        }
        return resp;
    }

    /**
     * 运行快照（BLOCKED 轮询 / 续流起点）。
     *
     * @param runId  运行 ID
     * @param userId 归属用户
     * @return 快照
     */
    @GetMapping("/{runId}")
    public BaseResult<RunDetailData> snapshot(@PathVariable Long runId,
                                              @RequestAttribute("lemon.userId") Long userId) {
        return BaseResult.success(agentRuntimeService.snapshot(userId, runId));
    }

    /**
     * SSE 事件流（契约 4.7）：afterEventId 重放 → 注册 → 补一轮 → 心跳保活。
     *
     * @param runId        运行 ID
     * @param userId       归属用户
     * @param afterEventId 重放游标（query，可空）
     * @param lastEventId  重放游标（SSE 标准头，兼容保留）
     * @return 事件流
     */
    @GetMapping(value = "/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable Long runId,
                             @RequestAttribute("lemon.userId") Long userId,
                             @RequestParam(value = "afterEventId", required = false) Long afterEventId,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return agentRuntimeService.subscribe(userId, runId, afterEventId, lastEventId);
    }

    /**
     * 取消运行（先通知后端、收到响应再 abort 本地 SSE——契约 4.9 前端顺序）。
     *
     * @param runId  运行 ID
     * @param userId 归属用户
     * @param source 调用来源
     * @return 取消后状态
     */
    @PostMapping("/{runId}/cancel")
    public CancelRunResp cancel(@PathVariable Long runId,
                                @RequestAttribute("lemon.userId") Long userId,
                                @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        CancelRunReq request = new CancelRunReq();
        request.setRunId(runId);
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new CancelRunResp(), req ->
                AssertUtils.notNull(req.getRunId(), "运行 ID 不能为空"));
    }

    /**
     * 重试失败运行（v3.9.1）：按原运行记录的输入 / 策略 / 深度思考重放，仅 FAILED / TIMEOUT 可重试。
     *
     * @param runId  路径参数：运行 ID
     * @param userId 当前用户
     * @param source 调用来源
     * @return 新运行数据
     */
    @PostMapping("/{runId}/retry")
    public RetryRunResp retry(@PathVariable Long runId,
                              @RequestAttribute("lemon.userId") Long userId,
                              @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        RetryRunReq request = new RetryRunReq();
        request.setRunId(runId);
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new RetryRunResp(), req ->
                AssertUtils.notNull(req.getRunId(), "运行 ID 不能为空"));
    }

    /**
     * 提交审批决定（契约 4.8）：APPROVE → RUNNING（拿 lastSeq 续流）；REJECT → FAILED(TOOL_REJECTED)。
     *
     * @param runId  运行 ID
     * @param request 审批决定
     * @param userId  归属用户
     * @param source  调用来源
     * @return 提交后的运行状态
     */
    @PostMapping("/{runId}/approval")
    public ApprovalResp approval(@PathVariable Long runId,
                                 @RequestBody ApprovalReq request,
                                 @RequestAttribute("lemon.userId") Long userId,
                                 @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        request.setRunId(runId);
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new ApprovalResp(), req -> {
            AssertUtils.notBlank(req.getApprovalId(), "approvalId 不能为空");
            AssertUtils.notBlank(req.getDecision(), "decision 不能为空");
        });
    }

    /**
     * 运行列表（运行历史页数据源 / BLOCKED 轮询感知 / 按状态筛失败 run）。
     *
     * @param userId    归属用户
     * @param sessionId 会话过滤（可空）
     * @param status    状态过滤（可空）
     * @param traceId   全链路 ID 精确匹配（可空；运行历史页的 traceId 反查）
     * @param page      页码
     * @param size      页大小
     * @return 列表数据
     */
    @GetMapping
    public BaseResult<RunListData> list(@RequestAttribute("lemon.userId") Long userId,
                                        @RequestParam(required = false) Long sessionId,
                                        @RequestParam(required = false) String status,
                                        @RequestParam(required = false) String traceId,
                                        @RequestParam(required = false) Long agentId,
                                        @RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return BaseResult.success(agentRuntimeService.listRuns(userId, sessionId, status, traceId,
                agentId, page, Math.min(size, 100)));
    }

}
