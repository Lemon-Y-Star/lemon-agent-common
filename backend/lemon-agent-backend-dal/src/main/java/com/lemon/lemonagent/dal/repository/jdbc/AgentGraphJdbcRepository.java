package com.lemon.lemonagent.dal.repository.jdbc;

import com.lemon.lemonagent.dal.entity.AgentGraphEntity;
import com.lemon.lemonagent.dal.repository.AgentGraphRepository;
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

/** 执行图仓储的 JDBC 实现。 */
@Repository
public class AgentGraphJdbcRepository implements AgentGraphRepository {

    private static final String COLUMNS =
            "id, agent_id, version, graph_json, status, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<AgentGraphEntity> mapper = new RowMapper<>() {
        @Override
        public AgentGraphEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            AgentGraphEntity e = new AgentGraphEntity();
            e.setId(rs.getLong("id"));
            e.setAgentId(rs.getLong("agent_id"));
            e.setVersion(rs.getInt("version"));
            e.setGraphJson(rs.getString("graph_json"));
            e.setStatus(rs.getInt("status"));
            Timestamp created = rs.getTimestamp("created_at");
            e.setCreatedAt(created == null ? null : created.toInstant());
            Timestamp updated = rs.getTimestamp("updated_at");
            e.setUpdatedAt(updated == null ? null : updated.toInstant());
            return e;
        }
    };

    public AgentGraphJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AgentGraphEntity findActive(Long agentId) {
        var list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM agent_graph WHERE agent_id = ? AND status = 1 "
                        + "ORDER BY version DESC LIMIT 1", mapper, agentId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public int insert(AgentGraphEntity e) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO agent_graph (agent_id, version, graph_json, status) VALUES (?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, e.getAgentId());
            ps.setInt(2, e.getVersion());
            ps.setString(3, e.getGraphJson());
            ps.setInt(4, e.getStatus());
            return ps;
        }, keys);
        e.setId(keys.getKey().longValue());
        return 1;
    }

    @Override
    public int deactivateAll(Long agentId) {
        return jdbcTemplate.update("UPDATE agent_graph SET status = 0 WHERE agent_id = ?", agentId);
    }

    @Override
    public int deleteByAgent(Long agentId) {
        return jdbcTemplate.update("DELETE FROM agent_graph WHERE agent_id = ?", agentId);
    }

}
