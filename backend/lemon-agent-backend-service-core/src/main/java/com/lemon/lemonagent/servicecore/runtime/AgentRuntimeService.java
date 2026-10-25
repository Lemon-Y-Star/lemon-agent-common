package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.dal.entity.AgentConfigEntity;
import com.lemon.lemonagent.dal.entity.AgentMessageEntity;
import com.lemon.lemonagent.dal.entity.AgentRunEntity;
import com.lemon.lemonagent.dal.entity.AgentSessionEntity;
import com.lemon.lemonagent.dal.repository.AgentMessageRepository;
import com.lemon.lemonagent.dal.repository.AgentRunEventRepository;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

    private final AgentRunEventRepository eventRepository;

    private final EventPublisher eventPublisher;

    private final RunExecutor runExecutor;

    private final AgentConfigService agentConfigService;

    private final com.lemon.lemonagent.dal.repository.AgentGraphRepository agentGraphRepository;

    private final WorkflowService workflowService;

    private final com.lemon.lemonagent.servicecore.safety.SensitiveWordService sensitiveWords;

    private final ToolRegistry toolRegistry;

    private final com.lemon.lemonagent.dal.repository.AgentConfigRepository agentConfigRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final SecureRandom random = new SecureRandom();

    public AgentRuntimeService(AgentSessionRepository sessionRepository,
                               AgentRunRepository runRepository,
                               AgentMessageRepository messageRepository,
                               AgentRunEventRepository eventRepository,
                               EventPublisher eventPublisher,
                               RunExecutor runExecutor,
                               AgentConfigService agentConfigService,
                               com.lemon.lemonagent.dal.repository.AgentGraphRepository agentGraphRepository,
                               WorkflowService workflowService,
                               com.lemon.lemonagent.servicecore.safety.SensitiveWordService sensitiveWords,
                               ToolRegistry toolRegistry,
                               com.lemon.lemonagent.dal.repository.AgentConfigRepository agentConfigRepository) {
        this.sessionRepository = sessionRepository;
        this.runRepository = runRepository;
        this.messageRepository = messageRepository;
        this.eventRepository = eventRepository;
        this.eventPublisher = eventPublisher;
        this.runExecutor = runExecutor;
        this.agentConfigService = agentConfigService;
        this.agentGraphRepository = agentGraphRepository;
        this.workflowService = workflowService;
        this.sensitiveWords = sensitiveWords;
        this.toolRegistry = toolRegistry;
        this.agentConfigRepository = agentConfigRepository;
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

    /**
     * 重命名会话。
     *
     * @param userId  入参：归属用户
     * @param sessionId 入参：会话 ID
     * @param title   入参：新标题（1..100 字符）
     * @return 出参：会话数据
     */
    public SessionData rename(Long userId, Long sessionId, String title) {
        if (title == null || title.isBlank() || title.length() > 100) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "标题不能为空且不超过 100 字符");
        }
        AgentSessionEntity session = requireSession(userId, sessionId);
        sessionRepository.rename(session.getId(), title.trim());
        return new SessionData(session.getId(), format(session.getCreatedAt()));
    }

    /**
     * 删除会话：级联删除消息、事件与运行（运行删除前要求非活跃，避免删掉一个正在执行的 run）。
     *
     * @param userId  入参：归属用户
     * @param sessionId 入参：会话 ID
     */
    public void delete(Long userId, Long sessionId) {
        AgentSessionEntity session = requireSession(userId, sessionId);
        AgentRunEntity active = runRepository.findActiveBySession(session.getId());
        if (active != null) {
            throw new BusinessException("SESSION_BUSY", "会话有正在执行的运行，先取消再删除");
        }
        sessionRepository.deleteCascade(session.getId(), userId);
    }

    // ---------- 运行 ----------

    /**
     * 发起运行（契约 4.5）：幂等 → 互斥 → 建 run → run_started → 落 USER 消息 → 异步执行。
     *
     * @return 出参：运行数据（duplicated=true 时为原 run）
     */
    public RunData createRun(Long userId, Long sessionId, Long agentId, String input, String strategyRaw,
                             String clientRequestId, Boolean thinking) {
        if (input == null || input.isBlank() || input.length() > MAX_INPUT_LENGTH) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "输入不能为空且不超过 4000 字符");
        }
        // 敏感词输入拦截（v3.9.2）：命中即拒绝，不做替换——输入是用户的意图，改写有风险
        java.util.List<String> sensitiveHits = sensitiveWords.hits(input);
        if (!sensitiveHits.isEmpty()) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR,
                    "输入包含敏感词：" + String.join("、", sensitiveHits) + "，请修改后重试");
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
        // Agent 挂载：跟随当前已启用版本（PRD S2 工具语义）；未指定时取平台默认
        AgentConfigEntity agent = resolveAgent(userId, agentId);

        // custom（执行图）：提交时冻结图快照并校验，改图不影响在跑的运行；图非法则直接拒绝，不分配 seq
        String graphSnapshot = null;
        String graphStateSnapshot = null;
        if (strategy == Strategy.CUSTOM) {
            // v3.7：挂载了工作流就跑工作流的版本快照，否则跑 Agent 自己的执行图
            String rawGraphJson;
            Integer rawVersion;
            if (agent.getWorkflowId() != null) {
                com.lemon.lemonagent.dal.entity.WorkflowVersionEntity wfVersion =
                        workflowService.resolveVersion(agent.getWorkflowId(), agent.getWorkflowVersion());
                if (wfVersion == null) {
                    throw new BusinessException(ResultCodes.VALIDATION_ERROR,
                            "该 Agent 挂载的工作流没有可用版本，请先在「工作流」里保存版本");
                }
                rawGraphJson = wfVersion.getGraphJson();
                rawVersion = wfVersion.getVersion();
            } else {
                com.lemon.lemonagent.dal.entity.AgentGraphEntity graphEntity =
                        agentGraphRepository.findActive(agent.getId());
                if (graphEntity == null) {
                    throw new BusinessException(ResultCodes.VALIDATION_ERROR,
                            "该 Agent 还没有执行图，请先在「执行图」里配置后再用 custom 策略");
                }
                rawGraphJson = graphEntity.getGraphJson();
                rawVersion = graphEntity.getVersion();
            }
            com.lemon.lemonagent.model.runtime.AgentGraphData graph = GraphCodec.decodeGraph(rawGraphJson);
            GraphValidator.validate(graph, toolRegistry);
            graph.setVersion(rawVersion);
            graphSnapshot = GraphCodec.encodeGraph(graph);
            graphStateSnapshot = GraphCodec.encodeState(new com.lemon.lemonagent.model.runtime.GraphStateData());
        }

        AgentRunEntity run = new AgentRunEntity();
        run.setSessionId(session.getId());
        run.setUserId(userId);
        run.setAgentId(agent.getId());
        run.setGraphJson(graphSnapshot);
        run.setGraphStateJson(graphStateSnapshot);
        run.setClientRequestId(clientRequestId);
        run.setStatus(RunStatus.RUNNING.name());
        run.setStrategy(strategy.wireName());
        run.setInput(input);
        run.setTraceId(newTraceId());
        run.setLastSeq(0);
        runRepository.insert(run);
        if (graphSnapshot != null) {
            runRepository.saveGraph(run.getId(), graphSnapshot, graphStateSnapshot);
        }
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
        // 深度思考只对 auto / fixed 生效（plan / custom 已有计划卡 / 节点进度作为可见过程），其余策略归零
        int thinkingFlag = thinking != null && thinking
                && (strategy == Strategy.AUTO || strategy == Strategy.FIXED) ? 1 : 0;
        run.setThinking(thinkingFlag);
        runRepository.saveThinking(run.getId(), thinkingFlag);
        long startedSeq = eventPublisher.append(run.getId(), RuntimeEventType.RUN_STARTED.wireName(),
                objectMapper.writeValueAsString(Map.of(
                        "runId", run.getId(),
                        "sessionId", run.getSessionId(),
                        "traceId", run.getTraceId(),
                        "strategy", run.getStrategy(),
                        "thinking", thinkingFlag == 1,
                        "ts", System.currentTimeMillis())));
        run.setLastSeq(startedSeq);
        runExecutor.submit(run);
        return toRunData(run, false);
    }

    /**
     * 重试失败运行（v3.9.1）：复用原始输入 / 策略 / 深度思考 / Agent，在**原会话**里发起新运行——
     * 会话上下文不丢，多轮对话可以接着聊。仅 FAILED / TIMEOUT 可重试（幂等键独立生成，与原 run 无关）。
     *
     * @param userId 入参：当前用户
     * @param runId  入参：要重试的运行 ID
     * @return 出参：新运行数据
     */
    public RunData retryRun(Long userId, Long runId) {
        AgentRunEntity origin = runRepository.findByIdAndUser(runId, userId);
        if (origin == null) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "运行不存在");
        }
        boolean failed = RunStatus.FAILED.name().equals(origin.getStatus())
                || RunStatus.TIMEOUT.name().equals(origin.getStatus());
        if (!failed) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "只有失败 / 超时的运行可以重试");
        }
        if (origin.getInput() == null || origin.getInput().isBlank()) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "该运行没有记录原始输入（旧数据），无法重试");
        }
        return createRun(userId, origin.getSessionId(), origin.getAgentId(), origin.getInput(),
                origin.getStrategy(), "retry_" + runId + "_" + UUID.randomUUID().toString().substring(0, 8),
                origin.getThinking() != null && origin.getThinking() == 1);
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
        data.setAgentId(run.getAgentId());
        data.setAgentName(run.getAgentId() == null ? null
                : agentNamesFor(userId).get(run.getAgentId()));
        data.setStatus(run.getStatus());
        data.setStrategy(run.getStrategy());
        data.setTraceId(run.getTraceId());
        data.setInput(run.getInput());
        data.setOutputText(run.getOutputText());
        data.setLastSeq(run.getLastSeq());
        data.setErrorCode(run.getErrorCode());
        data.setErrorMsg(run.getErrorMsg());
        Map<String, Object> pending = parsePending(run.getPendingApprovalJson());
        data.setPendingApproval(pending.isEmpty() ? null : pending);
        data.setGraph(graphView(run));
        // plan 策略：刷新页面后仍能看到计划与进度（auto 策略为 null）
        data.setPlan(PlanCodec.decode(run.getPlanJson()));
        data.setPlanStep(run.getPlanStep());
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
     * 提交审批决定（契约 4.8）：仅 BLOCKED + approvalId 一致才接受；幂等：同 approvalId
     * 重复提交返回当前状态；APPROVE → RUNNING + 异步续跑；REJECT → FAILED(TOOL_REJECTED)。
     */
    public RunStatusData approval(Long userId, Long runId, String approvalId, String decision, String comment) {
        AgentRunEntity run = requireRun(userId, runId);
        RunStatus status = RunStatus.valueOf(run.getStatus());
        if (status != RunStatus.BLOCKED) {
            // 幂等：该 approvalId 已处理过 → 返回当前状态不报错（契约 4.8 行为细则）
            if (approvalAlreadyResolved(runId, approvalId)) {
                return new RunStatusData(runId, run.getStatus(), run.getLastSeq());
            }
            throw new BusinessException("ILLEGAL_STATE", "当前运行没有待审批项（状态 " + status.name() + "）");
        }
        Map<String, Object> pending = parsePending(run.getPendingApprovalJson());
        if (!approvalId.equals(String.valueOf(pending.get("approvalId")))) {
            throw new BusinessException("APPROVAL_MISMATCH", "审批项不匹配或已过期");
        }

        boolean approved = "APPROVE".equalsIgnoreCase(decision);
        if (!approved && !"REJECT".equalsIgnoreCase(decision)) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "decision 必须为 APPROVE 或 REJECT");
        }

        // 状态迁移先行（原子闸）：APPROVE → RUNNING 续跑；REJECT → FAILED(TOOL_REJECTED)
        int rows = runRepository.transition(runId, RunStatus.BLOCKED.name(),
                approved ? RunStatus.RUNNING.name() : RunStatus.FAILED.name(),
                approved ? null : "TOOL_REJECTED",
                approved ? null : "你拒绝了本次工具调用，任务已终止。");
        if (rows == 0) {
            throw new BusinessException("ILLEGAL_STATE", "运行状态已变化，请刷新后重试");
        }
        runRepository.clearPendingApproval(runId);

        appendApprovalResolved(runId, approvalId, approved ? "APPROVE" : "REJECT");

        String kind = String.valueOf(pending.getOrDefault("kind", "TOOL"));
        boolean nodeApproval = "NODE".equals(kind);
        if (approved) {
            if (nodeApproval) {
                // 节点审批：标记该节点完成，回到图调度继续下一个节点
                runExecutor.resumeNodeApproved(run, String.valueOf(pending.get("nodeId")));
            } else {
                // 工具审批：执行工具 → tool_result → 继续模型循环
                runExecutor.resumeApproved(run,
                        String.valueOf(pending.get("toolName")),
                        (Map<String, Object>) pending.get("args"));
            }
        } else {
            String errorMsg = nodeApproval ? "你拒绝了该审批节点，任务已终止。" : "你拒绝了本次工具调用，任务已终止。";
            appendEvent(runId, RuntimeEventType.RUN_FAILED, objectMapper.writeValueAsString(Map.of(
                    "runId", runId,
                    "errorCode", "TOOL_REJECTED",
                    "errorMsg", errorMsg,
                    "ts", System.currentTimeMillis())));
        }
        AgentRunEntity current = requireRun(userId, runId);
        return new RunStatusData(runId, current.getStatus(), current.getLastSeq());
    }

    /**
     * 收件箱（PRD FR-21 简化版）：通知由现有数据派生——待审批（BLOCKED）+ 近 7 天失败运行。
     * 不新增通知表；已读语义由前端 localStorage 的最后查看时间承载。
     */
    public com.lemon.lemonagent.model.runtime.InboxData inbox(Long userId) {
        com.lemon.lemonagent.model.runtime.InboxData data = new com.lemon.lemonagent.model.runtime.InboxData();
        List<com.lemon.lemonagent.model.runtime.InboxData.Item> items = new ArrayList<>();
        for (AgentRunEntity run : runRepository.findByStatus(RunStatus.BLOCKED.name(), 50)) {
            if (run.getUserId().equals(userId)) {
                Map<String, Object> pending = parsePending(run.getPendingApprovalJson());
                com.lemon.lemonagent.model.runtime.InboxData.Item item = new com.lemon.lemonagent.model.runtime.InboxData.Item();
                item.setKind("APPROVAL");
                item.setRunId(run.getId());
                item.setSessionId(run.getSessionId());
                item.setText("运行 #" + run.getId() + " 待审批：工具 " + pending.getOrDefault("toolName", "?")
                        + "（" + pending.getOrDefault("riskLevel", "HIGH") + "）");
                item.setCreatedAt(format(run.getUpdatedAt()));
                items.add(item);
            }
        }
        // 近 7 天失败
        runRepository.findByUser(userId, RunStatus.FAILED.name(), null, null, null, 0, 50).forEach(run -> {
            if (run.getStartedAt() != null && run.getStartedAt().isAfter(Instant.now().minusSeconds(7 * 86400L))) {
                com.lemon.lemonagent.model.runtime.InboxData.Item item = new com.lemon.lemonagent.model.runtime.InboxData.Item();
                item.setKind("RUN_FAILED");
                item.setRunId(run.getId());
                item.setSessionId(run.getSessionId());
                item.setText("运行 #" + run.getId() + " 失败：" + (run.getErrorMsg() != null ? run.getErrorMsg() : run.getErrorCode()));
                item.setCreatedAt(format(run.getUpdatedAt()));
                items.add(item);
            }
        });
        // createdAt 为 ISO-8601 字符串（字典序 == 时间序），倒序把最新的一条放最前；null 沉底
        items.sort((a, b) -> {
            String left = a.getCreatedAt();
            String right = b.getCreatedAt();
            if (left == null && right == null) {
                return 0;
            }
            if (left == null) {
                return 1;
            }
            if (right == null) {
                return -1;
            }
            return right.compareTo(left);
        });
        data.setItems(items);
        data.setUnreadHint(items.size());
        return data;
    }

    /**
     * 审批超时扫描（BUILD-SPEC §1.3：approval-timeout-seconds）：BLOCKED 超时 →
     * FAILED(APPROVAL_TIMEOUT)，审批自动拒绝（approval_resolved TIMEOUT + run_failed）。
     * 由组合根的 @EnableScheduling 驱动，30 秒一轮。
     */
    @Scheduled(fixedDelay = 30_000)
    public void scanExpiredApprovals() {
        List<AgentRunEntity> blocked = runRepository.findByStatus(RunStatus.BLOCKED.name(), 100);
        long now = System.currentTimeMillis();
        for (AgentRunEntity run : blocked) {
            Map<String, Object> pending = parsePending(run.getPendingApprovalJson());
            long expiresAt = Long.parseLong(String.valueOf(pending.getOrDefault("expiresAt", "0")));
            if (expiresAt > 0 && expiresAt < now) {
                int rows = runRepository.transition(run.getId(), RunStatus.BLOCKED.name(),
                        RunStatus.FAILED.name(), "APPROVAL_TIMEOUT",
                        "审批超时，本次操作已自动拒绝。");
                if (rows > 0) {
                    runRepository.clearPendingApproval(run.getId());
                    appendApprovalResolved(run.getId(),
                            String.valueOf(pending.get("approvalId")), "TIMEOUT");
                    appendEvent(run.getId(), RuntimeEventType.RUN_FAILED, objectMapper.writeValueAsString(Map.of(
                            "runId", run.getId(),
                            "errorCode", "APPROVAL_TIMEOUT",
                            "errorMsg", "审批超时，本次操作已自动拒绝。",
                            "ts", System.currentTimeMillis())));
                    log.info("审批超时自动拒绝 runId:{} approvalId:{}", run.getId(), pending.get("approvalId"));
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePending(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private boolean approvalAlreadyResolved(Long runId, String approvalId) {
        return eventRepository.findByRunAfter(runId, 0).stream()
                .anyMatch(e -> "approval_resolved".equals(e.getEventType())
                        && String.valueOf(e.getPayload()).contains(approvalId));
    }

    private void appendApprovalResolved(Long runId, String approvalId, String decision) {
        appendEvent(runId, RuntimeEventType.APPROVAL_RESOLVED, objectMapper.writeValueAsString(Map.of(
                "approvalId", approvalId,
                "decision", decision,
                "operator", "USER",
                "ts", System.currentTimeMillis())));
    }

    /**
     * 执行图视图：节点 + 当前状态（前端刷新后复原用）。非 custom 运行返回 null。
     *
     * @param run 入参：运行实体
     * @return 出参：图视图
     */
    private Object graphView(AgentRunEntity run) {
        com.lemon.lemonagent.model.runtime.AgentGraphData graph = GraphCodec.decodeGraph(run.getGraphJson());
        if (graph == null || graph.getNodes() == null || graph.getNodes().isEmpty()) {
            return null;
        }
        com.lemon.lemonagent.model.runtime.GraphStateData state = GraphCodec.decodeState(run.getGraphStateJson());
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (com.lemon.lemonagent.model.runtime.GraphNode node : graph.getNodes()) {
            String status = state.getDone().contains(node.getId()) ? "DONE"
                    : state.getSkipped().contains(node.getId()) ? "SKIPPED"
                    : node.getId().equals(state.getCurrent()) ? "RUNNING" : "PENDING";
            String title = node.getTitle() == null || node.getTitle().isBlank() ? node.getId() : node.getTitle();
            // 用 HashMap：x/y 与输入/输出可能为 null，Map.of 不允许 null 值
            Map<String, Object> item = new java.util.HashMap<>();
            item.put("id", node.getId());
            item.put("type", node.getType());
            item.put("title", title);
            item.put("status", status);
            item.put("x", node.getX());
            item.put("y", node.getY());
            item.put("input", state.getInputs() == null ? null : state.getInputs().get(node.getId()));
            item.put("output", state.getOutputs() == null ? null : state.getOutputs().get(node.getId()));
            nodes.add(item);
        }
        return Map.of(
                "version", graph.getVersion() == null ? 1 : graph.getVersion(),
                "nodes", nodes,
                "edges", graph.getEdges() == null ? List.of() : graph.getEdges());
    }

    private void appendEvent(Long runId, RuntimeEventType type, String payload) {
        eventPublisher.append(runId, type.wireName(), payload);
    }

    /**
     * 运行列表（契约 4.10，必须 user_id 过滤；traceId 精确匹配用于链路反查）。
     */
    public RunListData listRuns(Long userId, Long sessionId, String status, String traceId, Long agentId, int page, int size) {
        Map<Long, String> agentNames = agentNamesFor(userId);
        RunListData data = new RunListData();
        data.setTotal(runRepository.countByUser(userId, blankToNull(status), sessionId, blankToNull(traceId), agentId));
        data.setItems(runRepository.findByUser(userId, blankToNull(status), sessionId, blankToNull(traceId), agentId,
                        (page - 1) * size, size).stream()
                .map(r -> {
                    RunListItem item = new RunListItem();
                    item.setRunId(r.getId());
                    item.setAgentId(r.getAgentId());
                    item.setAgentName(r.getAgentId() == null ? null : agentNames.get(r.getAgentId()));
                    item.setStatus(r.getStatus());
                    item.setStrategy(r.getStrategy());
                    item.setErrorCode(r.getErrorCode());
                    item.setTraceId(r.getTraceId());
                    item.setLastSeq(r.getLastSeq());
                    item.setStartedAt(format(r.getStartedAt()));
                    item.setEndedAt(format(r.getEndedAt()));
                    item.setModelName(r.getModelName());
                    item.setInputTokens(r.getInputTokens());
                    item.setOutputTokens(r.getOutputTokens());
                    if (r.getStartedAt() != null && r.getEndedAt() != null) {
                        item.setLatencyMs(java.time.Duration.between(r.getStartedAt(), r.getEndedAt()).toMillis());
                    }
                    return item;
                })
                .toList());
        return data;
    }

    /**
     * Agent 名称映射（含平台公共），供运行列表 / 详情标注「用了哪个 Agent」。
     * agentId 为 null 表示发起时未选择（运行时取用户默认 = 第一个启用的 Agent）。
     */
    private Map<Long, String> agentNamesFor(Long userId) {
        Map<Long, String> names = new java.util.HashMap<>();
        agentConfigRepository.findPlatform().forEach(a -> names.put(a.getId(), a.getName()));
        agentConfigRepository.findByUser(userId).forEach(a -> names.put(a.getId(), a.getName()));
        return names;
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

    private AgentConfigEntity resolveAgent(Long userId, Long agentId) {
        AgentConfigEntity agent = agentId == null
                ? agentConfigService.firstEnabledForUser(userId)
                : agentConfigService.requireUsableForRun(userId, agentId);
        if (agent == null) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "没有可用的 Agent，请先在「我的 Agent」页创建");
        }
        return agent;
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
