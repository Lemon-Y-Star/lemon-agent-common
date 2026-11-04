package com.lemon.lemonagent.servicecore.runtime.engine;

import com.lemon.lemonagent.dal.entity.AgentConfigEntity;
import com.lemon.lemonagent.dal.entity.AgentMessageEntity;
import com.lemon.lemonagent.dal.entity.AgentRunEntity;
import com.lemon.lemonagent.dal.entity.ModelConnectionEntity;
import com.lemon.lemonagent.dal.repository.AgentConfigRepository;
import com.lemon.lemonagent.dal.repository.AgentMessageRepository;
import com.lemon.lemonagent.dal.repository.AgentRunRepository;
import com.lemon.lemonagent.dal.repository.ModelConnectionRepository;
import com.lemon.lemonagent.model.runtime.AgentGraphData;
import com.lemon.lemonagent.model.runtime.CodeSandboxPort;
import com.lemon.lemonagent.model.runtime.GraphEdge;
import com.lemon.lemonagent.model.runtime.GraphNode;
import com.lemon.lemonagent.model.runtime.GraphStateData;
import com.lemon.lemonagent.model.runtime.ModelPort;
import com.lemon.lemonagent.model.runtime.PlanStepData;
import com.lemon.lemonagent.model.runtime.Strategy;
import com.lemon.lemonagent.model.runtime.RunStatus;
import com.lemon.lemonagent.model.runtime.RuntimeEventType;
import com.lemon.lemonagent.util.crypto.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
import com.lemon.lemonagent.servicecore.runtime.graph.GraphCodec;
import com.lemon.lemonagent.servicecore.runtime.graph.RoutePredicates;
import com.lemon.lemonagent.servicecore.runtime.agent.AgentConfigService;
import com.lemon.lemonagent.servicecore.runtime.agent.AgentToolCodec;
import com.lemon.lemonagent.servicecore.runtime.tool.ToolRegistry;
import com.lemon.lemonagent.servicecore.runtime.memory.MemoryService;
import com.lemon.lemonagent.servicecore.runtime.artifact.RunArtifactService;

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
    private static final long FLUSH_EVERY_MILLIS = 200;    /** 短期记忆默认窗口：10 轮（1 轮 = 用户 + 助手各 1 条），Agent 未配置时的兜底。 */
    private static final int DEFAULT_HISTORY_TURNS = 10;
    /** 短期记忆轮数上限：与 AgentConfigService 同口径，防 prompt 无界膨胀。 */
    private static final int MAX_HISTORY_TURNS = 50;
    /** 工具轮次上限（v4.5.9 5→8）：主 Agent 协同场景 2 个子工作流 × (发起+跟进) 就要 4 轮，
     *  叠加自主知识库检索 5 轮必爆——模型被迫中途收尾，产出缺子工作流细节。 */
    private static final int MAX_TOOL_ROUNDS = 8;

    /** 工具轮次用尽后的提示（run158 实测：不明说，DeepSeek 会把调用标记当正文吐出来）。 */
    private static final String TOOL_CUT_NOTE = "[系统] 工具调用轮次已达上限，无法再调用任何工具。"
            + "请直接基于以上已获得的工具结果给出最终回答：不要发起工具调用，也不要输出任何调用标记或代码围栏外的东西。";

    /** 计划步骤上限（plan 策略）：再多就不是「计划」而是流水账，且每次运行的成本线性增长。 */
    private static final int MAX_PLAN_STEPS = 6;

    /** 深度思考（CoT）指令：推理放进 <think> 标签（用户可见、流式展开），标签后是最终回答。 */
    private static final String THINKING_INSTRUCTION =
            "【深度思考模式】先用 <think> 与 </think> 标签包裹你的逐步推理（这部分用户可见，"
                    + "推理要简洁、控制在 300 字以内、不要使用 markdown 标题），"
                    + "标签结束后另起一行输出最终回答；最终回答要完整、可直接使用。";

    /** 规划阶段追加到 Agent 人格之后的约束：只输出 JSON，便于解析。 */
    /**
     * 规划阶段专用 system prompt：<b>不掺 Agent 人格</b>——人格里常带"调用 xx 工具"的话术，
     * 会诱导规划器写成散文或提前替用户干活。这里只要一件事：按 JSON 给步骤。
     */
    private static final String PLANNER_PROMPT = "你是任务规划器。把用户任务拆成 2..5 个可执行步骤，"
            + "每步一句话、动词开头、彼此不重复、各自能独立完成。"
            + "只输出一个 JSON 对象：第一个字符是 {，最后一个字符是 }；不要标题、不要编号列表、不要解释、"
            + "不要 Markdown 代码块。格式："
            + "{\"steps\":[{\"title\":\"查询服务器当前时间\",\"detail\":\"调用时间工具\"},"
            + "{\"title\":\"判断上午还是下午\",\"detail\":\"根据小时数下结论\"}]}";

    private final AgentRunRepository runRepository;

    private final AgentMessageRepository messageRepository;

    private final ModelConnectionRepository modelConnectionRepository;

    /** Agent 配置服务（v4.5.2：反查 Agent 知识库绑定范围）。 */
    private final com.lemon.lemonagent.servicecore.runtime.agent.AgentConfigService agentConfigService;

    private final ModelPort modelPort;

    private final EventPublisher eventPublisher;

    private final ToolRegistry toolRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String systemPrompt;

    private final long approvalTimeoutSeconds;

    /** 执行图并行度（同层无依赖节点的并发上限；1 = 串行）。 */
    private final int graphParallelism;

    /** runId → 取消标志（取消接口置位，onChunk 回调检查后中断模型流）。 */
    private final Map<Long, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    /** 附图已消耗的 runId 集合（v4.9 多模态）：图只发给 run 的首个 LLM 调用，防工具循环/并行节点重发。 */
    private final java.util.Set<Long> imagesConsumed = ConcurrentHashMap.newKeySet();

    /** Agent 配置仓储：每轮循环按 run.agent_id 回查，挂起恢复后语义不变（无内存态依赖）。 */
    private final AgentConfigRepository agentConfigRepository;

    /** 长期记忆：拼进 system prompt。 */
    private final MemoryService memoryService;

    /** 代码沙箱端口（可空：沙箱服务缺席时代码节点/工具报明确错误）。 */
    private final CodeSandboxPort codeSandbox;

    /** 运行文件产物（v4.4.4）：沙箱生成的文件落库。 */
    private final RunArtifactService artifactService;

    private final com.lemon.lemonagent.servicecore.safety.SensitiveWordService sensitiveWords;

    public RunExecutor(AgentRunRepository runRepository,
                       AgentMessageRepository messageRepository,
                       ModelConnectionRepository modelConnectionRepository,
                       com.lemon.lemonagent.servicecore.runtime.agent.AgentConfigService agentConfigService,
                       AgentConfigRepository agentConfigRepository,
                       MemoryService memoryService,
                       ObjectProvider<CodeSandboxPort> codeSandboxProvider,
                       RunArtifactService artifactService,
                       com.lemon.lemonagent.servicecore.safety.SensitiveWordService sensitiveWords,
                       ModelPort modelPort,
                       EventPublisher eventPublisher,
                       ToolRegistry toolRegistry,
                       @Value("${lemon.runtime.system-prompt:你是 LemonAgent 平台的智能助手，请用中文、简洁准确地回答用户的问题。你可以使用提供的工具完成任务；需要创建工单时调用 create_ticket 工具；涉及平台知识的问题先调用 search_docs 检索。当 search_docs 返回的 hits 为空时，必须直说文档中没有相关内容，不得基于自身知识编造。}") String systemPrompt,
                       @Value("${lemon.runtime.approval-timeout-seconds:300}") long approvalTimeoutSeconds,
                       @Value("${lemon.graph.parallelism:2}") int graphParallelism) {
        this.runRepository = runRepository;
        this.messageRepository = messageRepository;
        this.modelConnectionRepository = modelConnectionRepository;
        this.agentConfigService = agentConfigService;
        this.agentConfigRepository = agentConfigRepository;
        this.memoryService = memoryService;
        this.codeSandbox = codeSandboxProvider.getIfAvailable();
        this.artifactService = artifactService;
        this.sensitiveWords = sensitiveWords;
        this.modelPort = modelPort;
        this.eventPublisher = eventPublisher;
        this.toolRegistry = toolRegistry;
        this.systemPrompt = systemPrompt;
        this.approvalTimeoutSeconds = approvalTimeoutSeconds;
        this.graphParallelism = graphParallelism;
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
        // plan-execute：先规划再逐步执行；工具审批恢复后也回到这里，按库里的 plan_step 接着跑
        if (Strategy.PLAN.wireName().equals(run.getStrategy())) {
            planLoop(run, continuationNotes, depth);
            return;
        }
        // 执行图（custom）：按拓扑序节点执行；节点审批恢复后同样回到这里，按 done 集合续跑
        if (Strategy.CUSTOM.wireName().equals(run.getStrategy())) {
            graphLoop(run, continuationNotes, depth);
            return;
        }
        Long runId = run.getId();
        AgentConfigEntity agent = agentOf(run);
        List<String> allowedTools = allowedToolsOf(agent);
        // fixed（直接问答）：不装配任何工具、单轮生成；人格与长期记忆注入保持一致——限的是能力，不是人格
        boolean fixed = Strategy.FIXED.wireName().equals(run.getStrategy());

        ModelConnectionEntity conn = resolveConnection(run, agent);
        if (conn == null || conn.getApiKeyCipher() == null) {
            failIfStillRunning(runId, "MODEL_ERROR", "尚未配置启用的模型连接，请先在「模型连接」页添加并启用。");
            return;
        }
        List<ModelPort.Msg> context = historyContext(run, agent);
        String userInput = lastUserInput(run);
        if (context == null || userInput == null) {
            failIfStillRunning(runId, "MODEL_ERROR", "会话内没有用户消息，无法发起运行。");
            return;
        }
        // 工具结果作为合成上下文补充在历史之后（恢复路径的关键输入）
        continuationNotes.forEach(note -> context.add(new ModelPort.Msg("USER", "[系统] " + note)));

        List<ModelPort.ToolSpec> tools = !fixed && depth < MAX_TOOL_ROUNDS
                ? toolRegistry.specs(allowedTools) : List.of();
        if (!fixed && depth >= MAX_TOOL_ROUNDS) {
            // 轮次上限后砍掉工具：必须明说，否则模型（如 DeepSeek）会把调用标记当文本吐出来（run158 实测）
            context.add(new ModelPort.Msg("USER", TOOL_CUT_NOTE));
        }
        // 深度思考（CoT）：auto / fixed 均支持（plan / custom 有各自的可见进度）。
        // 指令同时写 system 与用户消息——部分中转渠道在带 tools 的请求里会丢弃 system 消息（v3.0 已知缺陷），
        // 只写 system 会让 CoT 在 auto 模式静默失效；落库的 USER 消息保持原始输入不受影响。
        boolean thinkingOn = run.getThinking() != null && run.getThinking() == 1;
        String systemPrompt = instructionOf(agent, run.getUserId());
        if (thinkingOn) {
            systemPrompt = systemPrompt + "\n\n" + THINKING_INSTRUCTION;
        }
        String modelInput = thinkingOn
                ? "[系统指令]" + THINKING_INSTRUCTION + "\n\n用户问题：" + userInput
                : userInput;
        TurnResult turn = streamTurnResilient(run, agent, systemPrompt, context, modelInput, tools, 0, true);

        // ④ 分支：工具调用（挂起或直执行） vs 文本完成；fixed 模式没有装配工具，模型报 tool_calls 属异常，按文本收口
        if (turn.toolCalls() != null && !fixed) {
            // 中场发言落库（模型调工具前输出的文本，恢复后仍在上下文里）。
            // 剥思考块后为空（这轮只思考+调工具）就不落库——否则历史里出现一串「无正文」气泡（run164 实测）
            String midText = stripThink(turn.text());
            if (!midText.isBlank()) {
                insertAssistantMessage(run, midText, runId);
            }
            handleToolCalls(run, turn.toolCalls(), continuationNotes, depth, allowedTools);
            return;
        }

        // ⑤ 终态收口：finish 先行（只对 RUNNING 生效的幂等闸），成功才补终态事件与 ASSISTANT 消息
        if (isTerminal(run)) {
            return;
        }
        String outputText = maskOutput(turn.text());
        if (fixed && outputText.isEmpty() && turn.toolCalls() != null) {
            outputText = "（fixed 模式不装配工具，模型请求的工具调用已被忽略）";
        }
        if (turn.completed()) {
            int rows = runRepository.finish(runId, RunStatus.COMPLETED.name(),
                    outputText, null, null, (int) turn.inputTokens(), (int) turn.outputTokens());
            if (rows > 0) {
                appendEvent(runId, RuntimeEventType.RUN_COMPLETED, objectMapper.writeValueAsString(Map.of(
                        "runId", runId,
                        "outputText", outputText,
                        "usage", Map.of("inputTokens", turn.inputTokens(), "outputTokens", turn.outputTokens()),
                        "traceId", run.getTraceId() == null ? "" : run.getTraceId(),
                        "durationMs", durationMs(run),
                        "ts", System.currentTimeMillis())));
                insertAssistantMessage(run, outputText, runId);
            }
        } else {
            failIfStillRunning(runId, "MODEL_ERROR", "模型调用失败，请检查模型配置。");
        }
    }

    /**
     * plan-execute 主流程（v3.1）：没有计划就先规划（一次模型调用，不给工具），然后逐步执行。
     *
     * <p><b>无内存态</b>：计划与步骤游标都落在 {@code agent_run.plan_json / plan_step}，
     * 工具审批挂起后 {@code resumeApproved} 重新进入本方法，从当前步骤继续；进程重启同理。</p>
     *
     * @param run   入参：运行实体（含 strategy=plan）
     * @param notes 入参：工具结果等合成上下文（审批恢复时非空 = 当前步骤重跑）
     * @param depth 入参：工具轮次
     */
    private void planLoop(AgentRunEntity run, List<String> notes, int depth) {
        Long runId = run.getId();
        AgentConfigEntity agent = agentOf(run);
        List<String> allowedTools = allowedToolsOf(agent);
        ModelConnectionEntity conn = resolveConnection(run, agent);
        if (conn == null || conn.getApiKeyCipher() == null) {
            failIfStillRunning(runId, "MODEL_ERROR", "尚未配置启用的模型连接，请先在「模型连接」页添加并启用。");
            return;
        }
        String userInput = lastUserInput(run);
        if (userInput == null) {
            failIfStillRunning(runId, "MODEL_ERROR", "会话内没有用户消息，无法发起运行。");
            return;
        }

        // ① 计划：库里没有就先规划（规划只读文本，不给工具——顺带绕开「带 tools 丢 system」的渠道差异）
        List<PlanStepData> plan = PlanCodec.decode(run.getPlanJson());
        if (plan == null) {
            plan = createPlan(run, conn, agent, userInput);
            if (plan == null) {
                return;
            }
            run.setPlanJson(PlanCodec.encode(plan));
            run.setPlanStep(0);
            runRepository.savePlan(runId, run.getPlanJson(), 0);
            List<Map<String, Object>> brief = new java.util.ArrayList<>();
            for (PlanStepData step : plan) {
                brief.add(Map.of("index", step.getIndex(), "title", step.getTitle()));
            }
            appendEvent(runId, RuntimeEventType.PLAN_CREATED, objectMapper.writeValueAsString(Map.of(
                    "steps", brief,
                    "total", plan.size(),
                    "ts", System.currentTimeMillis())));
            log.info("run {} 计划已生成，共 {} 步：{}", runId, plan.size(),
                    plan.stream().map(PlanStepData::getTitle).toList());
        }

        int total = plan.size();
        int stepIndex = run.getPlanStep() == null ? 0 : run.getPlanStep();

        // ② 逐步执行：每步一次模型调用；工具调用走统一决策（可能挂起，恢复后回到本方法）
        for (; stepIndex < total; stepIndex++) {
            if (isTerminal(run)) {
                return;
            }
            PlanStepData step = plan.get(stepIndex);
            boolean resumedStep = !notes.isEmpty();
            if (!resumedStep) {
                appendEvent(runId, RuntimeEventType.STEP_STARTED, objectMapper.writeValueAsString(Map.of(
                        "stepIndex", stepIndex,
                        "title", step.getTitle(),
                        "total", total,
                        "ts", System.currentTimeMillis())));
            }
            step.setStatus("RUNNING");

            List<ModelPort.Msg> context = historyContext(run, agent);
            if (context == null) {
                failIfStillRunning(runId, "MODEL_ERROR", "会话内没有用户消息，无法发起运行。");
                return;
            }
            // 已完成步骤的产出作为上下文（不落消息表，避免计划中间产物污染后续对话）
            for (int i = 0; i < stepIndex; i++) {
                PlanStepData done = plan.get(i);
                context.add(new ModelPort.Msg("ASSISTANT",
                        "[第" + (i + 1) + "步：" + done.getTitle() + "] " + (done.getOutput() == null ? "" : done.getOutput())));
            }
            notes.forEach(note -> context.add(new ModelPort.Msg("USER", "[系统] " + note)));

            String prompt = stepPrompt(instructionOf(agent, run.getUserId()), step, stepIndex, total);
            String stepInput = stepInput(step, stepIndex, userInput);
            List<ModelPort.ToolSpec> tools = depth < MAX_TOOL_ROUNDS
                    ? toolRegistry.specs(allowedTools) : List.of();
            if (depth >= MAX_TOOL_ROUNDS) {
                context.add(new ModelPort.Msg("USER", TOOL_CUT_NOTE));
            }
            TurnResult turn = streamTurnResilient(run, agent, prompt, context, stepInput, tools, stepIndex, true);

            if (turn.toolCalls() != null) {
                // 工具调用：挂起（BLOCKED）或直接执行后重新进入（runLoop 会回到 planLoop 同一 step）
                handleToolCalls(run, turn.toolCalls(), notes, depth, allowedTools);
                return;
            }
            if (!turn.completed()) {
                failIfStillRunning(runId, "MODEL_ERROR", "模型调用失败，请检查模型配置。");
                return;
            }

            // ③ 步骤完成：计划快照（含产出与用量）+ 游标一起落库，再发事件
            step.setStatus("DONE");
            step.setOutput(turn.text());
            step.setInputTokens(turn.inputTokens());
            step.setOutputTokens(turn.outputTokens());
            run.setPlanJson(PlanCodec.encode(plan));
            run.setPlanStep(stepIndex + 1);
            runRepository.savePlan(runId, run.getPlanJson(), stepIndex + 1);
            appendEvent(runId, RuntimeEventType.STEP_COMPLETED, objectMapper.writeValueAsString(Map.of(
                    "stepIndex", stepIndex,
                    "title", step.getTitle(),
                    "outputText", turn.text(),
                    "usage", Map.of("inputTokens", turn.inputTokens(), "outputTokens", turn.outputTokens()),
                    "ts", System.currentTimeMillis())));
            notes.clear();
            log.info("run {} 第 {}/{} 步完成：{}", runId, stepIndex + 1, total, step.getTitle());
        }

        // ④ 汇总收口：正文 = 各步骤产出拼装（run_completed.outputText 仍是权威正文）
        long totalIn = 0;
        long totalOut = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < total; i++) {
            PlanStepData step = plan.get(i);
            totalIn += step.getInputTokens();
            totalOut += step.getOutputTokens();
            sb.append("### 第").append(i + 1).append("步：").append(step.getTitle()).append("\n")
                    .append(step.getOutput() == null ? "" : step.getOutput()).append("\n\n");
        }
        String outputText = maskOutput(sb.toString().trim());
        int rows = runRepository.finish(runId, RunStatus.COMPLETED.name(),
                outputText, null, null, (int) totalIn, (int) totalOut);
        if (rows > 0) {
            appendEvent(runId, RuntimeEventType.RUN_COMPLETED, objectMapper.writeValueAsString(Map.of(
                    "runId", runId,
                    "outputText", outputText,
                    "usage", Map.of("inputTokens", totalIn, "outputTokens", totalOut),
                    "traceId", run.getTraceId() == null ? "" : run.getTraceId(),
                    "durationMs", durationMs(run),
                    "ts", System.currentTimeMillis())));
            insertAssistantMessage(run, outputText, runId);
        }
    }

    /**
     * 规划：一次模型调用产出 2..5 步计划。
     *
     * @return 出参：步骤列表；解析失败退化成「单步完成用户请求」，模型调用失败返回 null（调用方已收口）
     */
    private List<PlanStepData> createPlan(AgentRunEntity run, ModelConnectionEntity conn,
                                          AgentConfigEntity agent, String userInput) {
        List<ModelPort.Msg> context = historyContext(run, agent);
        if (context == null) {
            context = new ArrayList<>();
        }
        String toolsNote = allowedToolsOf(agent) == null ? "全部工具" : String.valueOf(allowedToolsOf(agent));
        TurnResult turn = streamTurnResilient(run, agent, PLANNER_PROMPT, context,
                "用户任务：" + userInput + "\n可用工具：" + toolsNote + "\n请给出执行计划。",
                List.of(), -1, false);
        if (!turn.completed() || turn.toolCalls() != null) {
            failIfStillRunning(run.getId(), "MODEL_ERROR", "规划阶段模型调用失败，请重试或改用 auto 策略。");
            return null;
        }
        List<PlanStepData> plan = parsePlan(turn.text(), userInput);
        if (plan == null) {
            return null;
        }
        return plan;
    }

    /** 宽松解析计划 JSON：剥代码块 → 取首个 {..} → 读 steps；失败返回单步兜底（永不让运行失败）。 */
    private List<PlanStepData> parsePlan(String raw, String userInput) {
        List<PlanStepData> steps = new ArrayList<>();
        try {
            String json = raw == null ? "" : raw.trim();
            int begin = json.indexOf('{');
            int end = json.lastIndexOf('}');
            if (begin >= 0 && end > begin) {
                json = json.substring(begin, end + 1);
            }
            Map<String, Object> root = objectMapper.readValue(json, Map.class);
            Object rawSteps = root.get("steps");
            if (rawSteps instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> map)) {
                        continue;
                    }
                    Object title = map.get("title");
                    if (title == null || String.valueOf(title).isBlank()) {
                        continue;
                    }
                    PlanStepData step = new PlanStepData();
                    step.setIndex(steps.size());
                    step.setTitle(String.valueOf(title).trim());
                    Object detail = map.get("detail");
                    step.setDetail(detail == null ? "" : String.valueOf(detail).trim());
                    step.setStatus("PENDING");
                    step.setOutput("");
                    steps.add(step);
                    if (steps.size() >= MAX_PLAN_STEPS) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("计划 JSON 解析失败，尝试按行解析：{}", e.getMessage());
        }
        if (steps.isEmpty()) {
            // 模型没按 JSON 输出时，退一步按「1. xxx / - xxx」的行结构解析（比直接单步兜底可用得多）
            steps = parsePlanLines(raw);
        }
        if (steps.isEmpty()) {
            // 兜底：模型没按格式给计划时，把整件事当一个步骤，保证运行不失败
            PlanStepData fallback = new PlanStepData();
            fallback.setIndex(0);
            fallback.setTitle("完成用户请求");
            fallback.setDetail(userInput == null ? "" : userInput);
            fallback.setStatus("PENDING");
            fallback.setOutput("");
            steps.add(fallback);
        }
        return steps;
    }

    /**
     * 按行解析计划（JSON 失败时的兜底）：识别「1. / 1) / 1、/ - / * / • / Step 1:」等前缀，
     * 去 Markdown 强调符号；标题超长截断。返回空列表表示没解析出可用步骤。
     */
    private List<PlanStepData> parsePlanLines(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        List<String> numbered = new ArrayList<>();
        List<String> bulleted = new ArrayList<>();
        for (String line : raw.split("\\r?\\n")) {
            String text = line.trim();
            if (text.isEmpty() || text.startsWith("#") || text.startsWith("```")) {
                continue;
            }
            if (text.matches("^\\d+[.、)]\\s*.*")) {
                numbered.add(text.replaceFirst("^\\d+[.、)]\\s*", ""));
            } else if (text.matches("^[-*•]\\s*.*")) {
                bulleted.add(text.replaceFirst("^[-*•]\\s*", ""));
            }
        }
        // 有编号就用编号（散文里的补充说明通常不带编号）；否则退到项目符号
        List<String> picked = numbered.size() >= 2 ? numbered : (bulleted.size() >= 2 ? bulleted : List.of());
        List<PlanStepData> steps = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (String rawItem : picked) {
            String cleaned = rawItem.replace("**", "").replace("__", "").replace("`", "").trim();
            // 过滤条件句、说明句、以冒号结尾的小标题——这些不是"可执行步骤"
            if (cleaned.length() < 4 || cleaned.endsWith("：") || cleaned.endsWith(":")
                    || cleaned.startsWith("若") || cleaned.startsWith("如果") || cleaned.startsWith("说明")
                    || !seen.add(cleaned)) {
                continue;
            }
            PlanStepData step = new PlanStepData();
            step.setIndex(steps.size());
            step.setTitle(cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned);
            step.setDetail("");
            step.setStatus("PENDING");
            step.setOutput("");
            steps.add(step);
            if (steps.size() >= MAX_PLAN_STEPS) {
                break;
            }
        }
        // 少于 2 步说明没解析出真正的计划：宁可退化成单步，也不要拿一堆碎句当步骤
        return steps.size() >= 2 ? steps : new ArrayList<>();
    }

    /** 步骤执行的 system prompt：Agent 人格 + 当前步骤边界。 */
    private String stepPrompt(String base, PlanStepData step, int index, int total) {
        return base + "\n\n【执行阶段】你正在执行计划的第 " + (index + 1) + "/" + total + " 步："
                + step.getTitle() + "。只完成这一步，不要复述整个计划，不要提前做后面的步骤。";
    }

    /** 步骤执行的用户输入：步骤指令 + 用户原始请求（保留目标上下文）。 */
    private String stepInput(PlanStepData step, int index, String userInput) {
        String detail = step.getDetail() == null || step.getDetail().isBlank()
                ? "" : "（要点：" + step.getDetail() + "）";
        return "请执行第 " + (index + 1) + " 步：" + step.getTitle() + detail
                + "\n用户原始请求：" + userInput;
    }

    /** 单轮模型输出结果（runLoop 与 planLoop 共用的流式单元）。 */
    private record TurnResult(String text, List<ModelPort.ModelToolCall> toolCalls,
                              long inputTokens, long outputTokens, boolean completed) {
    }

    /**
     * 跑一轮模型流式调用：攒批 token、收集工具调用与用量。
     *
     * @param stepIndex  入参：计划步骤下标（auto 策略恒 0；规划阶段 -1 不推送 token）
     * @param emitTokens 入参：是否推送 token 事件（规划阶段不推，避免把计划 JSON 直接喷进气泡）
     * @return 出参：本轮结果
     */
    private TurnResult streamTurn(Long runId, ModelConnectionEntity conn, String systemPrompt,
                                  List<ModelPort.Msg> context, String userInput,
                                  List<ModelPort.ToolSpec> tools, int stepIndex, boolean emitTokens,
                                  List<String> userImages) {
        String apiKey = CryptoUtil.decrypt(conn.getApiKeyCipher());
        ModelPort.ModelRequest request = new ModelPort.ModelRequest(
                conn.getBaseUrl(), apiKey, resolveModel(conn), systemPrompt, context, userInput, tools, userImages);

        StringBuilder fullOutput = new StringBuilder();
        StringBuilder tokenBuf = new StringBuilder();
        // 流式脱敏缓冲：敏感词可能被切在两个 delta 之间（v3.9.2）
        boolean maskOn = sensitiveWords.enabled();
        int maskKeep = maskOn ? sensitiveWords.words().stream().mapToInt(String::length).max().orElse(1) - 1 : 0;
        StringBuilder maskRaw = new StringBuilder();
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
                if (!emitTokens) {
                    return;
                }
                if (maskOn) {
                    maskRaw.append(delta);
                    String[] parts = sensitiveWords.maskStreaming(maskRaw.toString(), maskKeep);
                    // 必须追加而非覆盖：tokenBuf 里还有「已脱敏但未 flush」的上一段
                    tokenBuf.append(parts[0]);
                    maskRaw.setLength(0);
                    maskRaw.append(parts[1]);
                } else {
                    tokenBuf.append(delta);
                }
                boolean byChars = tokenBuf.length() >= FLUSH_EVERY_CHARS;
                boolean byTime = System.currentTimeMillis() - lastFlushAt[0] >= FLUSH_EVERY_MILLIS;
                if (byChars || byTime) {
                    flushToken(runId, tokenBuf, stepIndex);
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
        if (emitTokens) {
            if (maskOn && maskRaw.length() > 0) {
                tokenBuf.append(sensitiveWords.mask(maskRaw.toString()));
                maskRaw.setLength(0);
            }
            flushToken(runId, tokenBuf, stepIndex);
        }
        return new TurnResult(fullOutput.toString(), toolCallsHolder[0], usage[0], usage[1], completed[0]);
    }

    /**
     * 带连接 fallback 的流式调用（v4.1）：主连接失败（completed=false 且无工具调用且未吐出正文）
     * 时按用户其余启用连接顺位回落重试；一旦有正文流出就不再重试（避免气泡内容重复）。
     * 回落的连接同步回填 run 的 model_name / model_conn_id（LLM 观测口径不变）。
     */
    private TurnResult streamTurnResilient(AgentRunEntity run, AgentConfigEntity agent,
                                           String systemPrompt, List<ModelPort.Msg> context,
                                           String userInput, List<ModelPort.ToolSpec> tools,
                                           int stepIndex, boolean emitTokens) {
        ModelConnectionEntity primary = resolveConnection(run, agent);
        List<ModelConnectionEntity> candidates = new ArrayList<>();
        if (primary != null) {
            candidates.add(primary);
        }
        for (ModelConnectionEntity c : modelConnectionRepository.findByUser(run.getUserId())) {
            boolean dup = candidates.stream().anyMatch(x -> x.getId().equals(c.getId()));
            if (!dup && c.getStatus() != null && c.getStatus() == 1) {
                candidates.add(c);
            }
        }
        if (candidates.isEmpty()) {
            return new TurnResult("", null, 0, 0, false);
        }
        // 附图只跟「本轮对话的第一个 LLM 调用」：消耗后不再重发（工具循环/并行节点/恢复续跑不重复烧 token）
        List<String> images = takePendingImages(run);
        TurnResult turn = null;
        for (int i = 0; i < candidates.size(); i++) {
            ModelConnectionEntity attempt = candidates.get(i);
            if (i > 0) {
                log.warn("run {} 模型连接 {}({}) 调用失败，回落连接 {}({})",
                        run.getId(), candidates.get(i - 1).getName(), candidates.get(i - 1).getId(),
                        attempt.getName(), attempt.getId());
                if (!attempt.getId().equals(run.getModelConnId())) {
                    run.setModelConnId(attempt.getId());
                    run.setModelName(firstModel(attempt));
                    runRepository.saveModelInfo(run.getId(), run.getModelName(), attempt.getId());
                }
            }
            turn = streamTurn(run.getId(), attempt, systemPrompt, context, userInput, tools, stepIndex, emitTokens, images);
            if (turn.completed() || turn.toolCalls() != null || (turn.text() != null && !turn.text().isEmpty())) {
                return turn;
            }
        }
        return turn;
    }

    /**
     * 原子取走 run 的待发附图（v4.9）：首个 LLM 调用拿到，其余调用拿 null。
     * runId 级一次性标记——工具循环后续轮、并行节点、恢复续跑都不重发图。
     */
    private List<String> takePendingImages(AgentRunEntity run) {
        if (run.getImagesJson() == null || run.getImagesJson().isBlank()) {
            return List.of();
        }
        if (!imagesConsumed.add(run.getId())) {
            return List.of();
        }
        try {
            List<?> raw = objectMapper.readValue(run.getImagesJson(), List.class);
            List<String> images = raw == null ? List.of()
                    : raw.stream().map(String::valueOf).toList();
            log.info("run {} 附图 {} 张（发给首个 LLM 调用）", run.getId(), images.size());
            return images;
        } catch (Exception e) {
            log.warn("run {} 附图 JSON 解析失败，按纯文本继续: {}", run.getId(), e.getMessage());
            return List.of();
        }
    }

    /** 挂载 Agent（按 run.agent_id 回查；日志留一条，排障入口）。 */
    private AgentConfigEntity agentOf(AgentRunEntity run) {
        AgentConfigEntity agent = run.getAgentId() == null
                ? null : agentConfigRepository.findById(run.getAgentId());
        List<String> allowed = allowedToolsOf(agent);
        log.info("run {} 挂载 agentId={} -> {} · 工具白名单={}", run.getId(), run.getAgentId(),
                agent == null ? "平台默认提示词" : "Agent[" + agent.getName() + "] v" + agent.getVersion(),
                allowed == null ? "全部" : allowed);
        return agent;
    }

    private List<String> allowedToolsOf(AgentConfigEntity agent) {
        return agent == null ? null : AgentToolCodec.decode(agent.getToolsJson());
    }

    /**
     * 装配 system prompt：Agent 人格（缺省用平台提示词）+ 该用户的长期记忆。
     *
     * @param agent  入参：挂载的 Agent（可空）
     * @param userId 入参：用户（长期记忆按用户隔离）
     * @return 出参：最终 system prompt
     */
    private String instructionOf(AgentConfigEntity agent, Long userId) {
        String base = agent != null && agent.getInstruction() != null && !agent.getInstruction().isBlank()
                ? agent.getInstruction() : systemPrompt;
        return memoryService.augment(userId, base);
    }

    /**
     * 会话历史上下文（不含最后一条用户输入）。会话为空返回 null。
     *
     * <p>窗口按 Agent 配置的短期记忆轮数（{@code historyTurns}）计算：1 轮 = 用户 + 助手各 1 条；
     * 未配置回落 {@link #DEFAULT_HISTORY_TURNS}，与 v4.1 之前的固定 20 条行为等价。</p>
     */
    private List<ModelPort.Msg> historyContext(AgentRunEntity run, AgentConfigEntity agent) {
        List<AgentMessageEntity> history = messageRepository.findBySession(run.getSessionId(), run.getUserId());
        if (history.isEmpty()) {
            return null;
        }
        int window = historyWindow(agent);
        List<ModelPort.Msg> context = new ArrayList<>();
        history.stream()
                .limit(Math.max(0, history.size() - 1))
                .skip(Math.max(0, history.size() - 1 - window))
                .forEach(m -> context.add(new ModelPort.Msg(m.getRole(), m.getContent())));
        return context;
    }

    /** 短期记忆窗口（条）：Agent 配置轮数 × 2；未配置/越界回落默认，0 = 只看本轮输入。 */
    private int historyWindow(AgentConfigEntity agent) {
        Integer turns = agent == null ? null : agent.getHistoryTurns();
        int effective = (turns == null || turns < 0) ? DEFAULT_HISTORY_TURNS : Math.min(turns, MAX_HISTORY_TURNS);
        return effective * 2;
    }

    /** 本轮最后一条用户输入。会话为空返回 null。 */
    private String lastUserInput(AgentRunEntity run) {
        List<AgentMessageEntity> history = messageRepository.findBySession(run.getSessionId(), run.getUserId());
        return history.isEmpty() ? null : history.get(history.size() - 1).getContent();
    }

    /**
     * 运行耗时（毫秒）：收口事件（run_completed）与快照共用口径，startedAt → 当前时刻。
     * 对话页回答尾注展示「耗时 x.xs」用（v4.1.3）。
     */
    private long durationMs(AgentRunEntity run) {
        return run.getStartedAt() == null ? 0
                : System.currentTimeMillis() - run.getStartedAt().toEpochMilli();
    }

    // ---------- 执行图（v3.2.0） ----------

    /**
     * 执行图主流程：按 Kahn 拓扑序逐节点执行（v3.2.0 串行；并行留后续切片）。
     *
     * <p><b>无内存态</b>：图快照在创建运行时冻结（{@code agent_run.graph_json}），
     * 进度（done / outputs / current）逐步落 {@code graph_state_json}——
     * 节点审批挂起、进程重启后都从断点续跑，已完成的节点不会重跑。</p>
     */
    private void graphLoop(AgentRunEntity run, List<String> notes, int depth) {
        Long runId = run.getId();
        AgentConfigEntity agent = agentOf(run);
        List<String> agentTools = allowedToolsOf(agent);
        ModelConnectionEntity conn = resolveConnection(run, agent);
        if (conn == null || conn.getApiKeyCipher() == null) {
            failIfStillRunning(runId, "MODEL_ERROR", "尚未配置启用的模型连接，请先在「模型连接」页添加并启用。");
            return;
        }
        AgentGraphData graph = GraphCodec.decodeGraph(run.getGraphJson());
        if (graph == null || graph.getNodes() == null || graph.getNodes().isEmpty()) {
            failIfStillRunning(runId, "GRAPH_MISSING", "该运行缺少执行图快照，无法调度。");
            return;
        }
        String userInput = lastUserInput(run);
        if (userInput == null) {
            failIfStillRunning(runId, "MODEL_ERROR", "会话内没有用户消息，无法发起运行。");
            return;
        }

        List<GraphNode> all = graph.getNodes();
        Map<String, GraphNode> byId = new java.util.HashMap<>();
        for (GraphNode node : all) {
            byId.put(node.getId(), node);
        }
        GraphStateData state = GraphCodec.decodeState(run.getGraphStateJson());
        java.util.Set<String> done = new java.util.LinkedHashSet<>(state.getDone());
        java.util.Set<String> skipped = new java.util.LinkedHashSet<>(state.getSkipped());
        int total = all.size();

        if (done.isEmpty() && skipped.isEmpty() && state.getCurrent() == null) {
            List<Map<String, Object>> brief = new ArrayList<>();
            for (GraphNode node : all) {
                brief.add(Map.of("id", node.getId(), "type", node.getType(), "title", nodeLabel(node)));
            }
            // 出边信息一并下发：前端据此识别「条件路由判定节点」（多条条件出边的 LLM 节点），
            // 这类节点的产出是路由信号（如意图单词），对话页不作为回答气泡展示
            List<Map<String, Object>> edgeBrief = new ArrayList<>();
            for (GraphEdge edge : graph.getEdges() == null ? List.<GraphEdge>of() : graph.getEdges()) {
                edgeBrief.add(Map.of("from", edge.getFrom(), "to", edge.getTo(), "route", edge.getRoute() == null ? "" : edge.getRoute()));
            }
            appendEvent(runId, RuntimeEventType.GRAPH_STARTED, objectMapper.writeValueAsString(Map.of(
                    "nodes", brief,
                    "edges", edgeBrief,
                    "total", total,
                    "ts", System.currentTimeMillis())));
            log.info("run {} 执行图开始，共 {} 个节点", runId, total);
        }

        // 就绪队列调度（v3.3）：每轮重新计算「可执行 / 可跳过」，
        // 边的命中结果由上游产出实时重算（谓词是纯函数），因此恢复时无需持久化"走没走"。
        while (true) {
            if (isTerminal(run)) {
                return;
            }
            List<GraphNode> ready = new ArrayList<>();
            List<GraphNode> toSkip = new ArrayList<>();
            for (GraphNode node : all) {
                if (done.contains(node.getId()) || skipped.contains(node.getId())) {
                    continue;
                }
                List<GraphEdge> incoming = incomingEdges(graph, node.getId());
                if (incoming.isEmpty()) {
                    ready.add(node);
                    continue;
                }
                boolean resolved = true;
                boolean anyTaken = false;
                for (GraphEdge edge : incoming) {
                    Boolean taken = edgeTaken(graph, state, edge, done, skipped);
                    if (taken == null) {
                        resolved = false;
                        break;
                    }
                    if (taken) {
                        anyTaken = true;
                    }
                }
                if (!resolved) {
                    continue;
                }
                if (anyTaken) {
                    ready.add(node);
                } else {
                    toSkip.add(node);
                }
            }

            // 先处理跳过（一次落库、一次事件循环），再取就绪节点——保证状态收敛
            if (!toSkip.isEmpty()) {
                for (GraphNode node : toSkip) {
                    skipped.add(node.getId());
                    state.getSkipped().add(node.getId());
                }
                String skipJson = GraphCodec.encodeState(state);
                runRepository.saveGraphState(runId, skipJson);
                run.setGraphStateJson(skipJson);
                for (GraphNode node : toSkip) {
                    log.info("run {} 节点 {} 条件路由未命中，跳过", runId, node.getId());
                    appendEvent(runId, RuntimeEventType.STEP_COMPLETED, objectMapper.writeValueAsString(Map.of(
                            "stepIndex", all.indexOf(node),
                            "nodeId", node.getId(),
                            "title", nodeLabel(node),
                            "skipped", true,
                            "outputText", "",
                            "ts", System.currentTimeMillis())));
                }
                continue;
            }
            if (ready.isEmpty()) {
                // 条件路由死路守卫（v4.3.3）：还有节点既未完成也未跳过 = 上游的条件出边全部失配。
                // 此前静默 break → 图带着中间节点产出（如分类词）当最终答案「成功」收场，极难排查；
                // 改为显式报错，提示检查分支条件与上游节点的输出约定。
                boolean stuck = all.stream().anyMatch(n -> !done.contains(n.getId()) && !skipped.contains(n.getId()));
                if (stuck) {
                    failIfStillRunning(runId, "ROUTE_ERROR",
                            "执行图条件路由死路：某节点的产出没有命中任何出边条件（startsWith/contains），图无法继续。"
                                    + "请检查分支条件关键词与上游节点提示词约定的输出格式是否一致。");
                    return;
                }
                break;
            }

            // 并行批次：同层就绪节点（互无依赖）最多 graphParallelism 个并发执行。
            // 审批门不参与并发——它一旦挂起整个 run 就 BLOCKED，兄弟节点在飞会无法收口。
            if (graphParallelism > 1) {
                List<GraphNode> batch = new ArrayList<>();
                for (GraphNode candidate : ready) {
                    if ("APPROVAL".equals(candidate.getType())) {
                        continue;
                    }
                    batch.add(candidate);
                    if (batch.size() >= graphParallelism) {
                        break;
                    }
                }
                if (batch.size() >= 2) {
                    if (executeBatch(run, agent, conn, graph, state, all, batch, done, userInput,
                            depth, agentTools)) {
                        return;
                    }
                    continue;
                }
            }

            GraphNode node = ready.get(0);
            int stepIndex = all.indexOf(node);

            // 审批门：挂起等人批（run 与节点同时 BLOCKED，kind=NODE）
            if ("APPROVAL".equals(node.getType())) {
                suspendForNodeApproval(run, node);
                return;
            }

            // 同一节点可能因"工具执行后重入"再次进入：current 已是本节点时不重复发 step_started
            boolean reentry = node.getId().equals(state.getCurrent());
            state.setCurrent(node.getId());
            String currentJson = GraphCodec.encodeState(state);
            runRepository.saveGraphState(runId, currentJson);
            // 同步回内存实体：工具执行后重入时靠它识别"同一节点"，避免重复 step_started
            run.setGraphStateJson(currentJson);
            if (!reentry) {
                appendEvent(runId, RuntimeEventType.STEP_STARTED, objectMapper.writeValueAsString(Map.of(
                        "stepIndex", stepIndex,
                        "nodeId", node.getId(),
                        "nodeType", node.getType(),
                        "title", nodeLabel(node),
                        "total", total,
                        "ts", System.currentTimeMillis())));
            }

            if ("START".equals(node.getType()) || "END".equals(node.getType())) {
                // 开始节点表单（v4.5.4）：有 formSchema 时产出 = 变量表文本，下游按普通上游/变量引用取用
                if ("START".equals(node.getType()) && node.getFormSchema() != null && !node.getFormSchema().isEmpty()) {
                    done.add(node.getId());
                    markNodeDone(run, state, node, formTableText(node, run.getFormValuesJson()), stepIndex);
                } else {
                    done.add(node.getId());
                    markNodeDone(run, state, node, "", stepIndex);
                }
                continue;
            }

            if ("TOOL".equals(node.getType())) {
                ToolRegistry.ToolHandler handler = toolRegistry.find(node.getTool());
                if (handler == null) {
                    failIfStillRunning(runId, "GRAPH_TOOL_MISSING", "节点 " + node.getId()
                            + " 引用的工具已不可用：" + node.getTool());
                    return;
                }
                Map<String, Object> args = new HashMap<>(node.getArgs() == null ? Map.of() : node.getArgs());
                args.put("__runId", runId);
                args.put("__userId", run.getUserId());
                args.put("__callId", "node_" + node.getId());
                recordNodeInput(state, node, toolNodeInput(node));
                appendEvent(runId, RuntimeEventType.TOOL_CALL, objectMapper.writeValueAsString(Map.of(
                        "callId", "node_" + node.getId(),
                        "toolName", node.getTool(),
                        "args", node.getArgs() == null ? Map.of() : node.getArgs(),
                        "riskLevel", handler.needsApproval ? "HIGH" : "LOW",
                        "stepIndex", stepIndex,
                        "ts", System.currentTimeMillis())));
                // 作者显式配置的工具节点 = 已授权，直接执行（审批只拦"模型自主发起的写操作"）
                List<String> noteBuf = new ArrayList<>();
                executeAndEmitToolResult(run, node.getTool(), handler, args, noteBuf);
                String output = noteBuf.isEmpty() ? "" : noteBuf.get(noteBuf.size() - 1);
                done.add(node.getId());
                markNodeDone(run, state, node, output, stepIndex);
                continue;
            }

            if ("CODE".equals(node.getType())) {
                recordNodeInput(state, node, node.getCode());
                String output;
                try {
                    output = execInSandbox(run, node, upstreamOutputs(graph, state, node.getId()),
                            boundVarMap(graph, state, node));
        } catch (Exception e) {
            failIfStillRunning(runId, "SANDBOX_ERROR", "代码节点执行失败："
                    + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
            return;
        }
                done.add(node.getId());
                markNodeDone(run, state, node, output, stepIndex);
                continue;
            }

            // LLM 节点（context 要在 lambda 里用，必须是 effectively final）
            List<ModelPort.Msg> history = historyContext(run, agent);
            final List<ModelPort.Msg> context = history == null ? new ArrayList<>() : history;
            for (String upstream : upstreamOutputs(graph, state, node.getId())) {
                context.add(new ModelPort.Msg("ASSISTANT", upstream));
            }
            notes.forEach(note -> context.add(new ModelPort.Msg("USER", "[系统] " + note)));
            recordNodeInput(state, node, llmNodeInput(graph, state, node, userInput));

            List<String> allowed = node.getTools() == null ? agentTools : node.getTools();
            List<ModelPort.ToolSpec> tools = depth < MAX_TOOL_ROUNDS
                    ? graphTools(allowed) : List.of();
            if (depth >= MAX_TOOL_ROUNDS) {
                context.add(new ModelPort.Msg("USER", TOOL_CUT_NOTE));
            }
            TurnResult turn = streamTurnResilient(run, agent,
                    nodeSystemPrompt(instructionOf(agent, run.getUserId()), node),
                    context, nodeInput(node, userInput), tools, stepIndex, true);
            if (turn.toolCalls() != null) {
                handleToolCalls(run, turn.toolCalls(), notes, depth, allowed);
                return;
            }
            if (!turn.completed()) {
                failIfStillRunning(runId, "MODEL_ERROR", "模型调用失败，请检查模型配置。");
                return;
            }
            notes.clear();
            state.setInputTokens(state.getInputTokens() + turn.inputTokens());
            state.setOutputTokens(state.getOutputTokens() + turn.outputTokens());
            done.add(node.getId());
            markNodeDone(run, state, node, turn.text(), stepIndex);
        }

        // 收口：正文 = 已执行节点的产出（按图内声明顺序拼装，跳过/审批门不进正文）
        if (done.size() + skipped.size() < total) {
            failIfStillRunning(runId, "GRAPH_STALLED", "执行图未能推进（存在无法判定的节点），请检查连线");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (GraphNode node : all) {
            if ("START".equals(node.getType()) || "APPROVAL".equals(node.getType())
                    || skipped.contains(node.getId())) {
                continue;
            }
            String output = state.getOutputs().get(node.getId());
            if (output == null || output.isBlank()) {
                continue;
            }
            sb.append("### ").append(nodeLabel(node)).append("\n").append(output).append("\n\n");
        }
        String outputText = maskOutput(sb.toString().trim());
        int rows = runRepository.finish(runId, RunStatus.COMPLETED.name(), outputText, null, null,
                (int) state.getInputTokens(), (int) state.getOutputTokens());
        if (rows > 0) {
            appendEvent(runId, RuntimeEventType.GRAPH_COMPLETED, objectMapper.writeValueAsString(Map.of(
                    "completed", state.getDone(),
                    "skipped", state.getSkipped(),
                    "total", total,
                    "ts", System.currentTimeMillis())));
            appendEvent(runId, RuntimeEventType.RUN_COMPLETED, objectMapper.writeValueAsString(Map.of(
                    "runId", runId,
                    "outputText", outputText,
                    "usage", Map.of("inputTokens", state.getInputTokens(), "outputTokens", state.getOutputTokens()),
                    "traceId", run.getTraceId() == null ? "" : run.getTraceId(),
                    "durationMs", durationMs(run),
                    "ts", System.currentTimeMillis())));
            insertAssistantMessage(run, outputText, runId);
        }
    }

    /**
     * 并行执行一批就绪节点（同层、互无依赖）。
     *
     * <p><b>线程模型</b>：节点任务跑在虚拟线程里（模型流式调用各自独立）；
     * 事件由 EventPublisher 保证 seq 原子，token 交错到达前端按 stepIndex 归位；
     * <b>状态与落库只在主线程做</b>——批量结束后按图内顺序应用结果，避免并发写 state。</p>
     *
     * <p>批内任一节点要挂审批（模型自主工具调用）时：先把已完成节点的结果应用掉，再挂起，
     * 恢复后只重跑挂起那个节点——兄弟节点的产出不会白跑。</p>
     *
     * @return true 表示本方法已经收口（挂起或失败），调用方应直接 return
     */
    private boolean executeBatch(AgentRunEntity run, AgentConfigEntity agent, ModelConnectionEntity conn,
                                 AgentGraphData graph, GraphStateData state, List<GraphNode> all,
                                 List<GraphNode> batch, java.util.Set<String> done, String userInput,
                                 int depth, List<String> agentTools) {
        Long runId = run.getId();
        List<String> notesCopy = new ArrayList<>();
        state.setCurrent(batch.get(0).getId());
        String currentJson = GraphCodec.encodeState(state);
        runRepository.saveGraphState(runId, currentJson);
        run.setGraphStateJson(currentJson);
        for (GraphNode node : batch) {
            appendEvent(runId, RuntimeEventType.STEP_STARTED, objectMapper.writeValueAsString(Map.of(
                    "stepIndex", all.indexOf(node),
                    "nodeId", node.getId(),
                    "nodeType", node.getType(),
                    "title", nodeLabel(node),
                    "total", all.size(),
                    "parallel", true,
                    "ts", System.currentTimeMillis())));
        }

        List<NodeOutcome> outcomes = new ArrayList<>();
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<NodeOutcome>> futures = new ArrayList<>();
            for (GraphNode node : batch) {
                if ("TOOL".equals(node.getType())) {
                    outcomes.add(runToolNode(run, graph, state, all, node, notesCopy));
                    continue;
                }
                if ("CODE".equals(node.getType())) {
                    recordNodeInput(state, node, node.getCode());
                    outcomes.add(runCodeNode(run, graph, state, all, node));
                    continue;
                }
                List<ModelPort.Msg> context = historyContext(run, agent);
                final List<ModelPort.Msg> ctx = context == null ? new ArrayList<>() : context;
                for (String upstream : boundOutputs(graph, state, node)) {
                    ctx.add(new ModelPort.Msg("ASSISTANT", upstream));
                }
                // 输入摘要在主线程记（state 只在主线程改），并发线程只做模型调用
                recordNodeInput(state, node, llmNodeInput(graph, state, node, userInput));
                final int stepIndex = all.indexOf(node);
                final List<String> allowed = node.getTools() == null ? agentTools : node.getTools();
                final List<ModelPort.ToolSpec> tools = depth < MAX_TOOL_ROUNDS
                        ? graphTools(allowed) : List.of();
                if (depth >= MAX_TOOL_ROUNDS) {
                    ctx.add(new ModelPort.Msg("USER", TOOL_CUT_NOTE));
                }
                futures.add(executor.submit(() -> {
                    TurnResult turn = streamTurnResilient(run, agent,
                            nodeSystemPrompt(instructionOf(agent, run.getUserId()), node), ctx,
                            nodeInput(node, userInput), tools, stepIndex, true);
                    return new NodeOutcome(node, stepIndex, turn.text(), turn.inputTokens(),
                            turn.outputTokens(), turn.toolCalls(), turn.completed(),
                            allowed, null, null);
                }));
            }
            for (var future : futures) {
                try {
                    outcomes.add(future.get(180, java.util.concurrent.TimeUnit.SECONDS));
                } catch (java.util.concurrent.TimeoutException e) {
                    failIfStillRunning(runId, "MODEL_TIMEOUT", "节点执行超时（180s），已终止本次运行。");
                    return true;
                } catch (Exception e) {
                    log.error("并行节点执行异常 runId={}", runId, e);
                    failIfStillRunning(runId, "GRAPH_ERROR", "节点执行异常，请重试。");
                    return true;
                }
            }
        }

        outcomes.sort(java.util.Comparator.comparingInt(NodeOutcome::stepIndex));
        List<ModelPort.ModelToolCall> pendingCalls = null;
        List<String> pendingAllowed = null;
        for (NodeOutcome outcome : outcomes) {
            if (!outcome.completed()) {
                failIfStillRunning(runId, "MODEL_ERROR", "模型调用失败，请检查模型配置。");
                return true;
            }
            if (outcome.toolCalls() != null && !outcome.toolCalls().isEmpty()) {
                if (pendingCalls == null) {
                    pendingCalls = outcome.toolCalls();
                    pendingAllowed = outcome.allowed();
                }
                continue;
            }
            state.setInputTokens(state.getInputTokens() + outcome.inputTokens());
            state.setOutputTokens(state.getOutputTokens() + outcome.outputTokens());
            done.add(outcome.node().getId());
            markNodeDone(run, state, outcome.node(), outcome.output(), outcome.stepIndex());
        }
        if (pendingCalls != null) {
            // 先把并行任务里要审批的那条挂起；其节点保持未完成，恢复后会重跑
            handleToolCalls(run, pendingCalls, new ArrayList<>(), depth, pendingAllowed);
            return true;
        }
        return false;
    }

    /** 并行批次里的 TOOL 节点（作者显式配置、无审批，主线程直接执行）。 */
    private NodeOutcome runToolNode(AgentRunEntity run, AgentGraphData graph, GraphStateData state,
                                    List<GraphNode> all, GraphNode node, List<String> notes) {
        int stepIndex = all.indexOf(node);
        ToolRegistry.ToolHandler handler = toolRegistry.find(node.getTool());
        if (handler == null) {
            return new NodeOutcome(node, stepIndex, "", 0, 0, null, false, null,
                    "GRAPH_TOOL_MISSING", "工具已不可用：" + node.getTool());
        }
        Map<String, Object> args = new HashMap<>(node.getArgs() == null ? Map.of() : node.getArgs());
        args.put("__runId", run.getId());
        args.put("__userId", run.getUserId());
        args.put("__callId", "node_" + node.getId());
        recordNodeInput(state, node, toolNodeInput(node));
        appendEvent(run.getId(), RuntimeEventType.TOOL_CALL, objectMapper.writeValueAsString(Map.of(
                "callId", "node_" + node.getId(),
                "toolName", node.getTool(),
                "args", node.getArgs() == null ? Map.of() : node.getArgs(),
                "riskLevel", handler.needsApproval ? "HIGH" : "LOW",
                "stepIndex", stepIndex,
                "ts", System.currentTimeMillis())));
        List<String> noteBuf = new ArrayList<>();
        executeAndEmitToolResult(run, node.getTool(), handler, args, noteBuf);
        String output = noteBuf.isEmpty() ? "" : noteBuf.get(noteBuf.size() - 1);
        return new NodeOutcome(node, stepIndex, output, 0, 0, null, true, null, null, null);
    }

    /** 并行批次里的 CODE 节点（沙箱同步执行，快且无流式，主线程直接跑）。 */
    private NodeOutcome runCodeNode(AgentRunEntity run, AgentGraphData graph, GraphStateData state,
                                    List<GraphNode> all, GraphNode node) {
        int stepIndex = all.indexOf(node);
        try {
            String output = execInSandbox(run, node, upstreamOutputs(graph, state, node.getId()),
                    boundVarMap(graph, state, node));
            return new NodeOutcome(node, stepIndex, output, 0, 0, null, true, null, null, null);
        } catch (Exception e) {
            return new NodeOutcome(node, stepIndex, "", 0, 0, null, false, null,
                    "SANDBOX_ERROR", "代码节点执行失败："
                    + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /**
     * 沙箱执行统一入口：CODE 节点串行/并行与 run_python 工具共用。
     *
     * @param run      入参：运行（产物归属）
     * @param node     入参：CODE 节点（须有 code）
     * @param upstream 入参：上游节点产出（注入代码内 upstream 变量）
     * @return 出参：stdout（+stderr/超时标记）文本
     * @throws Exception 沙箱服务不可用等异常（调用方折算为 SANDBOX_ERROR）
     */
    private String execInSandbox(AgentRunEntity run, GraphNode node, List<String> upstream,
                                 java.util.LinkedHashMap<String, String> vars) throws Exception {
        if (codeSandbox == null) {
            throw new IllegalStateException("沙箱服务未就绪（LEMON_SANDBOX_URL 未配置或沙箱容器未启动）");
        }
        if (node.getCode() == null || node.getCode().isBlank()) {
            throw new IllegalArgumentException("节点 " + node.getId() + " 没有可执行的代码");
        }
        CodeSandboxPort.ExecResult r = codeSandbox.exec(node.getCode(), upstream, vars, 15);
        // CODE 节点产物落库（v4.4.4）：图片不进正文（数据 URI 会炸 token），文件统一入产物可下载
        if (artifactService != null && r.files() != null && !r.files().isEmpty()) {
            for (CodeSandboxPort.ExecResult.FileRef f : r.files()) {
                try {
                    artifactService.save(run.getUserId(), run.getId(), f.name(), f.contentType(), f.contentB64());
                } catch (Exception e) {
                    log.warn("CODE 节点产物落库失败 run={} name={}", run.getId(), f.name(), e);
                }
            }
        }
        return r.asText();
    }

    /** 并行节点执行结果（主线程据此统一落库与发事件）。 */
    private record NodeOutcome(GraphNode node, int stepIndex, String output, long inputTokens,
                               long outputTokens, List<ModelPort.ModelToolCall> toolCalls,
                               boolean completed, List<String> allowed, String errorCode, String errorMsg) {
    }

    /** 入边列表。 */
    private List<GraphEdge> incomingEdges(AgentGraphData graph, String nodeId) {
        List<GraphEdge> result = new ArrayList<>();
        for (GraphEdge edge : graph.getEdges() == null ? List.<GraphEdge>of() : graph.getEdges()) {
            if (nodeId.equals(edge.getTo())) {
                result.add(edge);
            }
        }
        return result;
    }

    /**
     * 边是否命中：上游未完成返回 null（未解析）；上游被跳过返回 false；
     * 上游已完成则按上游产出实时求值（纯函数，恢复时可重算）。
     */
    private Boolean edgeTaken(AgentGraphData graph, GraphStateData state, GraphEdge edge,
                              java.util.Set<String> done, java.util.Set<String> skipped) {
        String from = edge.getFrom();
        if (skipped.contains(from)) {
            return false;
        }
        if (!done.contains(from)) {
            return null;
        }
        // 条件路由匹配用净文：思考开着的节点输出以 <think>…</think> 开头，
        // 直接 startsWith/contains 会全部失配 → 无出边命中 → 图提前收场（v4.3.3 实测缺陷）
        return RoutePredicates.evaluate(edge.getRoute(), stripThink(state.getOutputs().get(from)));
    }

    /** 剥掉思考包装标签，取正文净文（无 <think> 时原样返回）。 */
    private static String stripThink(String text) {
        if (text == null) {
            return null;
        }
        return text.replaceAll("(?s)<think>.*?</think>", "").trim();
    }


    /** 节点完成：写 done/outputs + 覆盖进度（一次 UPDATE，进度与产出同源）。 */
    /** 输出脱敏：命中敏感词替换为【已屏蔽】（词库空时零开销直通）。 */
    private String maskOutput(String text) {
        return sensitiveWords.enabled() ? sensitiveWords.mask(text) : text;
    }

    private void markNodeDone(AgentRunEntity run, GraphStateData state, GraphNode node, String output, int stepIndex) {
        output = maskOutput(output);
        // 节点产出打点：路由类工作流（意图分类等）在后端日志里可直接看到判定结果
        String brief = output == null ? "" : output.replaceAll("\\s+", " ").trim();
        log.info("run {} 节点 {}[{}] 完成: {}", run.getId(), node.getId(), nodeLabel(node),
                brief.length() > 160 ? brief.substring(0, 160) + "…" : brief);
        if (!state.getDone().contains(node.getId())) {
            state.getDone().add(node.getId());
        }
        state.getOutputs().put(node.getId(), output == null ? "" : output);
        state.setCurrent(null);
        String json = GraphCodec.encodeState(state);
        runRepository.saveGraphState(run.getId(), json);
        run.setGraphStateJson(json);
        appendEvent(run.getId(), RuntimeEventType.STEP_COMPLETED, objectMapper.writeValueAsString(Map.of(
                "stepIndex", stepIndex,
                "nodeId", node.getId(),
                "title", nodeLabel(node),
                "outputText", output == null ? "" : output,
                "usage", Map.of("inputTokens", state.getInputTokens(), "outputTokens", state.getOutputTokens()),
                "ts", System.currentTimeMillis())));
    }

    /**
     * 开始节点表单的变量表文本（v4.5.4）：按 formSchema 顺序逐行「label（name）：值」。
     * 表单值缺失（旧 run/重试场景）用默认值回填；formValuesJson 解析失败按空表处理。
     *
     * @param node           入参：START 节点（含 formSchema）
     * @param formValuesJson 入参：创建时落库的表单值 JSON
     * @return 出参：变量表文本（至少一行）
     */
    private String formTableText(GraphNode node, String formValuesJson) {
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        if (formValuesJson != null && !formValuesJson.isBlank()) {
            try {
                java.util.Map<?, ?> parsed = objectMapper.readValue(formValuesJson, java.util.Map.class);
                for (java.util.Map.Entry<?, ?> e : parsed.entrySet()) {
                    values.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
            } catch (Exception e) {
                log.warn("run 表单值解析失败，按默认值回填：{}", e.getMessage());
            }
        }
        StringBuilder sb = new StringBuilder();
        for (GraphNode.FormField f : node.getFormSchema()) {
            String v = values.get(f.getName());
            if (v == null || v.isBlank()) {
                v = f.getDefaultValue() == null ? "" : f.getDefaultValue();
            }
            sb.append(f.getLabel()).append("（").append(f.getName()).append("）：").append(v).append("\n");
        }
        return sb.toString().stripTrailing();
    }

    /** 上游节点产出（用于拼下游上下文）：取所有指向本节点的边的来源节点输出（净文，剥思考块）。 */
    private List<String> upstreamOutputs(AgentGraphData graph, GraphStateData state, String nodeId) {
        List<String> result = new ArrayList<>();
        for (GraphEdge edge : graph.getEdges() == null ? List.<GraphEdge>of() : graph.getEdges()) {
            if (!nodeId.equals(edge.getTo())) {
                continue;
            }
            String output = stripThink(state.getOutputs().get(edge.getFrom()));
            if (output != null && !output.isBlank()) {
                result.add("[上游 " + edge.getFrom() + "] " + output);
            }
        }
        return result;
    }

    /**
     * 节点实际注入的上游上下文（v4.5.3 输入变量绑定）：
     * 有 inputRefs 时按声明引用任意节点的产出（【变量:别名】块，不限于直接上游）；
     * 无则回落默认的直接上游拼接。LLM 上下文与输入摘要同源共用。
     */
    private List<String> boundOutputs(AgentGraphData graph, GraphStateData state, GraphNode node) {
        if (node.getInputRefs() == null || node.getInputRefs().isEmpty()) {
            return upstreamOutputs(graph, state, node.getId());
        }
        List<String> result = new ArrayList<>();
        for (GraphNode.InputRef ref : node.getInputRefs()) {
            String output = stripThink(state.getOutputs().get(ref.getFrom()));
            if (output == null || output.isBlank()) {
                output = "（该节点尚未产出或输出为空）";
            }
            result.add("【变量: " + ref.getAlias() + "】\n" + output);
        }
        return result;
    }

    /**
     * 输入变量绑定的字典形态（v4.5.3）：CODE 节点绑定时 upstream 传 {别名: 产出}。
     *
     * @param graph 入参：图
     * @param state 入参：运行状态（各节点产出）
     * @param node  入参：CODE 节点
     * @return 出参：绑定字典；无绑定时返回 null（调用方回落列表语义）
     */
    private java.util.LinkedHashMap<String, String> boundVarMap(AgentGraphData graph, GraphStateData state, GraphNode node) {
        if (node.getInputRefs() == null || node.getInputRefs().isEmpty()) {
            return null;
        }
        java.util.LinkedHashMap<String, String> vars = new java.util.LinkedHashMap<>();
        for (GraphNode.InputRef ref : node.getInputRefs()) {
            String output = stripThink(state.getOutputs().get(ref.getFrom()));
            vars.put(ref.getAlias(), output == null ? "" : output);
        }
        return vars;
    }

    /**
     * 记录节点输入摘要（限 4000 字防 graph_state_json 膨胀），供运行历史的图例视图查看。
     * 只在主线程调用——与 done/outputs 一样落 {@code graph_state_json}，审批恢复/重启后仍在。
     */
    private void recordNodeInput(GraphStateData state, GraphNode node, String input) {
        String text = input == null ? "" : input;
        state.getInputs().put(node.getId(),
                text.length() > 4000 ? text.substring(0, 4000) + "…（已截断）" : text);
    }

    /** TOOL 节点的输入摘要：调了什么工具、带了什么参数。 */
    private String toolNodeInput(GraphNode node) {
        return "调用工具 " + node.getTool() + "，参数：" + objectMapper.writeValueAsString(
                node.getArgs() == null ? Map.of() : node.getArgs());
    }

    /** LLM 节点的输入摘要：节点任务 + 用户原始请求 + 上游产出（与发给模型的输入同源）。 */
    private String llmNodeInput(AgentGraphData graph, GraphStateData state, GraphNode node, String userInput) {
        StringBuilder sb = new StringBuilder(nodeInput(node, userInput));
        List<String> ups = boundOutputs(graph, state, node);
        if (!ups.isEmpty()) {
            sb.append("\n【上游产出】\n").append(String.join("\n---\n", ups));
        }
        return sb.toString();
    }

    /** 节点展示名：title 优先，否则用 id。 */
    private String nodeLabel(GraphNode node) {
        return node.getTitle() == null || node.getTitle().isBlank() ? node.getId() : node.getTitle();
    }

    /** LLM 节点的 system prompt：Agent 人格 + 节点指令（节点指令是这一步的局部目标）。 */
    private String nodeSystemPrompt(String base, GraphNode node) {
        String instruction = node.getPrompt() == null || node.getPrompt().isBlank()
                ? nodeLabel(node) : node.getPrompt();
        return base + "\n\n【当前节点： " + nodeLabel(node) + "】" + instruction
                + "\n只完成本节点职责，不要提前完成后续节点的工作。";
    }

    /** LLM 节点的用户输入：节点目标 + 上游产出 + 用户原始请求。 */
    private String nodeInput(GraphNode node, String userInput) {
        String instruction = node.getPrompt() == null || node.getPrompt().isBlank()
                ? nodeLabel(node) : node.getPrompt();
        return "本节点任务：" + instruction + "\n用户原始请求：" + userInput;
    }

    /**
     * 图执行内可用的工具定义：过滤工作流工具（run_wf_*），
     * 防止「图 → 工作流 → 图」的嵌套递归——工作流工具只在 auto / plan 对话中装配。
     */
    private List<ModelPort.ToolSpec> graphTools(java.util.Collection<String> allowed) {
        return toolRegistry.specs(allowed).stream()
                .filter(spec -> !spec.name().startsWith("run_wf_"))
                .toList();
    }

    /** 节点审批挂起：RUNNING→BLOCKED + pending(kind=NODE) + approval_required 事件。 */
    private void suspendForNodeApproval(AgentRunEntity run, GraphNode node) {
        long expiresAt = System.currentTimeMillis() + approvalTimeoutSeconds * 1000L;
        String approvalId = "ap_" + UUID.randomUUID().toString().substring(0, 8);
        String prompt = node.getApprovalPrompt() == null || node.getApprovalPrompt().isBlank()
                ? "执行到审批节点「" + nodeLabel(node) + "」，是否继续？" : node.getApprovalPrompt();
        String pendingJson = objectMapper.writeValueAsString(Map.of(
                "kind", "NODE",
                "approvalId", approvalId,
                "nodeId", node.getId(),
                "title", nodeLabel(node),
                "prompt", prompt,
                "riskLevel", "HIGH",
                "expiresAt", expiresAt));
        int rows = runRepository.suspend(run.getId(), approvalId, pendingJson);
        if (rows == 0) {
            return;
        }
        appendEvent(run.getId(), RuntimeEventType.APPROVAL_REQUIRED, objectMapper.writeValueAsString(Map.of(
                "approvalId", approvalId,
                "kind", "NODE",
                "nodeId", node.getId(),
                "prompt", prompt,
                "riskLevel", "HIGH",
                "expiresAt", expiresAt,
                "ts", System.currentTimeMillis())));
        log.info("运行挂起等待节点审批 runId:{} node:{} approvalId:{}", run.getId(), node.getId(), approvalId);
    }

    /**
     * 节点审批通过后的续跑：把该节点标记完成，然后回到图调度继续下一个节点。
     *
     * @param run    入参：运行实体（已回到 RUNNING）
     * @param nodeId 入参：审批节点 ID
     */
    public void resumeNodeApproved(AgentRunEntity run, String nodeId) {
        cancelFlags.put(run.getId(), new AtomicBoolean(false));
        Thread.startVirtualThread(() -> {
            try {
                GraphStateData state = GraphCodec.decodeState(run.getGraphStateJson());
                if (!state.getDone().contains(nodeId)) {
                    state.getDone().add(nodeId);
                }
                state.getOutputs().putIfAbsent(nodeId, "（人工审批通过）");
                state.setCurrent(null);
                String json = GraphCodec.encodeState(state);
                runRepository.saveGraphState(run.getId(), json);
                run.setGraphStateJson(json);
                runLoop(run, new ArrayList<>(), 0);
            } catch (Exception e) {
                log.error("节点审批续跑异常 runId={}", run.getId(), e);
                failIfStillRunning(run.getId(), "GRAPH_ERROR", "审批后续跑失败，请重试。");
            } finally {
                cancelFlags.remove(run.getId());
            }
        });
    }

    /** 工具调用决策：名单内挂起等审批，名单外直接执行进下一轮；未授权工具在执行前兜底拦截。 */
    private void handleToolCalls(AgentRunEntity run, List<ModelPort.ModelToolCall> calls,
                                 List<String> continuationNotes, int depth, List<String> allowedTools) {
        Long runId = run.getId();
        for (ModelPort.ModelToolCall call : calls) {
            ToolRegistry.ToolHandler handler = toolRegistry.find(call.name());
            if (handler == null) {
                // 模型幻觉出的工具名：以失败结果回注，让模型自行纠正
                continuationNotes.add("工具 " + call.name() + " 不存在，请改用其他方式或直接回答用户。");
                continue;
            }
            if (!toolRegistry.authorized(call.name(), allowedTools)) {
                // 未授权（模型越权调用或用户收窄了白名单）：不执行，回注结果让模型改写答复
                log.info("run {} 拦截未授权工具 {}", runId, call.name());
                continuationNotes.add("工具 " + call.name() + " 未授权给当前 Agent，"
                        + "请改用已授权工具，或直接回答用户。");
                continue;
            }
            // callId 两侧必须一致：tool_call 事件与 tool_result 事件用同一个 id，前端才能配对翻转状态
            // （此前 execArgs 漏放 __callId → tool_result 回空串 → 工具卡永远停在「执行中…」，run168 实测）
            String callId = call.id() == null ? UUID.randomUUID().toString() : call.id();
            appendEvent(runId, RuntimeEventType.TOOL_CALL, objectMapper.writeValueAsString(Map.of(
                    "callId", callId,
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
            execArgs.put("__callId", callId);
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
        // Agent 知识库绑定（v4.5.2）：search_docs 注入文档范围（四个工具执行点统一走这里，无需各自查）
        if ("search_docs".equals(toolName) && !args.containsKey("__kbDocIds")) {
            String kbScope = resolveKbDocIds(run);
            if (kbScope != null) {
                args.put("__kbDocIds", kbScope);
            }
        }
        long start = System.currentTimeMillis();
        String resultBrief;
        boolean ok;
        long childRunId = 0;
        List<String> refs = List.of();
        List<String> images = List.of();
        List<Map<String, Object>> artifactMetas = new ArrayList<>();
        try {
            Map<String, Object> result = new java.util.LinkedHashMap<>(handler.execute(args));
            // v4.5.7 主 Agent 协同可观测：工作流工具的子运行 ID 先提取到方法作用域（payload 段用）
            if (result.get("runId") instanceof Number childRid && result.get("workflow") != null) {
                childRunId = childRid.longValue();
            }
            // 图片旁路（v4.4.3）：data URI 只进 tool_result 事件给前端渲染，绝不进模型上下文（notes/结果回注）
            Object imgObj = result.remove("images");
            if (imgObj instanceof List<?> list) {
                images = list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
            }
            // 文件旁路（v4.4.4）：沙箱产物落库为运行文件产物，事件只带元数据供前端下载
            Object fileObj = result.remove("artifactFiles");
            if (fileObj instanceof List<?> rawFiles && !rawFiles.isEmpty()
                    && args.get("__runId") != null && args.get("__userId") != null) {
                Long uid = Long.valueOf(String.valueOf(args.get("__userId")));
                Long rid2 = Long.valueOf(String.valueOf(args.get("__runId")));
                for (Object o : rawFiles) {
                    if (o instanceof Map<?, ?> f && f.get("contentB64") instanceof String b64
                            && f.get("name") instanceof String name
                            && f.get("contentType") instanceof String ct) {
                        long artifactId = artifactService.save(uid, rid2, name, ct, b64);
                        artifactMetas.add(Map.of("id", artifactId, "name", name,
                                "contentType", ct, "sizeBytes", b64.length() / 4 * 3));
                    }
                }
            }
            ok = true;
            // v4.5.7：芯片摘要只留一行——run_wf 的 result 含 4000 字 output，全文拼 brief 会
            // 把瘦芯片撑爆（模型侧的 notes 仍带完整结果，不受影响）
            String joinedBrief = result.values().stream().map(String::valueOf)
                    .reduce((a, b) -> a + " / " + b).orElse("done");
            resultBrief = joinedBrief.length() > 200 ? joinedBrief.substring(0, 200) + "…" : joinedBrief;
            String imgNote = images.isEmpty() ? "" : "（另外已生成 " + images.size()
                    + " 张图表并直接展示给用户，无需重复描述图片内容）";
            String fileNote = artifactMetas.isEmpty() ? "" : "（已生成 " + artifactMetas.size()
                    + " 个文件并保存到运行产物，用户界面可下载，文件名："
                    + artifactMetas.stream().map(m -> m.get("name")).toList() + "）";
            notes.add("工具 " + toolName + " 已执行成功，结果：" + objectMapper.writeValueAsString(result)
                    + imgNote + fileNote + "。请基于该结果继续完成用户的任务。");
            // 引用溯源（v4.1）：search_docs 的命中文档标题随 tool_result 透出，前端渲染「依据」chip
            if ("search_docs".equals(toolName) && result.get("hits") instanceof List<?> hitList) {
                refs = hitList.stream()
                        .filter(h -> h instanceof Map<?, ?> m && m.get("title") != null)
                        .map(h -> String.valueOf(((Map<?, ?>) h).get("title")))
                        .limit(5).toList();
            }
        } catch (Exception e) {
            ok = false;
            resultBrief = "执行失败：" + e.getMessage();
            notes.add("工具 " + toolName + " 执行失败：" + e.getMessage() + "。请告知用户并尝试其他方式。");
        }
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("callId", String.valueOf(args.getOrDefault("__callId", "")));
        payload.put("ok", ok);
        payload.put("durationMs", System.currentTimeMillis() - start);
        payload.put("resultBrief", resultBrief);
        payload.put("resultChars", resultBrief.length());
        // v4.5.7 主 Agent 协同可观测：工作流工具的子运行 ID 随 tool_result 透出，
        // 前端芯片渲染「查看子运行」入口（打开运行详情弹窗看子工作流的节点瀑布）
        if (childRunId > 0) {
            payload.put("childRunId", childRunId);
        }
        if (!images.isEmpty()) {
            payload.put("images", images);
        }
        if (!artifactMetas.isEmpty()) {
            payload.put("artifacts", artifactMetas);
        }
        payload.put("ts", System.currentTimeMillis());
        if (!refs.isEmpty()) {
            payload.put("refs", refs);
        }
        appendEvent(run.getId(), RuntimeEventType.TOOL_RESULT, objectMapper.writeValueAsString(payload));
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

    private void flushToken(Long runId, StringBuilder buf, int stepIndex) {
        if (buf.isEmpty()) {
            return;
        }
        appendEvent(runId, RuntimeEventType.TOKEN, objectMapper.writeValueAsString(Map.of(
                "delta", buf.toString(),
                "index", 0,
                "stepIndex", Math.max(stepIndex, 0))));
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

    /** Agent 知识库绑定范围反查（v4.5.2）：run 挂载 Agent 的 knowledgeDocIds；无 Agent / 未绑定返回 null。 */
    private String resolveKbDocIds(AgentRunEntity run) {
        if (run.getAgentId() == null) {
            return null;
        }
        try {
            AgentConfigEntity agent = agentConfigService.findById(run.getAgentId());
            return agent == null ? null : agent.getKnowledgeDocIds();
        } catch (Exception e) {
            log.warn("run {} 反查知识库绑定失败：{}", run.getId(), e.getMessage());
            return null;
        }
    }

    /** 连接解析：Agent 绑定连接优先（须启用），否则用户最新启用连接。 */
    private ModelConnectionEntity resolveConnection(AgentRunEntity run, AgentConfigEntity agent) {
        ModelConnectionEntity conn;
        if (agent != null && agent.getModelConnectionId() != null) {
            conn = modelConnectionRepository.findByUser(run.getUserId()).stream()
                    .filter(c -> c.getId().equals(agent.getModelConnectionId())
                            && c.getStatus() != null && c.getStatus() == 1)
                    .findFirst()
                    .orElse(null);
        } else {
            conn = modelConnectionRepository.findByUser(run.getUserId()).stream()
                    .filter(c -> c.getStatus() != null && c.getStatus() == 1)
                    .reduce((first, second) -> second)
                    .orElse(null);
        }
        // 回填「实际用的模型」到 run（LLM 观测聚合用）；幂等：同连接不重复写
        if (conn != null && !conn.getId().equals(run.getModelConnId())) {
            run.setModelConnId(conn.getId());
            run.setModelName(firstModel(conn));
            runRepository.saveModelInfo(run.getId(), run.getModelName(), conn.getId());
        }
        return conn;
    }

    /** 连接的首选模型名：modelsJson 数组第一个，解析失败回退 provider。 */
    private String firstModel(ModelConnectionEntity conn) {
        try {
            List<?> models = objectMapper.readValue(conn.getModelsJson(), List.class);
            if (models != null && !models.isEmpty()) {
                return String.valueOf(models.get(0));
            }
        } catch (Exception ignored) {
            // modelsJson 为空/非法时回退 provider
        }
        return conn.getProvider();
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
