package com.lemon.lemonagent.dal.repository.jdbc;

import com.lemon.lemonagent.dal.entity.AgentMessageEntity;
import com.lemon.lemonagent.dal.repository.AgentMessageRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** 消息仓储的 JDBC 实现。 */
@Repository
public class AgentMessageJdbcRepository implements AgentMessageRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<AgentMessageEntity> mapper = new RowMapper<>() {
        @Override
        public AgentMessageEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            AgentMessageEntity e = new AgentMessageEntity();
            e.setId(rs.getLong("id"));
            e.setSessionId(rs.getLong("session_id"));
            e.setUserId(rs.getLong("user_id"));
            e.setRole(rs.getString("role"));
            e.setContent(rs.getString("content"));
            long runId = rs.getLong("run_id");
            e.setRunId(rs.wasNull() ? null : runId);
            e.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
            return e;
        }
    };

    public AgentMessageJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int insert(AgentMessageEntity e) {
        Instant now = Instant.now();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO agent_message (session_id, user_id, role, content, run_id, created_at) VALUES (?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, e.getSessionId());
            ps.setLong(2, e.getUserId());
            ps.setString(3, e.getRole());
            ps.setString(4, e.getContent());
            if (e.getRunId() == null) {
                ps.setNull(5, java.sql.Types.BIGINT);
            } else {
                ps.setLong(5, e.getRunId());
            }
            ps.setTimestamp(6, Timestamp.from(now));
            return ps;
        }, keys);
        e.setId(keys.getKey().longValue());
        e.setCreatedAt(now);
        return 1;
    }

    @Override
    public List<AgentMessageEntity> findBySession(Long sessionId, Long userId) {
        return jdbcTemplate.query(
                "SELECT id, session_id, user_id, role, content, run_id, created_at FROM agent_message "
                        + "WHERE session_id = ? AND user_id = ? ORDER BY id",
                mapper, sessionId, userId);
    }

    @Override
    public long countBySession(Long sessionId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_message WHERE session_id = ?", Long.class, sessionId);
        return n == null ? 0 : n;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

}
