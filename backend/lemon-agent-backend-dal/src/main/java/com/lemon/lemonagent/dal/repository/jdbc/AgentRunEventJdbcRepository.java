package com.lemon.lemonagent.dal.repository.jdbc;

import com.lemon.lemonagent.dal.entity.AgentRunEventEntity;
import com.lemon.lemonagent.dal.repository.AgentRunEventRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** 运行事件仓储的 JDBC 实现。 */
@Repository
public class AgentRunEventJdbcRepository implements AgentRunEventRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<AgentRunEventEntity> mapper = new RowMapper<>() {
        @Override
        public AgentRunEventEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            AgentRunEventEntity e = new AgentRunEventEntity();
            e.setId(rs.getLong("id"));
            e.setRunId(rs.getLong("run_id"));
            e.setSeq(rs.getLong("seq"));
            e.setEventType(rs.getString("event_type"));
            e.setPayload(rs.getString("payload"));
            e.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
            return e;
        }
    };

    public AgentRunEventJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int insert(AgentRunEventEntity e) {
        return jdbcTemplate.update(
                "INSERT INTO agent_run_event (run_id, seq, event_type, payload) VALUES (?,?,?,?)",
                e.getRunId(), e.getSeq(), e.getEventType(), e.getPayload());
    }

    @Override
    public List<AgentRunEventEntity> findByRunAfter(Long runId, long afterSeq) {
        return jdbcTemplate.query(
                "SELECT id, run_id, seq, event_type, payload, created_at FROM agent_run_event "
                        + "WHERE run_id = ? AND seq > ? ORDER BY seq",
                mapper, runId, afterSeq);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

}
