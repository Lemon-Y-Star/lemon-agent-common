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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import tools.jackson.databind.ObjectMapper;

/**
 * 运行执行器：异步跑一次模型流式调用，把模型增量转成事件流。
 *
 * <p>三件事：
 * ① 上下文组装——取用户最新启用的个人模型连接（BYOK，解密 apiKey）+ 会话历史（末条为本轮输入）；
 * ② token 攒批——「40 字符 / 200ms」双阈值合并落库（BUILD-SPEC §5.5），推送粒度与持久化粒度解耦；
 * ③ 终态收口——finish 先行（幂等闸：只对 RUNNING 生效），成功才补终态事件与 ASSISTANT 消息；
 * 取消通过取消标志在 onChunk 回调里抛出中断模型流，终态由取消接口负责、这里识别后让路。</p>
 *
 * <p>执行线程用 Java 21 虚拟线程：单次运行的生命周期就是一次阻塞式模型流，虚拟线程天然匹配。</p>
 */
@Component
public class RunExecutor {

    private static final Logger log = LoggerFactory.getLogger(RunExecutor.class);

    private static final int FLUSH_EVERY_CHARS = 40;
    private static final long FLUSH_EVERY_MILLIS = 200;
    private static final int HISTORY_WINDOW = 20;

    private final AgentRunRepository runRepository;

    private final AgentMessageRepository messageRepository;

    private final ModelConnectionRepository modelConnectionRepository;

    private final ModelPort modelPort;

    private final EventPublisher eventPublisher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 系统提示词（yml 可覆盖）。 */
    private final String systemPrompt;

    /** runId → 取消标志（取消接口置位，onChunk 回调检查后中断模型流）。 */
    private final Map<Long, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    public RunExecutor(AgentRunRepository runRepository,
                       AgentMessageRepository messageRepository,
                       ModelConnectionRepository modelConnectionRepository,
                       ModelPort modelPort,
                       EventPublisher eventPublisher,
                       @Value("${lemon.runtime.system-prompt:你是 LemonAgent 平台的智能助手，请用中文、简洁准确地回答用户的问题。}") String systemPrompt) {
        this.runRepository = runRepository;
        this.messageRepository = messageRepository;
        this.modelConnectionRepository = modelConnectionRepository;
        this.modelPort = modelPort;
        this.eventPublisher = eventPublisher;
        this.systemPrompt = systemPrompt;
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
                execute(run);
            } catch (Exception e) {
                log.error("运行执行异常 runId={}", run.getId(), e);
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

    private void execute(AgentRunEntity run) {
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

        String apiKey = CryptoUtil.decrypt(conn.getApiKeyCipher());
        ModelPort.ModelRequest request = new ModelPort.ModelRequest(
                conn.getBaseUrl(), apiKey, resolveModel(conn),
                systemPrompt, context, userInput);

        // ③ 流式执行 + token 攒批（listener 回调保证单线程串行）
        StringBuilder tokenBuf = new StringBuilder();
        long[] lastFlushAt = {System.currentTimeMillis()};
        long[] usage = new long[2];
        boolean[] completed = {false};
        AtomicBoolean cancelled = cancelFlags.get(runId);

        modelPort.stream(request, new ModelPort.Listener() {
            @Override
            public void onChunk(String delta) {
                if (cancelled != null && cancelled.get()) {
                    // 中断模型流；终态与 run_cancelled 事件由取消接口负责
                    throw new IllegalStateException("RUN_CANCELLED");
                }
                tokenBuf.append(delta);
                boolean byChars = tokenBuf.length() >= FLUSH_EVERY_CHARS;
                boolean byTime = System.currentTimeMillis() - lastFlushAt[0] >= FLUSH_EVERY_MILLIS;
                if (byChars || byTime) {
                    flushToken(runId, tokenBuf);
                    lastFlushAt[0] = System.currentTimeMillis();
                }
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
                // 统一在 stream() 返回后的状态收口处理，这里只记录
                log.debug("模型流错误 runId={}: {}", runId, error.getMessage());
            }
        });

        // ④ 终态收口：finish 先行（只对 RUNNING 生效的幂等闸），成功才补终态事件与 ASSISTANT 消息
        if (isTerminal(run)) {
            return;
        }
        String outputText = tokenBuf.toString();
        if (completed[0]) {
            int rows = runRepository.finish(runId, RunStatus.COMPLETED.name(),
                    outputText, null, null, (int) usage[0], (int) usage[1]);
            if (rows > 0) {
                appendEvent(runId, RuntimeEventType.RUN_COMPLETED, objectMapper.writeValueAsString(Map.of(
                        "runId", runId,
                        "outputText", outputText,
                        "usage", Map.of("inputTokens", usage[0], "outputTokens", usage[1]),
                        "ts", System.currentTimeMillis())));
                AgentMessageEntity assistant = new AgentMessageEntity();
                assistant.setSessionId(run.getSessionId());
                assistant.setUserId(run.getUserId());
                assistant.setRole("ASSISTANT");
                assistant.setContent(outputText);
                assistant.setRunId(runId);
                messageRepository.insert(assistant);
            }
        } else {
            failIfStillRunning(runId, "MODEL_ERROR", "模型调用失败，请检查模型配置。");
        }
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
