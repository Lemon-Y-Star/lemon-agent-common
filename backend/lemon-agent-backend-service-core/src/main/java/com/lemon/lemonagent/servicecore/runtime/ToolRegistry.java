package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.model.runtime.ModelPort;
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

    private final Map<String, ToolHandler> tools;

    private final JdbcTemplate jdbcTemplate;

    public ToolRegistry(JdbcTemplate jdbcTemplate,
                        @Value("${agent.tools.approval-required:create_ticket}") String approvalList) {
        this.jdbcTemplate = jdbcTemplate;
        List<String> needsApproval = List.of(approvalList.split(",")).stream()
                .map(String::trim).toList();
        this.tools = Map.of(
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
        return tools.values().stream().map(h -> h.spec).toList();
    }

    /**
     * 按名取工具；未知工具返回 null（模型幻觉出的工具名在调用前被拦截）。
     */
    public ToolHandler find(String name) {
        return tools.get(name);
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
