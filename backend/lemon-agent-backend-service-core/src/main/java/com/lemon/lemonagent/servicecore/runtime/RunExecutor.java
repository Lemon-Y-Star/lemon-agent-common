package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.dal.entity.AgentMessageEntity;
import com.lemon.lemonagent.dal.entity.AgentRunEntity;
import com.lemon.lemonagent.dal.entity.ModelConnectionEntity;
import com.lemon.lemonagent.dal.repository.AgentMessageRepository;
import com.lemon.lemonagent.dal.repository.AgentRunRepository;
import com.lemon.lemonagent.dal.repository.ModelConnectionRepository;
import com.lemon.lemonagent.model.runtime.ModelPort;
import com.lemon.lemonagent.model.runtime.RunStatus;
import com.lemon.lemonagent.model.runtime.RuntimeEventType;
import com.lemon.lemonagent.util.crypto.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import tools.jackson.databind.ObjectMapper;

/**
 * 运行执行器：异步跑模型流式调用 + 手动工具循环（BUILD-SPEC §7.3）。
 *
 * <p><b>工具循环</b>：模型请求调用工具时（ModelPort.onToolCalls），逐个决策——
 * 在审批名单内 → 发 tool_call / approval_required 事件 → 写 pending_approval_json →
 * 状态机 RUNNING→BLOCKED → 本方法直接返回（审批跨 HTTP 请求存活，恢复走 resumeApproved）；
 * 不在名单内 → 直接执行 → tool_result → 结果作为合成上下文进入下一轮模型调用。</p>
 *
 * <p><b>恢复</b>：审批通过后 {@link #resumeApproved} 执行工具 → tool_result → 继续循环。
 * 上下文从 DB 重建（挂起时的中场发言已作为 ASSISTANT 消息落库），无内存依赖——
 * 这正是 pending_approval_json 设计成 DB 快照而非内存态的原因。</p>
 *
 * <p>token 攒批（40 字 / 200ms）与终态收口幂等语义同 v2.0；执行线程为 Java 21 虚拟线程。</p>
 */
@Component
public class RunExecutor {

    private static final Logger log = LoggerFactory.getLogger(RunExecutor.class);

    private static final int FLUSH_EVERY_CHARS = 40;
    private static final long FLUSH_EVERY_MILLIS = 200;
    private static final int HISTORY_WINDOW = 20;
    private static final int MAX_TOOL_ROUNDS = 5;

    private final AgentRunRepository runRepository;

    private final AgentMessageRepository messageRepository;

    private final ModelConnectionRepository modelConnectionRepository;

    private final ModelPort modelPort;

    private final EventPublisher eventPublisher;

    private final ToolRegistry toolRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String systemPrompt;

    private final long approvalTimeoutSeconds;

    /** runId → 取消标志（取消接口置位，onChunk 回调检查后中断模型流）。 */
    private final Map<Long, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    public RunExecutor(AgentRunRepository runRepository,
                       AgentMessageRepository messageRepository,
                       ModelConnectionRepository modelConnectionRepository,
                       ModelPort modelPort,
                       EventPublisher eventPublisher,
                       ToolRegistry toolRegistry,
                       @Value("${lemon.runtime.system-prompt:你是 LemonAgent 平台的智能助手，请用中文、简洁准确地回答用户的问题。你可以使用提供的工具完成任务；需要创建工单时调用 create_ticket 工具。}") String systemPrompt,
                       @Value("${lemon.runtime.approval-timeout-seconds:300}") long approvalTimeoutSeconds) {
        this.runRepository = runRepository;
        this.messageRepository = messageRepository;
        this.modelConnectionRepository = modelConnectionRepository;
        this.modelPort = modelPort;
        this.eventPublisher = eventPublisher;
        this.toolRegistry = toolRegistry;
        this.systemPrompt = systemPrompt;
        this.approvalTimeoutSeconds = approvalTimeoutSeconds;
    }

    /**
     * 异步执行运行（创建接口持有实体直接传入；本方法接管之后的全部状态变化）。
     *
     * @param run 入参：已创建（RUNNING）的运行实体
     */
    public void submit(AgentRunEntity run) {
        cancelFlags.put(run.getId(), new AtomicBoolean(false));
        Thread.startVirtualThread(() -> {
            try {
                runLoop(run, new ArrayList<>(), 0);
            } catch (Exception e) {
                log.error("运行执行异常 runId={}", run.getId(), e);
                failIfStillRunning(run.getId(), "MODEL_ERROR", "执行过程出现异常，请稍后重试。");
            } finally {
                cancelFlags.remove(run.getId());
            }
        });
    }

