package com.lemon.lemonagent.dal.repository.jdbc;

import com.lemon.lemonagent.dal.entity.AgentMemoryEntity;
import com.lemon.lemonagent.dal.entity.KbDocumentEntity;
import com.lemon.lemonagent.dal.repository.KnowledgeRepository;
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
import java.util.List;

/** 知识库 + 长期记忆的 JDBC 实现。 */
@Repository
public class KnowledgeJdbcRepository implements KnowledgeRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<KbDocumentEntity> docMapper = new RowMapper<>() {
        @Override
        public KbDocumentEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            KbDocumentEntity e = new KbDocumentEntity();
            e.setId(rs.getLong("id"));
            e.setUserId(rs.getLong("user_id"));
            e.setTitle(rs.getString("title"));
            e.setContent(rs.getString("content"));
            Timestamp created = rs.getTimestamp("created_at");
            e.setCreatedAt(created == null ? null : created.toInstant());
            Timestamp updated = rs.getTimestamp("updated_at");
            e.setUpdatedAt(updated == null ? null : updated.toInstant());
            return e;
        }
    };

    private final RowMapper<AgentMemoryEntity> memoryMapper = new RowMapper<>() {
        @Override
        public AgentMemoryEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            AgentMemoryEntity e = new AgentMemoryEntity();
            e.setId(rs.getLong("id"));
            e.setUserId(rs.getLong("user_id"));
            e.setContent(rs.getString("content"));
            e.setEnabled(rs.getInt("enabled"));
            Timestamp created = rs.getTimestamp("created_at");
            e.setCreatedAt(created == null ? null : created.toInstant());
            return e;
        }
    };

    public KnowledgeJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<KbDocumentEntity> listDocs(Long userId) {
        return jdbcTemplate.query(
                "SELECT id, user_id, title, content, created_at, updated_at FROM kb_document "
                        + "WHERE user_id = ? ORDER BY id DESC LIMIT 200", docMapper, userId);
    }

    @Override
    public int insertDoc(KbDocumentEntity e) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO kb_document (user_id, title, content) VALUES (?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, e.getUserId());
            ps.setString(2, e.getTitle());
            ps.setString(3, e.getContent());
            return ps;
        }, keys);
        e.setId(keys.getKey().longValue());
        return 1;
    }

    @Override
    public int deleteDoc(Long id, Long userId) {
        return jdbcTemplate.update("DELETE FROM kb_document WHERE id = ? AND user_id = ?", id, userId);
    }

    @Override
    public List<KbDocumentEntity> searchDocs(Long userId, String keyword, int limit) {
        String like = "%" + keyword + "%";
        return jdbcTemplate.query(
                "SELECT id, user_id, title, content, created_at, updated_at FROM kb_document "
                        + "WHERE user_id = ? AND (title LIKE ? OR content LIKE ?) ORDER BY id DESC LIMIT ?",
                docMapper, userId, like, like, limit);
    }

    @Override
    public List<AgentMemoryEntity> listMemories(Long userId) {
        return jdbcTemplate.query(
                "SELECT id, user_id, content, enabled, created_at FROM agent_memory "
                        + "WHERE user_id = ? ORDER BY id DESC LIMIT 200", memoryMapper, userId);
    }

    @Override
    public List<AgentMemoryEntity> listEnabledMemories(Long userId, int limit) {
        return jdbcTemplate.query(
                "SELECT id, user_id, content, enabled, created_at FROM agent_memory "
                        + "WHERE user_id = ? AND enabled = 1 ORDER BY id DESC LIMIT ?",
                memoryMapper, userId, limit);
    }

    @Override
    public int insertMemory(AgentMemoryEntity e) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO agent_memory (user_id, content, enabled) VALUES (?,?,1)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, e.getUserId());
            ps.setString(2, e.getContent());
            return ps;
        }, keys);
        e.setId(keys.getKey().longValue());
        return 1;
    }

    @Override
    public int toggleMemory(Long id, Long userId, boolean enabled) {
        return jdbcTemplate.update("UPDATE agent_memory SET enabled = ? WHERE id = ? AND user_id = ?",
                enabled ? 1 : 0, id, userId);
    }

    @Override
    public int deleteMemory(Long id, Long userId) {
        return jdbcTemplate.update("DELETE FROM agent_memory WHERE id = ? AND user_id = ?", id, userId);
    }

}
