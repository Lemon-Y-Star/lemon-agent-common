package com.lemon.lemonagent.dal.repository.jdbc;

import com.lemon.lemonagent.dal.entity.AgentRunEntity;
import com.lemon.lemonagent.dal.repository.AgentRunRepository;
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
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** 运行仓储的 JDBC 实现：状态机迁移全部是带前置条件的原子 UPDATE。 */
@Repository
public class AgentRunJdbcRepository implements AgentRunRepository {

    private static final String COLUMNS =
            "id, session_id, agent_id, user_id, client_request_id, status, strategy, trace_id, last_seq, "
                    + "output_text, error_code, error_msg, pending_approval_json, plan_json, plan_step, "
                    + "graph_json, graph_state_json, "
                    + "input_tokens, output_tokens, started_at, ended_at, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<AgentRunEntity> mapper = new RowMapper<>() {
        @Override
        public AgentRunEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            AgentRunEntity e = new AgentRunEntity();
            e.setId(rs.getLong("id"));
            e.setSessionId(rs.getLong("session_id"));
            long agentId = rs.getLong("agent_id");
            e.setAgentId(rs.wasNull() ? null : agentId);
            e.setUserId(rs.getLong("user_id"));
            e.setClientRequestId(rs.getString("client_request_id"));
            e.setStatus(rs.getString("status"));
            e.setStrategy(rs.getString("strategy"));
            e.setTraceId(rs.getString("trace_id"));
            e.setLastSeq(rs.getLong("last_seq"));
            e.setOutputText(rs.getString("output_text"));
            e.setErrorCode(rs.getString("error_code"));
            e.setErrorMsg(rs.getString("error_msg"));
            e.setPendingApprovalJson(rs.getString("pending_approval_json"));
            e.setPlanJson(rs.getString("plan_json"));
            e.setPlanStep(rs.getInt("plan_step"));
            e.setGraphJson(rs.getString("graph_json"));
            e.setGraphStateJson(rs.getString("graph_state_json"));
            e.setInputTokens((Integer) rs.getObject("input_tokens"));
            e.setOutputTokens((Integer) rs.getObject("output_tokens"));
            e.setStartedAt(toInstant(rs.getTimestamp("started_at")));
            e.setEndedAt(toInstant(rs.getTimestamp("ended_at")));
            e.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
            e.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
            return e;
        }
    };

    public AgentRunJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int insert(AgentRunEntity e) {
        Instant now = Instant.now();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO agent_run (session_id, agent_id, user_id, client_request_id, status, strategy, "
                            + "trace_id, last_seq, started_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, e.getSessionId());
            if (e.getAgentId() == null) {
                ps.setNull(2, Types.BIGINT);
            } else {
                ps.setLong(2, e.getAgentId());
            }
            ps.setLong(3, e.getUserId());
            ps.setString(4, e.getClientRequestId());
            ps.setString(5, e.getStatus());
            ps.setString(6, e.getStrategy());
            ps.setString(7, e.getTraceId());
            ps.setLong(8, e.getLastSeq());
            ps.setTimestamp(9, Timestamp.from(now));
            return ps;
        }, keys);
        e.setId(keys.getKey().longValue());
        e.setStartedAt(now);
        return 1;
    }

    @Override
    public AgentRunEntity findByClientRequestId(Long userId, String clientRequestId) {
        List<AgentRunEntity> list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM agent_run WHERE user_id = ? AND client_request_id = ?",
                mapper, userId, clientRequestId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public AgentRunEntity findByIdAndUser(Long id, Long userId) {
        List<AgentRunEntity> list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM agent_run WHERE id = ? AND user_id = ?",
                mapper, id, userId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public AgentRunEntity findActiveBySession(Long sessionId) {
        List<AgentRunEntity> list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM agent_run WHERE session_id = ? AND status IN ('RUNNING','BLOCKED') "
                        + "ORDER BY id DESC LIMIT 1",
                mapper, sessionId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public int transition(Long id, String from, String to, String errorCode, String errorMsg) {
        return jdbcTemplate.update(
                "UPDATE agent_run SET status = ?, error_code = ?, error_msg = ?, "
                        + "ended_at = CASE WHEN ? IN ('COMPLETED','FAILED','CANCELLED','TIMEOUT') THEN ? ELSE ended_at END "
                        + "WHERE id = ? AND status = ?",
                to, errorCode, errorMsg, to, Timestamp.from(Instant.now()), id, from);
    }

    @Override
    public int finish(Long id, String to, String outputText, String errorCode, String errorMsg,
                      Integer inputTokens, Integer outputTokens) {
        return jdbcTemplate.update(
                "UPDATE agent_run SET status = ?, output_text = ?, error_code = ?, error_msg = ?, "
                        + "input_tokens = ?, output_tokens = ?, ended_at = ? WHERE id = ? AND status = 'RUNNING'",
                to, outputText, errorCode, errorMsg, inputTokens, outputTokens,
                Timestamp.from(Instant.now()), id);
    }

    @Override
    public List<AgentRunEntity> findBySession(Long sessionId, Long userId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM agent_run WHERE session_id = ? AND user_id = ? ORDER BY id",
                mapper, sessionId, userId);
    }

    @Override
    public List<AgentRunEntity> findByUser(Long userId, String status, Long sessionId, int offset, int limit) {
        StringBuilder sql = new StringBuilder(
                "SELECT " + COLUMNS + " FROM agent_run WHERE user_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        if (sessionId != null) {
            sql.append(" AND session_id = ?");
            args.add(sessionId);
        }
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), mapper, args.toArray());
    }

    @Override
    public long countByUser(Long userId, String status, Long sessionId) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM agent_run WHERE user_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        if (sessionId != null) {
            sql.append(" AND session_id = ?");
            args.add(sessionId);
        }
        Long n = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
        return n == null ? 0 : n;
    }

    @Override
    public int updateLastSeq(Long id, long seq) {
        return jdbcTemplate.update("UPDATE agent_run SET last_seq = ? WHERE id = ?", seq, id);
    }

    @Override
    public int suspend(Long id, String approvalId, String pendingJson) {
        return jdbcTemplate.update(
                "UPDATE agent_run SET status = 'BLOCKED', pending_approval_json = ? "
                        + "WHERE id = ? AND status = 'RUNNING'",
                pendingJson, id);
    }

    @Override
    public int clearPendingApproval(Long id) {
        return jdbcTemplate.update(
                "UPDATE agent_run SET pending_approval_json = NULL WHERE id = ?", id);
    }

    @Override
    public int savePlan(Long id, String planJson, int planStep) {
        return jdbcTemplate.update(
                "UPDATE agent_run SET plan_json = ?, plan_step = ? WHERE id = ?", planJson, planStep, id);
    }

    @Override
    public int saveGraph(Long id, String graphJson, String stateJson) {
        return jdbcTemplate.update(
                "UPDATE agent_run SET graph_json = ?, graph_state_json = ? WHERE id = ?",
                graphJson, stateJson, id);
    }

    @Override
    public int saveGraphState(Long id, String stateJson) {
        return jdbcTemplate.update(
                "UPDATE agent_run SET graph_state_json = ? WHERE id = ?", stateJson, id);
    }

    @Override
    public List<AgentRunEntity> findByStatus(String status, int limit) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM agent_run WHERE status = ? ORDER BY id LIMIT ?",
                mapper, status, limit);
    }

    @Override
    public int markOrphansAsInterrupted() {
        return jdbcTemplate.update(
                "UPDATE agent_run SET status = 'FAILED', error_code = 'INTERRUPTED', "
                        + "error_msg = '服务重启导致本次执行中断，请重新发起。', ended_at = ? "
                        + "WHERE status IN ('RUNNING','BLOCKED')",
                Timestamp.from(Instant.now()));
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

}