    /**
     * 审批通过后的恢复：执行工具 → tool_result → 继续循环（上下文从 DB 重建，无内存依赖）。
     *
     * @param run      入参：运行实体（已回到 RUNNING）
     * @param toolName 入参：待执行工具名
     * @param args     入参：审批快照里的工具入参
     */
    public void resumeApproved(AgentRunEntity run, String toolName, Map<String, Object> args) {
        cancelFlags.put(run.getId(), new AtomicBoolean(false));
        Thread.startVirtualThread(() -> {
            try {
                List<String> notes = new ArrayList<>();
                ToolRegistry.ToolHandler handler = toolRegistry.find(toolName);
                Map<String, Object> execArgs = new HashMap<>(args == null ? Map.of() : args);
                execArgs.put("__runId", run.getId());
                execArgs.put("__userId", run.getUserId());
                executeAndEmitToolResult(run, toolName, handler, execArgs, notes);
                runLoop(run, notes, 1);
            } catch (Exception e) {
                log.error("审批恢复执行异常 runId={}", run.getId(), e);
                failIfStillRunning(run.getId(), "MODEL_ERROR", "执行过程出现异常，请稍后重试。");
            } finally {
                cancelFlags.remove(run.getId());
            }
        });
    }

    /**
     * 请求取消（取消接口调用）：置位标志，模型流的下一个 chunk 回调会中断执行。
     *
     * @param runId 入参：运行 ID
     */
    public void requestCancel(Long runId) {
        AtomicBoolean flag = cancelFlags.get(runId);
        if (flag != null) {
            flag.set(true);
        }
    }

    /**
     * 工具循环主流程。
     *
     * @param run               入参：运行实体
     * @param continuationNotes 入参：本轮注入的合成上下文（工具执行结果说明），可空
     * @param depth             入参：工具轮次（防无限循环，上限 MAX_TOOL_ROUNDS）
     */
    private void runLoop(AgentRunEntity run, List<String> continuationNotes, int depth) {
        Long runId = run.getId();

        // ① 模型连接：用户最新启用的 BYOK 连接；没有则运行失败并指引配置
        ModelConnectionEntity conn = modelConnectionRepository.findByUser(run.getUserId()).stream()
                .filter(c -> c.getStatus() != null && c.getStatus() == 1)
                .reduce((first, second) -> second)
                .orElse(null);
        if (conn == null || conn.getApiKeyCipher() == null) {
            failIfStillRunning(runId, "MODEL_ERROR", "尚未配置启用的模型连接，请先在「模型连接」页添加并启用。");
            return;
        }

        // ② 会话历史：末条是本轮 USER 输入；其余为上下文（最近 20 条窗口）
        List<AgentMessageEntity> history = messageRepository.findBySession(run.getSessionId(), run.getUserId());
        if (history.isEmpty()) {
            failIfStillRunning(runId, "MODEL_ERROR", "会话内没有用户消息，无法发起运行。");
            return;
        }
        String userInput = history.get(history.size() - 1).getContent();
        List<ModelPort.Msg> context = new ArrayList<>();
        history.stream()
                .limit(Math.max(0, history.size() - 1))
                .skip(Math.max(0, history.size() - 1 - HISTORY_WINDOW))
                .forEach(m -> context.add(new ModelPort.Msg(m.getRole(), m.getContent())));
        // 工具结果作为合成上下文补充在历史之后（恢复路径的关键输入）
        continuationNotes.forEach(note -> context.add(new ModelPort.Msg("USER", "[系统] " + note)));

        String apiKey = CryptoUtil.decrypt(conn.getApiKeyCipher());
        List<ModelPort.ToolSpec> tools = depth < MAX_TOOL_ROUNDS ? toolRegistry.specs() : List.of();
        ModelPort.ModelRequest request = new ModelPort.ModelRequest(
                conn.getBaseUrl(), apiKey, resolveModel(conn),
                systemPrompt, context, userInput, tools);

        // ③ 流式执行 + token 攒批 + 工具调用拦截
        StringBuilder fullOutput = new StringBuilder();
        StringBuilder tokenBuf = new StringBuilder();
        long[] lastFlushAt = {System.currentTimeMillis()};
        long[] usage = new long[2];
        boolean[] completed = {false};
        List<ModelPort.ModelToolCall>[] toolCallsHolder = new List[]{null};
        AtomicBoolean cancelled = cancelFlags.get(runId);

        modelPort.stream(request, new ModelPort.Listener() {
            @Override
            public void onChunk(String delta) {
                if (cancelled != null && cancelled.get()) {
                    throw new IllegalStateException("RUN_CANCELLED");
                }
                fullOutput.append(delta);
                tokenBuf.append(delta);
                boolean byChars = tokenBuf.length() >= FLUSH_EVERY_CHARS;
                boolean byTime = System.currentTimeMillis() - lastFlushAt[0] >= FLUSH_EVERY_MILLIS;
                if (byChars || byTime) {
                    flushToken(runId, tokenBuf);
                    lastFlushAt[0] = System.currentTimeMillis();
                }
            }

            @Override
            public void onToolCalls(List<ModelPort.ModelToolCall> calls) {
                toolCallsHolder[0] = calls;
            }

            @Override
            public void onUsage(long inputTokens, long outputTokens) {
                usage[0] = inputTokens;
                usage[1] = outputTokens;
            }

            @Override
            public void onComplete() {
                completed[0] = true;
            }

            @Override
            public void onError(Throwable error) {
                log.debug("模型流错误 runId={}: {}", runId, error.getMessage());
            }
        });

        // ④ 分支：工具调用（挂起或直执行） vs 文本完成
        if (toolCallsHolder[0] != null) {
            flushToken(runId, tokenBuf);
            // 中场发言落库（模型调工具前输出的文本，恢复后仍在上下文里）
            if (!fullOutput.isEmpty()) {
                insertAssistantMessage(run, fullOutput.toString(), runId);
            }
            handleToolCalls(run, toolCallsHolder[0], continuationNotes, depth);
            return;
        }

        // ⑤ 终态收口：finish 先行（只对 RUNNING 生效的幂等闸），成功才补终态事件与 ASSISTANT 消息
        if (isTerminal(run)) {
            return;
        }
        String outputText = fullOutput.toString();
        if (completed[0]) {
            int rows = runRepository.finish(runId, RunStatus.COMPLETED.name(),
                    outputText, null, null, (int) usage[0], (int) usage[1]);
            if (rows > 0) {
                appendEvent(runId, RuntimeEventType.RUN_COMPLETED, objectMapper.writeValueAsString(Map.of(
                        "runId", runId,
                        "outputText", outputText,
                        "usage", Map.of("inputTokens", usage[0], "outputTokens", usage[1]),
                        "ts", System.currentTimeMillis())));
                insertAssistantMessage(run, outputText, runId);
            }
        } else {
            failIfStillRunning(runId, "MODEL_ERROR", "模型调用失败，请检查模型配置。");
        }
    }

