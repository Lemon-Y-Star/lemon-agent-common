package com.lemon.lemonagent.dal.repository.jdbc;

import com.lemon.lemonagent.dal.entity.AgentConfigEntity;
import com.lemon.lemonagent.dal.repository.AgentConfigRepository;
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
import java.util.List;

/** Agent 配置仓储的 JDBC 实现。 */
@Repository
public class AgentConfigJdbcRepository implements AgentConfigRepository {

    private static final String COLUMNS =
            "id, user_id, name, description, instruction, tools_json, model_connection_id, "
                    + "workflow_id, workflow_version, status, version, "
                    + "created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<AgentConfigEntity> mapper = new RowMapper<>() {
        @Override
        public AgentConfigEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            AgentConfigEntity e = new AgentConfigEntity();
            e.setId(rs.getLong("id"));
            long userId = rs.getLong("user_id");
            e.setUserId(rs.wasNull() ? null : userId);
            e.setName(rs.getString("name"));
            e.setDescription(rs.getString("description"));
            e.setInstruction(rs.getString("instruction"));
            e.setToolsJson(rs.getString("tools_json"));
            long connId = rs.getLong("model_connection_id");
            e.setModelConnectionId(rs.wasNull() ? null : connId);
            long wfId = rs.getLong("workflow_id");
            e.setWorkflowId(rs.wasNull() ? null : wfId);
            int wfVer = rs.getInt("workflow_version");
            e.setWorkflowVersion(rs.wasNull() ? null : wfVer);
            e.setStatus(rs.getInt("status"));
            e.setVersion(rs.getInt("version"));
            e.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
            e.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
            return e;
        }
    };

    public AgentConfigJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<AgentConfigEntity> findPlatform() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM agent_config WHERE user_id IS NULL ORDER BY id", mapper);
    }

    @Override
    public List<AgentConfigEntity> findByUser(Long userId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM agent_config WHERE user_id = ? ORDER BY id DESC",
                mapper, userId);
    }

    @Override
    public AgentConfigEntity findByIdVisible(Long id, Long userId) {
        List<AgentConfigEntity> list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM agent_config WHERE id = ? AND (user_id IS NULL OR user_id = ?)",
                mapper, id, userId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public AgentConfigEntity findById(Long id) {
        List<AgentConfigEntity> list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM agent_config WHERE id = ?", mapper, id);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public long countByWorkflowId(Long workflowId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM agent_config WHERE workflow_id = ?", Long.class, workflowId);
        return n == null ? 0 : n;
    }

    @Override
    public int insert(AgentConfigEntity e) {
        Instant now = Instant.now();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO agent_config (user_id, name, description, instruction, tools_json, "
                            + "model_connection_id, workflow_id, workflow_version, status, version, "
                            + "created_at, updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            if (e.getUserId() == null) {
                ps.setNull(1, Types.BIGINT);
            } else {
                ps.setLong(1, e.getUserId());
            }
            ps.setString(2, e.getName());
            ps.setString(3, e.getDescription());
            ps.setString(4, e.getInstruction());
            ps.setString(5, e.getToolsJson());
            if (e.getModelConnectionId() == null) {
                ps.setNull(6, Types.BIGINT);
            } else {
                ps.setLong(6, e.getModelConnectionId());
            }
            if (e.getWorkflowId() == null) {
                ps.setNull(7, Types.BIGINT);
            } else {
                ps.setLong(7, e.getWorkflowId());
            }
            if (e.getWorkflowVersion() == null) {
                ps.setNull(8, Types.INTEGER);
            } else {
                ps.setInt(8, e.getWorkflowVersion());
            }
            ps.setInt(9, e.getStatus());
            ps.setInt(10, e.getVersion());
            ps.setTimestamp(11, Timestamp.from(now));
            ps.setTimestamp(12, Timestamp.from(now));
            return ps;
        }, keys);
        e.setId(keys.getKey().longValue());
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return 1;
    }

    @Override
    public int update(AgentConfigEntity e) {
        return jdbcTemplate.update(
                "UPDATE agent_config SET name = ?, description = ?, instruction = ?, tools_json = ?, "
                        + "model_connection_id = ?, workflow_id = ?, workflow_version = ?, status = ?, "
                        + "version = version + 1 WHERE id = ? AND user_id = ?",
                e.getName(), e.getDescription(), e.getInstruction(), e.getToolsJson(),
                e.getModelConnectionId(), e.getWorkflowId(), e.getWorkflowVersion(), e.getStatus(),
                e.getId(), e.getUserId());
    }

    @Override
    public int delete(Long id, Long userId) {
        return jdbcTemplate.update(
                "DELETE FROM agent_config WHERE id = ? AND user_id = ?", id, userId);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

}
