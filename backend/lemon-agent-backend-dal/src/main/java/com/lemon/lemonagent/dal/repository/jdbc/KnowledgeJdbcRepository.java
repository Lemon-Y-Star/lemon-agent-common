package com.lemon.lemonagent.dal.repository.jdbc;

import com.lemon.lemonagent.dal.entity.AgentMemoryEntity;
import com.lemon.lemonagent.dal.entity.KbImageEntity;
import com.lemon.lemonagent.dal.entity.KbMissEntity;
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

    private final RowMapper<KbMissEntity> missMapper = new RowMapper<>() {
        @Override
        public KbMissEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            KbMissEntity e = new KbMissEntity();
            e.setId(rs.getLong("id"));
            e.setUserId(rs.getLong("user_id"));
            e.setQuery(rs.getString("query"));
            e.setHits(rs.getInt("hits"));
            Timestamp seen = rs.getTimestamp("last_seen_at");
            e.setLastSeenAt(seen == null ? null : seen.toInstant());
            Timestamp created = rs.getTimestamp("created_at");
            e.setCreatedAt(created == null ? null : created.toInstant());
            return e;
        }
    };

    private final RowMapper<KbImageEntity> imageMapper = new RowMapper<>() {
        @Override
        public KbImageEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            KbImageEntity e = new KbImageEntity();
            e.setId(rs.getLong("id"));
            e.setUserId(rs.getLong("user_id"));
            e.setDocId(rs.getLong("doc_id"));
            e.setUrl(rs.getString("url"));
            e.setAlt(rs.getString("alt"));
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
    public List<KbDocumentEntity> listDocsPage(Long userId, int offset, int limit) {
        return jdbcTemplate.query(
                "SELECT id, user_id, title, content, created_at, updated_at FROM kb_document "
                        + "WHERE user_id = ? ORDER BY id DESC LIMIT ? OFFSET ?", docMapper, userId, limit, offset);
    }

    @Override
    public int countDocs(Long userId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kb_document WHERE user_id = ?", Integer.class, userId);
        return n == null ? 0 : n;
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

    /**
     * 关键词检索（v4.1）：ngram 全文索引 BOOLEAN MODE 短语匹配优先（索引加速），
     * LIKE 兜底（单字 query 全文分词器命不中）；两者取并集，ORDER BY id DESC 语义不变。
     */
    @Override
    public List<KbDocumentEntity> searchDocs(Long userId, String keyword, int limit) {
        String like = "%" + keyword + "%";
        String phrase = "\"" + keyword + "\"";
        return jdbcTemplate.query(
                "SELECT id, user_id, title, content, created_at, updated_at FROM kb_document "
                        + "WHERE user_id = ? AND (MATCH(title, content) AGAINST(? IN BOOLEAN MODE) "
                        + "OR title LIKE ? OR content LIKE ?) ORDER BY id DESC LIMIT ?",
                docMapper, userId, phrase, like, like, limit);
    }

    @Override
    public void recordMiss(Long userId, String query) {
        jdbcTemplate.update(
                "INSERT INTO kb_miss (user_id, query, hits, last_seen_at, created_at) VALUES (?,?,1,NOW(6),NOW(6)) "
                        + "ON DUPLICATE KEY UPDATE hits = hits + 1, last_seen_at = NOW(6)",
                userId, query);
    }

    @Override
    public List<KbMissEntity> listMisses(Long userId, int limit) {
        return jdbcTemplate.query(
                "SELECT id, user_id, query, hits, last_seen_at, created_at FROM kb_miss "
                        + "WHERE user_id = ? ORDER BY hits DESC, last_seen_at DESC LIMIT ?",
                missMapper, userId, limit);
    }

    @Override
    public List<KbMissEntity> listMissesPage(Long userId, int offset, int limit) {
        return jdbcTemplate.query(
                "SELECT id, user_id, query, hits, last_seen_at, created_at FROM kb_miss "
                        + "WHERE user_id = ? ORDER BY hits DESC, last_seen_at DESC LIMIT ? OFFSET ?",
                missMapper, userId, limit, offset);
    }

    @Override
    public int countMisses(Long userId) {
        Long n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kb_miss WHERE user_id = ?", Long.class, userId);
        return n == null ? 0 : n.intValue();
    }

    @Override
    public void replaceImages(Long userId, Long docId, List<KbImageEntity> images) {
        jdbcTemplate.update("DELETE FROM kb_image WHERE doc_id = ?", docId);
        for (KbImageEntity img : images) {
            jdbcTemplate.update(
                    "INSERT INTO kb_image (user_id, doc_id, url, alt, created_at) VALUES (?,?,?,?,NOW(6))",
                    userId, docId, img.getUrl(), img.getAlt());
        }
    }

    @Override
    public List<KbImageEntity> findImagesByDocs(List<Long> docIds) {
        if (docIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(docIds.size(), "?"));
        Object[] args = docIds.toArray();
        return jdbcTemplate.query(
                "SELECT id, user_id, doc_id, url, alt, created_at FROM kb_image WHERE doc_id IN (" + placeholders + ")",
                imageMapper, args);
    }

    @Override
    public List<KbDocumentEntity> findByTitles(Long userId, List<String> titles) {
        if (titles.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(titles.size(), "?"));
        Object[] args = new Object[titles.size() + 1];
        args[0] = userId;
        for (int i = 0; i < titles.size(); i++) {
            args[i + 1] = titles.get(i);
        }
        return jdbcTemplate.query(
                "SELECT id, user_id, title, content, created_at, updated_at FROM kb_document "
                        + "WHERE user_id = ? AND title IN (" + placeholders + ")",
                docMapper, args);
    }

    @Override
    public KbDocumentEntity findById(Long docId) {
        List<KbDocumentEntity> list = jdbcTemplate.query(
                "SELECT id, user_id, title, content, created_at, updated_at FROM kb_document WHERE id = ?",
                docMapper, docId);
        return list.isEmpty() ? null : list.get(0);
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
