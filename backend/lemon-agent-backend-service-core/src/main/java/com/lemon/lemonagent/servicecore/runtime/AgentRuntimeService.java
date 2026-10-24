package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.dal.entity.AgentMessageEntity;
import com.lemon.lemonagent.dal.entity.AgentRunEntity;
import com.lemon.lemonagent.dal.entity.AgentSessionEntity;
import com.lemon.lemonagent.dal.repository.AgentMessageRepository;
import com.lemon.lemonagent.dal.repository.AgentRunRepository;
import com.lemon.lemonagent.dal.repository.AgentSessionRepository;
import com.lemon.lemonagent.model.runtime.ModelPort;
import com.lemon.lemonagent.model.runtime.RunData;
import com.lemon.lemonagent.model.runtime.RunListItem;
import com.lemon.lemonagent.model.runtime.RunListData;
import com.lemon.lemonagent.model.runtime.RunStatus;
import com.lemon.lemonagent.model.runtime.RunStatusData;
import com.lemon.lemonagent.model.runtime.RuntimeEventType;
import com.lemon.lemonagent.model.runtime.SessionData;
import com.lemon.lemonagent.model.runtime.SessionDetailData;
import com.lemon.lemonagent.model.runtime.SessionListItem;
import com.lemon.lemonagent.model.runtime.SessionListData;
import com.lemon.lemonagent.model.runtime.Strategy;
import com.lemon.lemonagent.model.runtime.MessageItem;
import com.lemon.lemonagent.model.runtime.RunBrief;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import tools.jackson.databind.ObjectMapper;

/**
 * 对话运行时领域服务：会话管理、运行编排（幂等 / 互斥 / 状态机）、SSE 订阅、取消。
 *
 * <p>越权口径：所有查询按 (id, userId) 双键收口，「存在但不属于你」统一表现为 NOT_FOUND
 * ——不区分两种 404，避免泄露资源存在性（与 v1 连接管理一致；契约的 403 FORBIDDEN
 * 语义由此被覆盖为更安全的 404，属于有意收紧）。</p>
 */