    /** 工具调用决策：名单内挂起等审批，名单外直接执行进下一轮。 */
    private void handleToolCalls(AgentRunEntity run, List<ModelPort.ModelToolCall> calls,
                                 List<String> continuationNotes, int depth) {
        Long runId = run.getId();
        for (ModelPort.ModelToolCall call : calls) {
            ToolRegistry.ToolHandler handler = toolRegistry.find(call.name());
            if (handler == null) {
                // 模型幻觉出的工具名：以失败结果回注，让模型自行纠正
                continuationNotes.add("工具 " + call.name() + " 不存在，请改用其他方式或直接回答用户。");
                continue;
            }
            appendEvent(runId, RuntimeEventType.TOOL_CALL, objectMapper.writeValueAsString(Map.of(
                    "callId", call.id() == null ? UUID.randomUUID().toString() : call.id(),
                    "toolName", call.name(),
                    "args", parseArgs(call.arguments()),
                    "riskLevel", handler.needsApproval ? "HIGH" : "LOW",
                    "stepIndex", 0,
                    "ts", System.currentTimeMillis())));

            if (handler.needsApproval) {
                suspendForApproval(run, call, handler);
                return; // BLOCKED：等审批，恢复走 resumeApproved
            }

            Map<String, Object> execArgs = new HashMap<>(parseArgs(call.arguments()));
            execArgs.put("__runId", run.getId());
            execArgs.put("__userId", run.getUserId());
            executeAndEmitToolResult(run, call.name(), handler, execArgs, continuationNotes);
        }
        // 全部工具直执行完毕，继续下一轮模型调用
        runLoop(run, continuationNotes, depth + 1);
    }

