package com.lemon.lemonagent.dal.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * 演示工单仓储（demo_ticket）：工具审批"批准后数据库里真多了一行"的落点。
 */
@Repository
public class DemoTicketRepository {

    private final JdbcTemplate jdbcTemplate;

    public DemoTicketRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 插入工单，回填自增主键。
     *
     * @param userId   入参：创建者
     * @param runId    入参：来源运行
     * @param title    入参：标题
     * @param body     入参：内容
     * @param priority 入参：优先级
     * @return 出参：工单 ID
     */
    public long insert(Long userId, Long runId, String title, String body, String priority) {
        KeyHolder keys = new GeneratedKeyHolder();
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
        return keys.getKey().longValue();
    }

    /**
     * 统计来源运行的工单数（验证审批效果用）。
     *
     * @param runId 入参：运行 ID
     * @return 出参：工单数
     */
    public long countByRun(Long runId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM demo_ticket WHERE run_id = ?", Long.class, runId);
        return n == null ? 0 : n;
    }

    /** 占位引用避免未用告警（时间字段由 DB 默认值填充）。 */
    @SuppressWarnings("unused")
    private static Timestamp now() {
        return Timestamp.from(Instant.now());
    }

}
