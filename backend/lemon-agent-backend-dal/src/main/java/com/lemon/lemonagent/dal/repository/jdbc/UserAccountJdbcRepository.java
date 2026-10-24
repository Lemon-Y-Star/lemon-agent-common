package com.lemon.lemonagent.dal.repository.jdbc;

import com.lemon.lemonagent.dal.entity.UserAccountEntity;
import com.lemon.lemonagent.dal.repository.UserAccountRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

/**
 * 用户仓储的 JDBC 实现。
 */
@Repository
public class UserAccountJdbcRepository implements UserAccountRepository {

    private static final String COLUMNS =
            "id, username, password_hash, role, status, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<UserAccountEntity> mapper = new RowMapper<>() {
        @Override
        public UserAccountEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            UserAccountEntity e = new UserAccountEntity();
            e.setId(rs.getLong("id"));
            e.setUsername(rs.getString("username"));
            e.setPasswordHash(rs.getString("password_hash"));
            e.setRole(rs.getString("role"));
            e.setStatus(rs.getInt("status"));
            e.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
            e.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
            return e;
        }
    };

    public UserAccountJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserAccountEntity findByUsername(String username) {
        List<UserAccountEntity> list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM user_account WHERE username = ?",
                mapper, username);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public UserAccountEntity findById(Long id) {
        List<UserAccountEntity> list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM user_account WHERE id = ?",
                mapper, id);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public int updatePasswordHash(Long id, String passwordHash) {
        return jdbcTemplate.update(
                "UPDATE user_account SET password_hash = ?, updated_at = ? WHERE id = ?",
                passwordHash, Timestamp.from(Instant.now()), id);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

}
