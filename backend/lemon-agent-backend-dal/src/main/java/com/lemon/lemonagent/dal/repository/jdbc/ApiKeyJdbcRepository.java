package com.lemon.lemonagent.dal.repository.jdbc;

import com.lemon.lemonagent.dal.entity.ApiKeyEntity;
import com.lemon.lemonagent.dal.repository.ApiKeyRepository;
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

/** API 分发密钥仓储的 JDBC 实现。 */
@Repository
public class ApiKeyJdbcRepository implements ApiKeyRepository {

    private static final String COLUMNS =
            "id, user_id, name, key_prefix, key_hash, status, last_used_at, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ApiKeyEntity> mapper = new RowMapper<>() {
        @Override
        public ApiKeyEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            ApiKeyEntity e = new ApiKeyEntity();
            e.setId(rs.getLong("id"));
            e.setUserId(rs.getLong("user_id"));
            e.setName(rs.getString("name"));
            e.setKeyPrefix(rs.getString("key_prefix"));
            e.setKeyHash(rs.getString("key_hash"));
            e.setStatus(rs.getInt("status"));
            e.setLastUsedAt(toInstant(rs.getTimestamp("last_used_at")));
            e.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
            e.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
            return e;
        }
    };

    public ApiKeyJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ApiKeyEntity> findByUser(Long userId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM api_key WHERE user_id = ? ORDER BY id DESC", mapper, userId);
    }

    @Override
    public ApiKeyEntity findByHash(String keyHash) {
        var list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM api_key WHERE key_hash = ?", mapper, keyHash);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public int insert(ApiKeyEntity e) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO api_key (user_id, name, key_prefix, key_hash, status) VALUES (?,?,?,?,1)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, e.getUserId());
            ps.setString(2, e.getName());
            ps.setString(3, e.getKeyPrefix());
            ps.setString(4, e.getKeyHash());
            return ps;
        }, keys);
        e.setId(keys.getKey().longValue());
        return 1;
    }

    @Override
    public int revoke(Long id, Long userId) {
        return jdbcTemplate.update(
                "UPDATE api_key SET status = 0 WHERE id = ? AND user_id = ?", id, userId);
    }

    @Override
    public int touch(Long id) {
        return jdbcTemplate.update("UPDATE api_key SET last_used_at = CURRENT_TIMESTAMP(6) WHERE id = ?", id);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

}
