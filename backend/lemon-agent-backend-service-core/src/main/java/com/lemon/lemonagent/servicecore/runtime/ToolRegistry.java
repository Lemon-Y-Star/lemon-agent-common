package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.model.runtime.ModelPort;
import com.lemon.lemonagent.model.runtime.RetrievalPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

    private final JdbcTemplate jdbcTemplate;

    /** 审批名单（yml `agent.tools.approval-required`）：目录接口要回传 needsApproval，故留字段。 */
    private final List<String> approvalList;

    public ToolRegistry(JdbcTemplate jdbcTemplate,
                        RetrievalPort retrievalPort,
                        KnowledgeService knowledgeService,
                        @Value("${agent.tools.approval-required:create_ticket}") String approvalList) {
        this.jdbcTemplate = jdbcTemplate;
        List<String> needsApproval = List.of(approvalList.split(",")).stream()
                .map(String::trim).toList();
        this.approvalList = needsApproval;
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
     * @return 出参：工具目录
     */
    public List<com.lemon.lemonagent.model.runtime.ToolInfoData> catalog() {
        List<com.lemon.lemonagent.model.runtime.ToolInfoData> list = new java.util.ArrayList<>();
        list.add(toInfo(fixedTools.get("current_time"), true));
        list.add(toInfo(searchDocsTool, true));
        list.add(toInfo(fixedTools.get("create_ticket"), true));
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
        return info;
    }

    /**
     * 按名取工具；未知工具返回 null（模型幻觉出的工具名在调用前被拦截）。
     */
    public ToolHandler find(String name) {
        if ("search_docs".equals(name) && searchDocsTool != null) {
            return searchDocsTool;
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
