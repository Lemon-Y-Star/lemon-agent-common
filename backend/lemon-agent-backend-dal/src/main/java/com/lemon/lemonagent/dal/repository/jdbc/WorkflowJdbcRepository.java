package com.lemon.lemonagent.dal.repository.jdbc;

import com.lemon.lemonagent.dal.entity.WorkflowEntity;
import com.lemon.lemonagent.dal.entity.WorkflowVersionEntity;
import com.lemon.lemonagent.dal.repository.WorkflowRepository;
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

/** 工作流仓储的 JDBC 实现。 */
@Repository
public class WorkflowJdbcRepository implements WorkflowRepository {

    private static final String WF_COLUMNS =
            "id, user_id, name, description, status, created_at, updated_at";

    private static final String VER_COLUMNS =
            "id, workflow_id, version, graph_json, note, status, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<WorkflowEntity> wfMapper = new RowMapper<>() {
        @Override
        public WorkflowEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            WorkflowEntity e = new WorkflowEntity();
            e.setId(rs.getLong("id"));
            e.setUserId(rs.getLong("user_id"));
            e.setName(rs.getString("name"));
            e.setDescription(rs.getString("description"));
            e.setStatus(rs.getInt("status"));
            e.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
            e.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
            return e;
        }
    };

    private final RowMapper<WorkflowVersionEntity> verMapper = new RowMapper<>() {
        @Override
        public WorkflowVersionEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            WorkflowVersionEntity e = new WorkflowVersionEntity();
            e.setId(rs.getLong("id"));
            e.setWorkflowId(rs.getLong("workflow_id"));
            e.setVersion(rs.getInt("version"));
            e.setGraphJson(rs.getString("graph_json"));
            e.setNote(rs.getString("note"));
            e.setStatus(rs.getInt("status"));
            e.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
            e.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
            return e;
        }
    };

    public WorkflowJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<WorkflowEntity> findByUser(Long userId) {
        return jdbcTemplate.query(
                "SELECT " + WF_COLUMNS + " FROM workflow WHERE user_id = ? ORDER BY id DESC",
                wfMapper, userId);
    }

    @Override
    public WorkflowEntity findById(Long id) {
        var list = jdbcTemplate.query(
                "SELECT " + WF_COLUMNS + " FROM workflow WHERE id = ?", wfMapper, id);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public int insert(WorkflowEntity e) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO workflow (user_id, name, description, status) VALUES (?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, e.getUserId());
            ps.setString(2, e.getName());
            ps.setString(3, e.getDescription());
            ps.setInt(4, e.getStatus() == null ? 1 : e.getStatus());
            return ps;
        }, keys);
        e.setId(keys.getKey().longValue());
        return 1;
    }

    @Override
    public WorkflowVersionEntity findActive(Long workflowId) {
        var list = jdbcTemplate.query(
                "SELECT " + VER_COLUMNS + " FROM workflow_version WHERE workflow_id = ? AND status = 1 "
                        + "ORDER BY version DESC LIMIT 1", verMapper, workflowId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public WorkflowVersionEntity findVersion(Long workflowId, Integer version) {
        var list = jdbcTemplate.query(
                "SELECT " + VER_COLUMNS + " FROM workflow_version WHERE workflow_id = ? AND version = ?",
                verMapper, workflowId, version);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<WorkflowVersionEntity> findVersions(Long workflowId) {
        return jdbcTemplate.query(
                "SELECT " + VER_COLUMNS + " FROM workflow_version WHERE workflow_id = ? ORDER BY version DESC",
                verMapper, workflowId);
    }

    @Override
    public int insertVersion(WorkflowVersionEntity e) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO workflow_version (workflow_id, version, graph_json, note, status) "
                            + "VALUES (?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, e.getWorkflowId());
            ps.setInt(2, e.getVersion());
            ps.setString(3, e.getGraphJson());
            ps.setString(4, e.getNote());
            ps.setInt(5, e.getStatus() == null ? 1 : e.getStatus());
            return ps;
        }, keys);
        e.setId(keys.getKey().longValue());
        return 1;
    }

    @Override
    public int deactivateAll(Long workflowId) {
        return jdbcTemplate.update("UPDATE workflow_version SET status = 0 WHERE workflow_id = ?", workflowId);
    }

    @Override
    public int delete(Long id, Long userId) {
        return jdbcTemplate.update("DELETE FROM workflow WHERE id = ? AND user_id = ?", id, userId);
    }

    @Override
    public int deleteVersions(Long workflowId) {
        return jdbcTemplate.update("DELETE FROM workflow_version WHERE workflow_id = ?", workflowId);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

}