    /** 挂起：RUNNING→BLOCKED + 审批快照（同一条 UPDATE 原子完成）+ approval_required 事件。 */
    private void suspendForApproval(AgentRunEntity run, ModelPort.ModelToolCall call,
                                    ToolRegistry.ToolHandler handler) {
        long expiresAt = System.currentTimeMillis() + approvalTimeoutSeconds * 1000L;
        String approvalId = "ap_" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> args = parseArgs(call.arguments());
        String pendingJson = objectMapper.writeValueAsString(Map.of(
                "kind", "TOOL",
                "approvalId", approvalId,
                "callId", call.id() == null ? "" : call.id(),
                "toolName", call.name(),
                "args", args,
                "riskLevel", "HIGH",
                "expiresAt", expiresAt));
        int rows = runRepository.suspend(run.getId(), approvalId, pendingJson);
        if (rows == 0) {
            // 并发窗口里状态已变（取消）：放弃挂起
            return;
        }
        appendEvent(run.getId(), RuntimeEventType.APPROVAL_REQUIRED, objectMapper.writeValueAsString(Map.of(
                "approvalId", approvalId,
                "kind", "TOOL",
                "callId", call.id() == null ? "" : call.id(),
                "toolName", call.name(),
                "args", args,
                "riskLevel", "HIGH",
                "expiresAt", expiresAt,
                "ts", System.currentTimeMillis())));
        log.info("运行挂起等待审批 runId:{} tool:{} approvalId:{}", run.getId(), call.name(), approvalId);
    }

    /** 执行工具（写操作在此真正落库）并发布 tool_result 事件，结果以合成上下文进入下一轮。 */
    private void executeAndEmitToolResult(AgentRunEntity run, String toolName,
                                          ToolRegistry.ToolHandler handler,
                                          Map<String, Object> args, List<String> notes) {
        long start = System.currentTimeMillis();
        String resultBrief;
        boolean ok;
        try {
            Map<String, Object> result = handler.execute(args);
            ok = true;
            resultBrief = result.values().stream().map(String::valueOf)
                    .reduce((a, b) -> a + " / " + b).orElse("done");
            notes.add("工具 " + toolName + " 已执行成功，结果：" + objectMapper.writeValueAsString(result)
                    + "。请基于该结果继续完成用户的任务。");
        } catch (Exception e) {
            ok = false;
            resultBrief = "执行失败：" + e.getMessage();
            notes.add("工具 " + toolName + " 执行失败：" + e.getMessage() + "。请告知用户并尝试其他方式。");
        }
        appendEvent(run.getId(), RuntimeEventType.TOOL_RESULT, objectMapper.writeValueAsString(Map.of(
                "callId", String.valueOf(args.getOrDefault("__callId", "")),
                "ok", ok,
                "durationMs", System.currentTimeMillis() - start,
                "resultBrief", resultBrief,
                "resultChars", resultBrief.length(),
                "ts", System.currentTimeMillis())));
    }

    private void insertAssistantMessage(AgentRunEntity run, String content, Long runId) {
        AgentMessageEntity assistant = new AgentMessageEntity();
        assistant.setSessionId(run.getSessionId());
        assistant.setUserId(run.getUserId());
        assistant.setRole("ASSISTANT");
        assistant.setContent(content);
        assistant.setRunId(runId);
        messageRepository.insert(assistant);
    }

    private void flushToken(Long runId, StringBuilder buf) {
        if (buf.isEmpty()) {
            return;
        }
        appendEvent(runId, RuntimeEventType.TOKEN, objectMapper.writeValueAsString(Map.of(
                "delta", buf.toString(),
                "index", 0,
                "stepIndex", 0)));
        buf.setLength(0);
    }

    private void failIfStillRunning(Long runId, String errorCode, String errorMsg) {
        int rows = runRepository.finish(runId, RunStatus.FAILED.name(), null, errorCode, errorMsg, null, null);
        if (rows > 0) {
            appendEvent(runId, RuntimeEventType.RUN_FAILED, objectMapper.writeValueAsString(Map.of(
                    "runId", runId,
                    "errorCode", errorCode,
                    "errorMsg", errorMsg,
                    "ts", System.currentTimeMillis())));
        }
    }

    private boolean isTerminal(AgentRunEntity run) {
        AgentRunEntity current = runRepository.findByIdAndUser(run.getId(), run.getUserId());
        return current != null && RunStatus.valueOf(current.getStatus()).terminal();
    }

    private void appendEvent(Long runId, RuntimeEventType type, String payload) {
        eventPublisher.append(runId, type.wireName(), payload);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(argumentsJson, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String resolveModel(ModelConnectionEntity conn) {
        // v1 连接的 models_json 是 JSON 数组；运行时取第一个可用模型（节点级选择在 v3 落地）
        String json = conn.getModelsJson();
        if (json != null && json.startsWith("[\"") && json.length() > 3) {
            String first = json.substring(2, json.indexOf('"', 2));
            if (!first.isBlank()) {
                return first;
            }
        }
        return "gpt-4o-mini";
    }

}
