package com.lemon.lemonagent.dal.repository.jdbc;

import com.lemon.lemonagent.dal.entity.ModelConnectionEntity;
import com.lemon.lemonagent.dal.repository.ModelConnectionRepository;
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

/**
 * 模型连接仓储的 JDBC 实现。
 */
@Repository
public class ModelConnectionJdbcRepository implements ModelConnectionRepository {

    private static final String COLUMNS =
            "id, user_id, name, provider, base_url, api_key_cipher, models_json, "
                    + "allow_auxiliary, status, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<ModelConnectionEntity> mapper = new RowMapper<>() {
        @Override
        public ModelConnectionEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            ModelConnectionEntity e = new ModelConnectionEntity();
            e.setId(rs.getLong("id"));
            e.setUserId(rs.getLong("user_id"));
            e.setName(rs.getString("name"));
            e.setProvider(rs.getString("provider"));
            e.setBaseUrl(rs.getString("base_url"));
            e.setApiKeyCipher(rs.getString("api_key_cipher"));
            e.setModelsJson(rs.getString("models_json"));
            e.setAllowAuxiliary(rs.getInt("allow_auxiliary"));
            e.setStatus(rs.getInt("status"));
            e.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
            e.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
            return e;
        }
    };

    public ModelConnectionJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ModelConnectionEntity> findByUser(Long userId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM model_connection WHERE user_id = ? ORDER BY id DESC",
                mapper, userId);
    }

    @Override
    public ModelConnectionEntity findByIdAndUser(Long id, Long userId) {
        List<ModelConnectionEntity> list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM model_connection WHERE id = ? AND user_id = ?",
                mapper, id, userId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public int insert(ModelConnectionEntity e) {
        Instant now = Instant.now();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO model_connection (user_id, name, provider, base_url, api_key_cipher, "
                            + "models_json, allow_auxiliary, status, created_at, updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, e.getUserId());
            ps.setString(2, e.getName());
            ps.setString(3, e.getProvider());
            ps.setString(4, e.getBaseUrl());
            ps.setString(5, e.getApiKeyCipher());
            ps.setString(6, e.getModelsJson());
            ps.setInt(7, e.getAllowAuxiliary());
            ps.setInt(8, e.getStatus());
            ps.setTimestamp(9, Timestamp.from(now));
            ps.setTimestamp(10, Timestamp.from(now));
            return ps;
        }, keys);
        e.setId(keys.getKey().longValue());
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return 1;
    }

    @Override
    public int update(ModelConnectionEntity e) {
        return jdbcTemplate.update(
                "UPDATE model_connection SET name=?, provider=?, base_url=?, api_key_cipher=?, "
                        + "models_json=?, allow_auxiliary=?, status=?, updated_at=? WHERE id=? AND user_id=?",
                e.getName(), e.getProvider(), e.getBaseUrl(), e.getApiKeyCipher(),
                e.getModelsJson(), e.getAllowAuxiliary(), e.getStatus(),
                Timestamp.from(Instant.now()), e.getId(), e.getUserId());
    }

    @Override
    public int delete(Long id, Long userId) {
        return jdbcTemplate.update(
                "DELETE FROM model_connection WHERE id=? AND user_id=?", id, userId);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

}
