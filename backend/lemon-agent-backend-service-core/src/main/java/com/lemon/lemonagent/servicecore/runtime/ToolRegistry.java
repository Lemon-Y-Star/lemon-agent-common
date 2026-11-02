package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.dal.entity.AgentRunEntity;
import com.lemon.lemonagent.dal.entity.McpConnectionEntity;
import com.lemon.lemonagent.dal.entity.WorkflowEntity;
import com.lemon.lemonagent.dal.repository.AgentRunRepository;
import com.lemon.lemonagent.dal.repository.McpConnectionRepository;
import com.lemon.lemonagent.dal.repository.WorkflowRepository;
import com.lemon.lemonagent.model.mcp.McpToolPort;
import com.lemon.lemonagent.model.runtime.CodeSandboxPort;
import com.lemon.lemonagent.model.runtime.ModelPort;
import com.lemon.lemonagent.model.runtime.RetrievalPort;
import com.lemon.lemonagent.model.runtime.RunStatus;
import com.lemon.lemonagent.util.crypto.CryptoUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 工具注册表：定义、审批名单、执行（BUILD-SPEC §1.3）。
 *
 * <p>两个工具，一读一写，证明"不是所有调用都要审批"：
 * <ul>
 *   <li>{@code current_time}：只读，直接执行；</li>
 *   <li>{@code create_ticket}：写操作（demo_ticket 落库），在审批名单内 → 挂起等审批。</li>
 * </ul>
 *
 * <p>审批策略不写死在代码里：名单来自 {@code agent.tools.approval-required}（yml），
 * 显式白名单 + 默认拒绝——按工具名判定而不是"读写自动判定"，因为自动判定不可靠
 * （一个叫 {@code query_xxx} 的工具也可能有副作用），显式名单才可审计。</p>
 *
 * <p>工具描述必须写清"是否改变外部状态"——模型靠这段描述判断该不该调用。</p>
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    /** 工具句柄：定义 + 审批标记 + 执行体。 */
    public static class ToolHandler {

        /** 工具定义（下发给模型）。 */
        public final ModelPort.ToolSpec spec;

        /** 是否需要审批（审批名单内为 true）。 */
        public final boolean needsApproval;

        private final Function<Map<String, Object>, Map<String, Object>> executor;

        ToolHandler(ModelPort.ToolSpec spec, boolean needsApproval,
                    Function<Map<String, Object>, Map<String, Object>> executor) {
            this.spec = spec;
            this.needsApproval = needsApproval;
            this.executor = executor;
        }

        /** 执行工具。 */
        public Map<String, Object> execute(Map<String, Object> args) {
            return executor.apply(args);
        }
    }

    private final Map<String, ToolHandler> fixedTools;

    /** 检索工具（可空：embedding 未配置时不注册，对话不受影响）。 */
    private final ToolHandler searchDocsTool;

    /** Python 沙箱工具（v4.4）：执行环境在沙箱容器，本进程只转发。 */
    private final ToolHandler runPythonTool;

    /** 发布为工具的工作流（run_wf_<id>）：启动回放 + 创建/删除时动态绑定。 */
    private final Map<String, ToolHandler> workflowTools = new java.util.concurrent.ConcurrentHashMap<>();

    /** 工具名 → 发布者（工具目录按用户过滤）。 */
    private final Map<String, Long> workflowOwners = new java.util.concurrent.ConcurrentHashMap<>();

    /** MCP 桥接工具（mcp<connId>_<tool>）：连接启用时注册，停用/删除/探测失败时解除。 */
    private final Map<String, ToolHandler> mcpTools = new ConcurrentHashMap<>();

    /** MCP 工具名 → 归属用户（0 = 平台公共；ConcurrentHashMap 不收 null 值用 0 代替）。 */
    private final Map<String, Long> mcpOwners = new ConcurrentHashMap<>();

    /** MCP 工具名 → 连接描述（执行时重新发起远端调用用）。 */
    private final Map<String, McpToolPort.ConnSpec> mcpConns = new ConcurrentHashMap<>();

    /** 工具名 → 展示标签（目录下拉中文显示用；内置工具在 catalog 里硬编码）。 */
    private final Map<String, String> toolLabels = new ConcurrentHashMap<>();

    private final JdbcTemplate jdbcTemplate;

    private final WorkflowRepository workflowRepository;

    private final AgentRunRepository agentRunRepository;

    /** 延迟获取运行时服务（发起子运行）；ObjectProvider 打破 ToolRegistry ↔ AgentRuntimeService 构造环。 */
    private final ObjectProvider<AgentRuntimeService> agentRuntimeProvider;

    /** 审批名单（yml `agent.tools.approval-required`）：目录接口要回传 needsApproval，故留字段。 */
    private final List<String> approvalList;

    /** MCP 工具端口（可空：integration 未装配时不桥接，主流程不受影响）。 */
    private final McpToolPort mcpToolPort;

    private final McpConnectionRepository mcpConnectionRepository;

    /** 代码沙箱端口（可空：沙箱服务缺席时工具仍注册，执行时返回明确错误）。 */
    private final CodeSandboxPort codeSandbox;

    public ToolRegistry(JdbcTemplate jdbcTemplate,
                        RetrievalPort retrievalPort,
                        KnowledgeService knowledgeService,
                        WorkflowRepository workflowRepository,
                        AgentRunRepository agentRunRepository,
                        ObjectProvider<AgentRuntimeService> agentRuntimeProvider,
                        ObjectProvider<McpToolPort> mcpToolPortProvider,
                        McpConnectionRepository mcpConnectionRepository,
                        ObjectProvider<CodeSandboxPort> codeSandboxProvider,
                        @Value("${agent.tools.approval-required:create_ticket}") String approvalList) {
        this.jdbcTemplate = jdbcTemplate;
        this.workflowRepository = workflowRepository;
        this.agentRunRepository = agentRunRepository;
        this.agentRuntimeProvider = agentRuntimeProvider;
        List<String> needsApproval = List.of(approvalList.split(",")).stream()
                .map(String::trim).toList();
        this.approvalList = needsApproval;
        this.mcpToolPort = mcpToolPortProvider.getIfAvailable();
        this.mcpConnectionRepository = mcpConnectionRepository;
        this.codeSandbox = codeSandboxProvider.getIfAvailable();
        // search_docs 常驻：没配 embedding 时走关键词检索（v3.6 双路降级），配了再叠加向量
        this.searchDocsTool = new ToolHandler(new ModelPort.ToolSpec(
                "search_docs",
                "检索用户知识库（只读工具，不改变任何外部状态）。当用户的问题可能涉及已上传的文档、"
                        + "架构设计、接口约定或操作流程时，先调用本工具检索再回答。",
                "{\"type\":\"object\",\"properties\":{"
                        + "\"query\":{\"type\":\"string\",\"description\":\"检索语句\"},"
                        + "\"topK\":{\"type\":\"integer\",\"description\":\"召回条数，默认 5，最大 10\"}},"
                        + "\"required\":[\"query\"]}"),
                needsApproval.contains("search_docs"),
                args -> {
                    String query = String.valueOf(args.getOrDefault("query", ""));
                    int topK = Integer.parseInt(String.valueOf(args.getOrDefault("topK", "5")));
                    Long userId = Long.valueOf(String.valueOf(args.getOrDefault("__userId", "0")));
                    List<com.lemon.lemonagent.model.runtime.KbSearchHit> hits =
                            knowledgeService.search(userId, query, topK);
                    // 空召回显式 {hits:[]}（AC-25 第一道闸）：不抛异常、不兜底
                    List<Map<String, Object>> hitMaps = hits.stream()
                            .map(h -> {
                                Map<String, Object> m = new java.util.LinkedHashMap<>();
                                m.put("title", h.title());
                                m.put("snippet", h.snippet());
                                m.put("source", h.source());
                                if (h.images() != null && !h.images().isEmpty()) {
                                    // 多模态随文带出（v4.1）：模型可引用图片链接回答
                                    m.put("images", h.images());
                                }
                                return m;
                            })
                            .collect(java.util.stream.Collectors.toList());
                    return Map.of("hits", (Object) hitMaps);
                });
        this.fixedTools = Map.of(
                "current_time", new ToolHandler(new ModelPort.ToolSpec(
                        "current_time",
                        "获取服务器当前时间（只读工具，不改变任何外部状态）。",
                        "{\"type\":\"object\",\"properties\":{},\"required\":[]}"),
                        needsApproval.contains("current_time"),
                        args -> Map.of("currentTime", LocalDateTime.now().toString())),
                "create_ticket", new ToolHandler(new ModelPort.ToolSpec(
                        "create_ticket",
                        "创建一个演示工单（写操作，会改变外部状态：向工单表插入一行记录）。",
                        "{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\",\"description\":\"工单标题\"},"
                                + "\"body\":{\"type\":\"string\",\"description\":\"工单内容\"},"
                                + "\"priority\":{\"type\":\"string\",\"enum\":[\"P0\",\"P1\",\"P2\"]}},"
                                + "\"required\":[\"title\",\"priority\"]}"),
                        needsApproval.contains("create_ticket"),
                        this::createTicket));
        // run_python 常驻（v4.4）：代码在隔离沙箱执行（禁网 + rlimit + 超时），写操作风险由沙箱吸收
        this.runPythonTool = new ToolHandler(new ModelPort.ToolSpec(
                "run_python",
                "在隔离沙箱中执行一段 Python 代码并返回 stdout/stderr（不改变外部状态，代码无法联网）。"
                        + "适合精确计算、数据处理、格式转换、算法验证、绘制图表等模型心算不可靠的场景；"
                        + "环境已预装 numpy/pandas/matplotlib。代码必须用 print() 输出结果；"
                        + "画图时用 matplotlib 并 plt.savefig('名称.png', dpi=120, bbox_inches='tight')，"
                        + "保存的图片会自动展示在用户界面（不要用 plt.show，不要把 base64 打进 stdout）。",
                "{\"type\":\"object\",\"properties\":{"
                        + "\"code\":{\"type\":\"string\",\"description\":\"要执行的 Python 代码（Python 3.11）\"}},"
                        + "\"required\":[\"code\"]}"),
                needsApproval.contains("run_python"),
                args -> {
                    if (codeSandbox == null) {
                        return Map.of("error", "沙箱服务未就绪（LEMON_SANDBOX_URL 未配置或服务未启动）");
                    }
                    String code = String.valueOf(args.getOrDefault("code", ""));
                    try {
                        CodeSandboxPort.ExecResult r = codeSandbox.exec(code, List.of(), 15);
                        if (r.timedOut()) {
                            return Map.of("error", "代码执行超时（超过 15 秒被强制终止）", "stdout", r.stdout());
                        }
                        Map<String, Object> out = new java.util.LinkedHashMap<>();
                        out.put("stdout", r.stdout());
                        if (r.stderr() != null && !r.stderr().isBlank()) {
                            out.put("stderr", r.stderr());
                        }
                        out.put("exitCode", r.exitCode());
                        out.put("durationMs", r.durationMs());
                        // 图片直通前端（v4.4.3）：data URI 只进 tool_result 事件，绝不进模型上下文
                        if (r.images() != null && !r.images().isEmpty()) {
                            out.put("images", r.images());
                        }
                        return out;
                    } catch (Exception e) {
                        return Map.of("error", "沙箱调用失败：" + e.getMessage());
                    }
                });
        // 启动回放：把库里全部启用中的工作流发布为工具（run_wf_<id>）
        workflowRepository.findAllEnabled().forEach(this::bindWorkflow);
    }

    /**
     * 把工作流发布为工具（PRD FR-25.4 最小版）：工具名 {@code run_wf_<id>}，
     * Agent 在工具白名单里勾选即完成挂载；执行 = 以调用方身份发起一次 custom 子运行
     * （独立会话，跑工作流**当前生效版本**），轮询到终态并返回产出。
     *
     * @param wf 入参：工作流（须已有 ID）
     */
    public void bindWorkflow(WorkflowEntity wf) {
        String toolName = "run_wf_" + wf.getId();
        String desc = "调用已发布的工作流「" + wf.getName() + "」"
                + (wf.getDescription() == null || wf.getDescription().isBlank() ? "" : "：" + wf.getDescription())
                + "。工具按该工作流的编排（大模型 / 工具 / 审批节点）完成 input 描述的任务并返回最终产出；"
                + "只读编排，除工作流自身节点外不改变外部状态。不要在工作流或其它工具里嵌套调用本工具。";
        workflowTools.put(toolName, new ToolHandler(new ModelPort.ToolSpec(
                toolName, desc,
                "{\"type\":\"object\",\"properties\":{"
                        + "\"input\":{\"type\":\"string\",\"description\":\"交给工作流的任务描述\"}},"
                        + "\"required\":[\"input\"]}"),
                false,
                args -> runWorkflowTool(wf.getId(), args)));
        workflowOwners.put(toolName, wf.getUserId());
        toolLabels.put(toolName, "工作流：" + wf.getName());
    }

    /** 工作流删除时解绑（run_wf_<id> 立即从目录与装配中消失）。 */
    public void unbindWorkflow(Long workflowId) {
        String toolName = "run_wf_" + workflowId;
        workflowTools.remove(toolName);
        workflowOwners.remove(toolName);
        toolLabels.remove(toolName);
    }

    // ==================================================================
    // MCP 桥接（PRD FR-12）：连接 = 工具源，启动回放 + CRUD 联动
    // ==================================================================

    /** 启动回放：全部启用中的 MCP 连接异步探测并注册为工具（不阻塞启动）。 */
    @EventListener(ApplicationReadyEvent.class)
    public void bindAllMcp() {
        if (mcpToolPort == null) {
            return;
        }
        Thread.ofVirtual().name("mcp-bind").start(() -> {
            for (McpConnectionEntity c : mcpConnectionRepository.findAll()) {
                if (c.getStatus() != null && c.getStatus() == 1) {
                    bindMcp(c);
                }
            }
        });
    }

    /**
     * 把一条 MCP 连接注册为工具：tools/list 探测成功 → 逐个注册
     * {@code mcp<connId>_<tool>}；失败 → 写 unavailable_reason（连接页可见，不抛异常）。
     *
     * @param entity 入参：连接实体（须已有 ID）
     */
    public void bindMcp(McpConnectionEntity entity) {
        if (mcpToolPort == null) {
            return;
        }
        unbindMcp(entity.getId());
        McpToolPort.ConnSpec conn = toConnSpec(entity);
        try {
            List<McpToolPort.ToolDef> defs = mcpToolPort.listTools(conn);
            if (defs.isEmpty()) {
                markUnavailable(entity.getId(), "连接可用但未提供任何工具");
                return;
            }
            for (McpToolPort.ToolDef def : defs) {
                String toolName = mcpToolName(entity.getId(), def.name());
                String desc = "MCP「" + entity.getName() + "」提供。" + def.description();
                mcpTools.put(toolName, new ToolHandler(new ModelPort.ToolSpec(
                                toolName, desc, def.inputSchemaJson()),
                        approvalList.contains(toolName) || approvalList.contains(def.name()),
                        args -> callMcpTool(toolName, def.name(), args)));
                // 0 = 平台公共（userId NULL）；自建连接记归属用户
                mcpOwners.put(toolName, entity.getUserId() == null ? 0L : entity.getUserId());
                mcpConns.put(toolName, conn);
                toolLabels.put(toolName, "MCP·" + entity.getName() + " / " + def.name());
            }
            mcpConnectionRepository.updateUnavailable(entity.getId(), null);
            log.info("MCP 工具桥：连接 {}({}) 注册 {} 个工具", entity.getName(), entity.getId(), defs.size());
        } catch (Exception e) {
            String reason = "工具探测失败：" + rootMessage(e);
            markUnavailable(entity.getId(), reason);
            log.warn("MCP 工具桥：连接 {}({}) 不可用 - {}", entity.getName(), entity.getId(), reason);
        }
    }

    /** 连接停用/删除/改配置时解绑：该连接的全部工具立即从目录与装配中消失。 */
    public void unbindMcp(Long connId) {
        String prefix = "mcp" + connId + "_";
        mcpTools.keySet().removeIf(n -> n.startsWith(prefix));
        mcpOwners.keySet().removeIf(n -> n.startsWith(prefix));
        mcpConns.keySet().removeIf(n -> n.startsWith(prefix));
        toolLabels.keySet().removeIf(n -> n.startsWith(prefix));
    }

    /** MCP 工具执行体：调远端 tools/call，异常折算为 error 键（让模型能看到失败原因自行重试/改道）。 */
    private Map<String, Object> callMcpTool(String toolName, String remoteName, Map<String, Object> args) {
        McpToolPort.ConnSpec conn = mcpConns.get(toolName);
        if (conn == null) {
            return Map.of("error", "工具已下线（连接被停用或删除）");
        }
        try {
            return mcpToolPort.callTool(conn, remoteName, args);
        } catch (Exception e) {
            return Map.of("error", "MCP 调用失败：" + rootMessage(e));
        }
    }

    private McpToolPort.ConnSpec toConnSpec(McpConnectionEntity e) {
        String secret = e.getSecretCipher() == null || e.getSecretCipher().isBlank()
                ? null : CryptoUtil.decrypt(e.getSecretCipher());
        return new McpToolPort.ConnSpec(e.getId(), e.getName(), e.getTransportType(),
                e.getUrl(), e.getCommand(), secret);
    }

    /** 工具名 = mcp<connId>_<远端名>（非 [a-zA-Z0-9_] 字符折叠为下划线，保证模型可合法调用）。 */
    private static String mcpToolName(long connId, String remoteName) {
        String safe = remoteName.replaceAll("[^a-zA-Z0-9_]", "_");
        return "mcp" + connId + "_" + safe;
    }

    private void markUnavailable(Long connId, String reason) {
        try {
            mcpConnectionRepository.updateUnavailable(connId, reason);
        } catch (Exception ignored) {
            // 标注失败不影响主流程
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null || msg.isBlank() ? cur.getClass().getSimpleName() : msg;
    }

    /**
     * run_wf_* 执行体：发起子运行并轮询到终态（上限 120s）。
     *
     * <p>子运行 = 独立会话 + custom 策略 + 宿主 Agent（父运行的 agentId），
     * 复用 {@code createRun} 的幂等/互斥/越权口径；图执行内不装配 run_wf_*
     * （见 RunExecutor#graphTools），天然防「图→工作流→图」嵌套递归。</p>
     */
    private Map<String, Object> runWorkflowTool(long workflowId, Map<String, Object> args) {
        Long userId = Long.valueOf(String.valueOf(args.getOrDefault("__userId", "0")));
        Long parentRunId = Long.valueOf(String.valueOf(args.getOrDefault("__runId", "0")));
        WorkflowEntity wf = workflowRepository.findById(workflowId);
        if (wf == null || !wf.getUserId().equals(userId)) {
            return Map.of("error", "工作流不存在或无权调用");
        }
        if (workflowRepository.findActive(workflowId) == null) {
            return Map.of("error", "工作流「" + wf.getName() + "」还没有可用版本");
        }
        AgentRunEntity parent = agentRunRepository.findByIdAndUser(parentRunId, userId);
        if (parent == null || parent.getAgentId() == null) {
            return Map.of("error", "无法确定宿主 Agent");
        }
        String input = String.valueOf(args.getOrDefault("input", ""));
        String clientRequestId = "wftool_" + parentRunId + "_" + UUID.randomUUID().toString().substring(0, 8);
        var child = agentRuntimeProvider.getObject().createRun(userId, null, parent.getAgentId(),
                input, "custom", clientRequestId, null);
        long childRunId = child.getRunId();
        long deadline = System.currentTimeMillis() + 120_000L;
        AgentRunEntity done = null;
        while (System.currentTimeMillis() < deadline) {
            AgentRunEntity e = agentRunRepository.findByIdAndUser(childRunId, userId);
            if (e != null && RunStatus.valueOf(e.getStatus()).terminal()) {
                done = e;
                break;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (done == null) {
            return Map.of("workflow", wf.getName(), "runId", childRunId, "status", "RUNNING",
                    "note", "工作流仍在执行（超过 120s），可在运行历史查看后续产出");
        }
        String output = done.getOutputText() == null ? "" : done.getOutputText();
        if (output.length() > 4000) {
            output = output.substring(0, 4000) + "…（已截断）";
        }
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("workflow", wf.getName());
        result.put("runId", childRunId);
        result.put("status", done.getStatus());
        result.put("output", output);
        if (done.getErrorCode() != null) {
            result.put("errorCode", done.getErrorCode());
        }
        return result;
    }

    /**
     * 全部工具定义（下发给模型）。
     *
     * @return 出参：工具清单
     */
    public List<ModelPort.ToolSpec> specs() {
        List<ModelPort.ToolSpec> result = new java.util.ArrayList<>();
        result.add(fixedTools.get("current_time").spec);
        if (searchDocsTool != null) {
            result.add(searchDocsTool.spec);
        }
        result.add(fixedTools.get("create_ticket").spec);
        if (runPythonTool != null) {
            result.add(runPythonTool.spec);
        }
        workflowTools.values().forEach(h -> result.add(h.spec));
        mcpTools.values().forEach(h -> result.add(h.spec));
        return result;
    }

    /**
     * 按 Agent 授权清单过滤后的工具定义（v3.0 工具授权）。
     *
     * @param allowed 入参：授权的工具名；null = 全部（旧数据与未配置的 Agent）
     * @return 出参：下发给模型的工具清单
     */
    public List<ModelPort.ToolSpec> specs(java.util.Collection<String> allowed) {
        List<ModelPort.ToolSpec> all = specs();
        if (allowed == null) {
            return all;
        }
        return all.stream().filter(spec -> allowed.contains(spec.name())).toList();
    }

    /**
     * 全部已注册工具名（顺序：只读 → 检索 → 写操作）。
     *
     * @return 出参：工具名清单
     */
    public List<String> names() {
        return specs().stream().map(ModelPort.ToolSpec::name).toList();
    }

    /**
     * 可授权工具目录（前端勾选用）：含审批标记与可用性。
     *
     * <p>run_wf_* 只对发布者可见——工具目录按当前用户过滤。</p>
     *
     * @param userId 入参：当前用户
     * @return 出参：工具目录
     */
    public List<com.lemon.lemonagent.model.runtime.ToolInfoData> catalog(Long userId) {
        List<com.lemon.lemonagent.model.runtime.ToolInfoData> list = new java.util.ArrayList<>();
        list.add(withLabel(toInfo(fixedTools.get("current_time"), true), "当前时间"));
        list.add(withLabel(toInfo(searchDocsTool, true), "知识库检索"));
        list.add(withLabel(toInfo(fixedTools.get("create_ticket"), true), "创建工单（演示写操作）"));
        if (runPythonTool != null) {
            list.add(withLabel(toInfo(runPythonTool, true), "Python 代码执行（沙箱）"));
        }
        workflowTools.forEach((name, handler) -> {
            Long owner = workflowOwners.get(name);
            if (owner != null && owner.equals(userId)) {
                list.add(toInfo(handler, true));
            }
        });
        mcpTools.forEach((name, handler) -> {
            Long owner = mcpOwners.get(name);
            // 0 = 平台公共连接：所有用户可见；自建连接只对归属者可见
            if (owner == null || owner == 0L || owner.equals(userId)) {
                list.add(toInfo(handler, true));
            }
        });
        return list;
    }

    /**
     * 工具是否在授权清单内（null 清单 = 全部允许）。模型幻觉出未授权工具时，执行前兜底拦截。
     *
     * @param name    入参：工具名
     * @param allowed 入参：授权清单（可空）
     * @return 出参：是否允许
     */
    public boolean authorized(String name, java.util.Collection<String> allowed) {
        return allowed == null || allowed.contains(name);
    }

    private com.lemon.lemonagent.model.runtime.ToolInfoData toInfo(ToolHandler handler, boolean available) {
        com.lemon.lemonagent.model.runtime.ToolInfoData info =
                new com.lemon.lemonagent.model.runtime.ToolInfoData();
        info.setName(handler.spec.name());
        info.setDescription(handler.spec.description());
        info.setNeedsApproval(handler.needsApproval);
        info.setAvailable(available);
        info.setLabel(toolLabels.get(handler.spec.name()));
        return info;
    }

    /** 覆写展示标签（内置工具中文名）。 */
    private com.lemon.lemonagent.model.runtime.ToolInfoData withLabel(
            com.lemon.lemonagent.model.runtime.ToolInfoData info, String label) {
        info.setLabel(label);
        return info;
    }

    /**
     * 按名取工具；未知工具返回 null（模型幻觉出的工具名在调用前被拦截）。
     */
    public ToolHandler find(String name) {
        if ("search_docs".equals(name) && searchDocsTool != null) {
            return searchDocsTool;
        }
        if ("run_python".equals(name) && runPythonTool != null) {
            return runPythonTool;
        }
        ToolHandler workflowTool = workflowTools.get(name);
        if (workflowTool != null) {
            return workflowTool;
        }
        ToolHandler mcpTool = mcpTools.get(name);
        if (mcpTool != null) {
            return mcpTool;
        }
        return fixedTools.get(name);
    }

    /** create_ticket 执行体：真的写一行 demo_ticket，"批准之后数据库里真多了一行"。 */
    private Map<String, Object> createTicket(Map<String, Object> args) {
        String title = String.valueOf(args.getOrDefault("title", "未命名工单"));
        String body = String.valueOf(args.getOrDefault("body", ""));
        String priority = String.valueOf(args.getOrDefault("priority", "P2"));
        Long runId = Long.valueOf(String.valueOf(args.get("__runId")));
        Long userId = Long.valueOf(String.valueOf(args.get("__userId")));

        var keys = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO demo_ticket (user_id, run_id, title, body, priority) VALUES (?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, userId);
            ps.setLong(2, runId);
            ps.setString(3, title);
            ps.setString(4, body);
            ps.setString(5, priority);
            return ps;
        }, keys);
        long ticketId = keys.getKey().longValue();
        return Map.of("ticketId", ticketId, "url", "/tickets/" + ticketId, "title", title);
    }

}
