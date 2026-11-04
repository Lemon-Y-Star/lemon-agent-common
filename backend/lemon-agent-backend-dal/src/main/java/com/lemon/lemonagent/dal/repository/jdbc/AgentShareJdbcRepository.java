package com.lemon.lemonagent.dal.repository.jdbc;

import com.lemon.lemonagent.dal.entity.AgentShareEntity;
import com.lemon.lemonagent.dal.repository.AgentShareRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Agent 分享配置仓储（v4.6.0）：每 Agent 一条，upsert 按 agent_id。
 */
@Component
public class AgentShareJdbcRepository implements AgentShareRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<AgentShareEntity> mapper = new RowMapper<>() {
        @Override
        public AgentShareEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            AgentShareEntity e = new AgentShareEntity();
            e.setId(rs.getLong("id"));
            e.setUserId(rs.getLong("user_id"));
            e.setAgentId(rs.getLong("agent_id"));
            e.setShareCode(rs.getString("share_code"));
            e.setAccessCode(rs.getString("access_code"));
            e.setEnabled(rs.getInt("enabled"));
            Timestamp created = rs.getTimestamp("created_at");
            e.setCreatedAt(created == null ? null : created.toInstant());
            return e;
        }
    };

    public AgentShareJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AgentShareEntity findByCode(String shareCode) {
        List<AgentShareEntity> list = jdbcTemplate.query(
                "SELECT id, user_id, agent_id, share_code, access_code, enabled, created_at "
                        + "FROM agent_share WHERE share_code = ?",
                mapper, shareCode);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public AgentShareEntity findByAgentId(Long agentId) {
        List<AgentShareEntity> list = jdbcTemplate.query(
                "SELECT id, user_id, agent_id, share_code, access_code, enabled, created_at "
                        + "FROM agent_share WHERE agent_id = ?",
                mapper, agentId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public int upsert(AgentShareEntity e) {
        AgentShareEntity existing = findByAgentId(e.getAgentId());
        if (existing == null) {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbcTemplate.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO agent_share (user_id, agent_id, share_code, access_code, enabled) VALUES (?,?,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, e.getUserId());
                ps.setLong(2, e.getAgentId());
                ps.setString(3, e.getShareCode());
                ps.setString(4, e.getAccessCode());
                ps.setInt(5, e.getEnabled());
                return ps;
            }, keys);
            e.setId(keys.getKey().longValue());
            return 1;
        }
        int rows = jdbcTemplate.update(
                "UPDATE agent_share SET access_code = ?, enabled = ? WHERE id = ?",
                e.getAccessCode(), e.getEnabled(), existing.getId());
        e.setId(existing.getId());
        e.setShareCode(existing.getShareCode());
        return rows;
    }
}