@Service
public class AgentRuntimeService {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntimeService.class);

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;
    private static final int MAX_INPUT_LENGTH = 4000;
    private static final int TITLE_FROM_INPUT_LENGTH = 20;

    private final AgentSessionRepository sessionRepository;

    private final AgentRunRepository runRepository;

    private final AgentMessageRepository messageRepository;

    private final EventPublisher eventPublisher;

    private final RunExecutor runExecutor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final SecureRandom random = new SecureRandom();

    public AgentRuntimeService(AgentSessionRepository sessionRepository,
                               AgentRunRepository runRepository,
                               AgentMessageRepository messageRepository,
                               EventPublisher eventPublisher,
                               RunExecutor runExecutor) {
        this.sessionRepository = sessionRepository;
        this.runRepository = runRepository;
        this.messageRepository = messageRepository;
        this.eventPublisher = eventPublisher;
        this.runExecutor = runExecutor;
    }

    // ---------- 会话 ----------

    /**
     * 创建会话（契约 4.2）：标题留空给默认值，懒创建场景由 RunService 用首条消息补名。
     *
     * @param userId 入参：归属用户
     * @param title  入参：标题（可空）
     * @return 出参：会话数据
     */
    public SessionData createSession(Long userId, String title) {
        AgentSessionEntity entity = new AgentSessionEntity();
        entity.setUserId(userId);
        entity.setTitle(title == null || title.isBlank() ? "新对话" : title.trim());
        sessionRepository.insert(entity);
        return new SessionData(entity.getId(), format(entity.getCreatedAt()));
    }

    /**
     * 会话列表（契约 4.3，lastActiveAt 倒序 + 消息条数）。
     */
    public SessionListData listSessions(Long userId, int page, int size) {
        SessionListData data = new SessionListData();
        data.setTotal(sessionRepository.countByUser(userId));
        data.setItems(sessionRepository.findByUser(userId, (page - 1) * size, size).stream()
                .map(s -> {
                    SessionListItem item = new SessionListItem();
                    item.setSessionId(s.getId());
                    item.setTitle(s.getTitle());
                    item.setLastActiveAt(format(s.getLastActiveAt()));
                    item.setMessageCount((int) sessionRepository.messageCount(s.getId()));
                    return item;
                })
                .toList());
        return data;
    }

    /**
     * 会话消息 + 运行快照（契约 4.4，刷新恢复数据源）。
     */
    public SessionDetailData sessionDetail(Long userId, Long sessionId) {
        AgentSessionEntity session = requireSession(userId, sessionId);
        SessionDetailData data = new SessionDetailData();
        data.setSessionId(session.getId());
        data.setMessages(messageRepository.findBySession(session.getId(), userId).stream()
                .map(m -> {
                    MessageItem item = new MessageItem();
                    item.setMessageId(m.getId());
                    item.setRole(m.getRole());
                    item.setContent(m.getContent());
                    item.setRunId(m.getRunId());
                    item.setCreatedAt(format(m.getCreatedAt()));
                    return item;
                })
                .toList());
        data.setRuns(runRepository.findBySession(session.getId(), userId).stream()
                .map(r -> {
                    RunBrief brief = new RunBrief();
                    brief.setRunId(r.getId());
                    brief.setStatus(r.getStatus());
                    brief.setLastSeq(r.getLastSeq());
                    brief.setTraceId(r.getTraceId());
                    brief.setStrategy(r.getStrategy());
                    return brief;
                })
                .toList());
        return data;
    }

    // ---------- 运行 ----------

    /**
     * 发起运行（契约 4.5）：幂等 → 互斥 → 建 run → run_started → 落 USER 消息 → 异步执行。
     *
     * @return 出参：运行数据（duplicated=true 时为原 run）
     */
    public RunData createRun(Long userId, Long sessionId, String input, String strategyRaw, String clientRequestId) {
        if (input == null || input.isBlank() || input.length() > MAX_INPUT_LENGTH) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "输入不能为空且不超过 4000 字符");
        }
        if (clientRequestId == null || clientRequestId.isBlank() || clientRequestId.length() > 64) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "clientRequestId 必须为不超过 64 位的幂等键");
        }
        Strategy strategy = Strategy.parse(strategyRaw);

        // ① 幂等：同 (userId, clientRequestId) 命中原 run，不新建不二次调模型
        AgentRunEntity existing = runRepository.findByClientRequestId(userId, clientRequestId);
        if (existing != null) {
            return toRunData(existing, true);
        }

        // ② 会话归属 / 懒创建
        AgentSessionEntity session;
        if (sessionId == null) {
            session = new AgentSessionEntity();
            session.setUserId(userId);
            session.setTitle(input.length() > TITLE_FROM_INPUT_LENGTH
                    ? input.substring(0, TITLE_FROM_INPUT_LENGTH) : input);
            sessionRepository.insert(session);
        } else {
            session = requireSession(userId, sessionId);
        }

        // ③ 会话互斥：同会话同时只允许一个活跃 run
        AgentRunEntity active = runRepository.findActiveBySession(session.getId());
        if (active != null) {
            throw new SessionBusyException(active.getId());
        }

        // ④ 建 run：创建即 RUNNING；run_started 占 seq=1
        AgentRunEntity run = new AgentRunEntity();
        run.setSessionId(session.getId());
        run.setUserId(userId);
        run.setClientRequestId(clientRequestId);
        run.setStatus(RunStatus.RUNNING.name());
        run.setStrategy(strategy.wireName());
        run.setTraceId(newTraceId());
        run.setLastSeq(0);
        runRepository.insert(run);
        eventPublisher.initCounter(run.getId(), 0);

        // ⑤ USER 消息落库 + 会话活跃（run_started 事件由 appendStarted 在下一步落库，seq=1）
        AgentMessageEntity userMessage = new AgentMessageEntity();
        userMessage.setSessionId(session.getId());
        userMessage.setUserId(userId);
        userMessage.setRole("USER");
        userMessage.setContent(input);
        messageRepository.insert(userMessage);
        sessionRepository.touch(session.getId());

        // ⑥ run_started 事件（seq=1）：游标回填运行实体，响应 lastSeq 即为前端续流起点
        long startedSeq = eventPublisher.append(run.getId(), RuntimeEventType.RUN_STARTED.wireName(),
                objectMapper.writeValueAsString(Map.of(
                        "runId", run.getId(),
                        "sessionId", run.getSessionId(),
                        "traceId", run.getTraceId(),
                        "strategy", run.getStrategy(),
                        "ts", System.currentTimeMillis())));
        run.setLastSeq(startedSeq);
        runExecutor.submit(run);
        return toRunData(run, false);
    }

    /**
     * SSE 订阅（契约 4.7）：归属校验后交给 EventPublisher（重放 → 注册 → 补一轮）。
     *
     * @param afterQuery   入参：query 的 afterEventId（可空）
     * @param lastEventId  入参：Last-Event-ID 头（可空）
     * @return 出参：SSE emitter
     */
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter subscribe(
            Long userId, Long runId, Long afterQuery, String lastEventId) {
        AgentRunEntity run = requireRun(userId, runId);
        long cursor = Math.max(afterQuery == null ? 0 : afterQuery, parseLastEventId(lastEventId));
        RunStatus status = RunStatus.valueOf(run.getStatus());
        return eventPublisher.subscribe(runId, cursor, status.terminal(), e -> { });
    }

    /**
     * 取消运行（契约 4.9）：仅 RUNNING/BLOCKED 可取消；已是 CANCELLED 幂等返回；其他终态 409。
     */
    public RunStatusData cancel(Long userId, Long runId) {
        AgentRunEntity run = requireRun(userId, runId);
        RunStatus status = RunStatus.valueOf(run.getStatus());
        if (status == RunStatus.CANCELLED) {
            return new RunStatusData(runId, status.name(), run.getLastSeq());
        }
        if (!status.cancellable()) {
            throw new BusinessException("ILLEGAL_STATE", "运行已结束（" + status.name() + "），不能取消");
        }
        int rows = runRepository.transition(runId, status.name(), RunStatus.CANCELLED.name(),
                "CANCELLED", "已取消。");
        if (rows == 0) {
            // 并发窗口里状态已变：重读后按幂等口径返回当前状态
            AgentRunEntity current = requireRun(userId, runId);
            return new RunStatusData(runId, current.getStatus(), current.getLastSeq());
        }
        runExecutor.requestCancel(runId);
        long seq = eventPublisher.append(runId, RuntimeEventType.RUN_CANCELLED.wireName(),
                objectMapper.writeValueAsString(Map.of(
                        "runId", runId,
                        "cancelledBy", "USER",
                        "ts", System.currentTimeMillis())));
        return new RunStatusData(runId, RunStatus.CANCELLED.name(), seq);
    }

    /**
     * 运行快照（契约 4.6）：BLOCKED 轮询、续流起点、失败归因都从这里读。
     */
    public com.lemon.lemonagent.model.runtime.RunDetailData snapshot(Long userId, Long runId) {
        AgentRunEntity run = requireRun(userId, runId);
        com.lemon.lemonagent.model.runtime.RunDetailData data = new com.lemon.lemonagent.model.runtime.RunDetailData();
        data.setRunId(run.getId());
        data.setSessionId(run.getSessionId());
        data.setStatus(run.getStatus());
        data.setStrategy(run.getStrategy());
        data.setTraceId(run.getTraceId());
        data.setLastSeq(run.getLastSeq());
        data.setErrorCode(run.getErrorCode());
        data.setErrorMsg(run.getErrorMsg());
        data.setPendingApproval(null);
        data.setGraph(null);
        if (run.getInputTokens() != null || run.getOutputTokens() != null) {
            com.lemon.lemonagent.model.runtime.RunDetailData.Usage usage =
                    new com.lemon.lemonagent.model.runtime.RunDetailData.Usage();
            usage.setInputTokens(run.getInputTokens() == null ? 0 : run.getInputTokens());
            usage.setOutputTokens(run.getOutputTokens() == null ? 0 : run.getOutputTokens());
            data.setUsage(usage);
        }
        data.setStartedAt(format(run.getStartedAt()));
        data.setEndedAt(format(run.getEndedAt()));
        return data;
    }

    /**
     * 提交审批决定（契约 4.8）。v2 增量 1 无工具审批：任何 run 都不会 BLOCKED，
     * 因此必然 409 ILLEGAL_STATE——端点先行就位，v2.1 工具链路接入后由 kind 分流恢复动作。
     */
    public RunStatusData approval(Long userId, Long runId, String approvalId, String decision, String comment) {
        AgentRunEntity run = requireRun(userId, runId);
        RunStatus status = RunStatus.valueOf(run.getStatus());
        if (status != RunStatus.BLOCKED) {
            throw new BusinessException("ILLEGAL_STATE", "当前运行没有待审批项（状态 " + status.name() + "）");
        }
        throw new BusinessException("APPROVAL_MISMATCH", "审批项不存在或已处理");
    }

    /**
     * 运行列表（契约 4.10，必须 user_id 过滤）。
     */
    public RunListData listRuns(Long userId, Long sessionId, String status, int page, int size) {
        RunListData data = new RunListData();
        data.setTotal(runRepository.countByUser(userId, blankToNull(status), sessionId));
        data.setItems(runRepository.findByUser(userId, blankToNull(status), sessionId,
                        (page - 1) * size, size).stream()
                .map(r -> {
                    RunListItem item = new RunListItem();
                    item.setRunId(r.getId());
                    item.setStatus(r.getStatus());
                    item.setStrategy(r.getStrategy());
                    item.setErrorCode(r.getErrorCode());
                    item.setTraceId(r.getTraceId());
                    item.setLastSeq(r.getLastSeq());
                    item.setStartedAt(format(r.getStartedAt()));
                    item.setEndedAt(format(r.getEndedAt()));
                    return item;
                })
                .toList());
        return data;
    }

    /**
     * 进程重启后的孤儿清理：把上次进程遗留的 RUNNING/BLOCKED run 标为 FAILED(INTERRUPTED)，
     * 否则前端会对着一个永远不会推进的 RUNNING run 无限续流。由组合根在启动完成时调用。
     *
     * @return 出参：收口的运行数
     */
    public int failOrphanRunsOnStartup() {
        int rows = runRepository.markOrphansAsInterrupted();
        if (rows > 0) {
            log.warn("启动期孤儿运行收口：{} 个 RUNNING/BLOCKED run 已标记为 FAILED(INTERRUPTED)", rows);
        }
        return rows;
    }

    private AgentSessionEntity requireSession(Long userId, Long sessionId) {
        AgentSessionEntity session = sessionRepository.findByIdAndUser(sessionId, userId);
        if (session == null) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "会话不存在或已被删除");
        }
        return session;
    }

    private AgentRunEntity requireRun(Long userId, Long runId) {
        AgentRunEntity run = runRepository.findByIdAndUser(runId, userId);
        if (run == null) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "运行不存在或已被删除");
        }
        return run;
    }

    private RunData toRunData(AgentRunEntity run, boolean duplicated) {
        return new RunData(run.getId(), run.getSessionId(), run.getTraceId(), run.getStatus(),
                run.getLastSeq(), duplicated, format(run.getStartedAt()));
    }

    private static long parseLastEventId(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(lastEventId.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static String format(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC).format(ISO);
    }

    private String newTraceId() {
        return HexFormat.of().formatHex(randomBytes());
    }

    private byte[] randomBytes() {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    /** 会话互斥异常：携带活跃 runId 供包络 data.activeRunId 带出（契约 4.5 的 409 SESSION_BUSY）。 */
    public static class SessionBusyException extends BusinessException {

        private final Long activeRunId;

        public SessionBusyException(Long activeRunId) {
            super("SESSION_BUSY", "该会话已有正在执行的运行，请先等待完成或取消");
            this.activeRunId = activeRunId;
        }

        public Long getActiveRunId() {
            return activeRunId;
        }

    }

}
