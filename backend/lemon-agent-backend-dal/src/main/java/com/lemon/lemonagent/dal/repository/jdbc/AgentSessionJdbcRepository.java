package com.lemon.lemonagent.dal.repository.jdbc;

import com.lemon.lemonagent.dal.entity.AgentSessionEntity;
import com.lemon.lemonagent.dal.repository.AgentSessionRepository;
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

/** 会话仓储的 JDBC 实现。 */
@Repository
public class AgentSessionJdbcRepository implements AgentSessionRepository {

    private static final String COLUMNS = "id, user_id, title, last_active_at, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<AgentSessionEntity> mapper = new RowMapper<>() {
        @Override
        public AgentSessionEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            AgentSessionEntity e = new AgentSessionEntity();
            e.setId(rs.getLong("id"));
            e.setUserId(rs.getLong("user_id"));
            e.setTitle(rs.getString("title"));
            e.setLastActiveAt(toInstant(rs.getTimestamp("last_active_at")));
            e.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
            e.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
            return e;
        }
    };

    public AgentSessionJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int insert(AgentSessionEntity e) {
        Instant now = Instant.now();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO agent_session (user_id, title, last_active_at, created_at, updated_at) VALUES (?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, e.getUserId());
            ps.setString(2, e.getTitle());
            ps.setTimestamp(3, Timestamp.from(now));
            ps.setTimestamp(4, Timestamp.from(now));
            ps.setTimestamp(5, Timestamp.from(now));
            return ps;
        }, keys);
        e.setId(keys.getKey().longValue());
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        e.setLastActiveAt(now);
        return 1;
    }

    @Override
    public AgentSessionEntity findByIdAndUser(Long id, Long userId) {
        List<AgentSessionEntity> list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM agent_session WHERE id = ? AND user_id = ?",
                mapper, id, userId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<AgentSessionEntity> findByUser(Long userId, int offset, int limit) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM agent_session WHERE user_id = ? ORDER BY last_active_at DESC LIMIT ? OFFSET ?",
                mapper, userId, limit, offset);
    }

    @Override
    public long countByUser(Long userId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_session WHERE user_id = ?", Long.class, userId);
        return n == null ? 0 : n;
    }

    @Override
    public int touch(Long id) {
        return jdbcTemplate.update(
                "UPDATE agent_session SET last_active_at = ? WHERE id = ?", Timestamp.from(Instant.now()), id);
    }

    @Override
    public long messageCount(Long id) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_message WHERE session_id = ?", Long.class, id);
        return n == null ? 0 : n;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

}
