package com.lemon.lemonagent.dal.repository.jdbc;

import com.lemon.lemonagent.dal.entity.McpConnectionEntity;
import com.lemon.lemonagent.dal.repository.McpConnectionRepository;
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

/**
 * MCP 连接仓储的 JDBC 实现（平台公共与自建共用一张表，userId NULL 语义用 Types.NULL 写入）。
 */
@Repository
public class McpConnectionJdbcRepository implements McpConnectionRepository {

    private static final String COLUMNS =
            "id, user_id, name, transport_type, url, command, secret_cipher, status, "
                    + "editable, platform_public, unavailable_reason, version, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<McpConnectionEntity> mapper = new RowMapper<>() {
        @Override
        public McpConnectionEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            McpConnectionEntity e = new McpConnectionEntity();
            e.setId(rs.getLong("id"));
            long userId = rs.getLong("user_id");
            e.setUserId(rs.wasNull() ? null : userId);
            e.setName(rs.getString("name"));
            e.setTransportType(rs.getString("transport_type"));
            e.setUrl(rs.getString("url"));
            e.setCommand(rs.getString("command"));
            e.setSecretCipher(rs.getString("secret_cipher"));
            e.setStatus(rs.getInt("status"));
            e.setEditable(rs.getInt("editable"));
            e.setPlatformPublic(rs.getInt("platform_public"));
            e.setUnavailableReason(rs.getString("unavailable_reason"));
            e.setVersion(rs.getInt("version"));
            e.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
            e.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
            return e;
        }
    };

    public McpConnectionJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<McpConnectionEntity> findPlatform() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM mcp_connection WHERE user_id IS NULL ORDER BY id",
                mapper);
    }

    @Override
    public List<McpConnectionEntity> findByUser(Long userId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM mcp_connection WHERE user_id = ? ORDER BY id DESC",
                mapper, userId);
    }

    @Override
    public McpConnectionEntity findByIdAndUser(Long id, Long userId) {
        List<McpConnectionEntity> list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM mcp_connection WHERE id=? AND user_id=?",
                mapper, id, userId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public int insert(McpConnectionEntity e) {
        Instant now = Instant.now();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO mcp_connection (user_id, name, transport_type, url, command, "
                            + "secret_cipher, status, editable, platform_public, unavailable_reason, "
                            + "version, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            if (e.getUserId() == null) {
                ps.setNull(1, Types.BIGINT);
            } else {
                ps.setLong(1, e.getUserId());
            }
            ps.setString(2, e.getName());
            ps.setString(3, e.getTransportType());
            ps.setString(4, e.getUrl());
            ps.setString(5, e.getCommand());
            ps.setString(6, e.getSecretCipher());
            ps.setInt(7, e.getStatus());
            ps.setInt(8, e.getEditable());
            ps.setInt(9, e.getPlatformPublic());
            ps.setString(10, e.getUnavailableReason());
            ps.setInt(11, e.getVersion());
            ps.setTimestamp(12, Timestamp.from(now));
            ps.setTimestamp(13, Timestamp.from(now));
            return ps;
        }, keys);
        e.setId(keys.getKey().longValue());
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return 1;
    }

    @Override
    public int update(McpConnectionEntity e) {
        return jdbcTemplate.update(
                "UPDATE mcp_connection SET name=?, transport_type=?, url=?, command=?, secret_cipher=?, "
                        + "status=?, unavailable_reason=?, version=version+1, updated_at=? "
                        + "WHERE id=? AND user_id=?",
                e.getName(), e.getTransportType(), e.getUrl(), e.getCommand(), e.getSecretCipher(),
                e.getStatus(), e.getUnavailableReason(), Timestamp.from(Instant.now()),
                e.getId(), e.getUserId());
    }

    @Override
    public int delete(Long id, Long userId) {
        return jdbcTemplate.update(
                "DELETE FROM mcp_connection WHERE id=? AND user_id=?", id, userId);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

}
