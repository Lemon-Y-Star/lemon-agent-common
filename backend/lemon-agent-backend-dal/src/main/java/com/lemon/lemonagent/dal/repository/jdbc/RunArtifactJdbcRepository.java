package com.lemon.lemonagent.dal.repository.jdbc;

import com.lemon.lemonagent.dal.entity.RunArtifactEntity;
import com.lemon.lemonagent.dal.repository.RunArtifactRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

/**
 * 运行文件产物 JDBC 实现（v4.4.4）。
 */
@Repository
public class RunArtifactJdbcRepository implements RunArtifactRepository {

    private static final String META_COLUMNS =
            "id, run_id, user_id, name, content_type, size_bytes, created_at";

    private static final RowMapper<RunArtifactEntity> META_MAPPER = (rs, i) -> {
        RunArtifactEntity e = new RunArtifactEntity();
        e.setId(rs.getLong("id"));
        e.setRunId(rs.getLong("run_id"));
        e.setUserId(rs.getLong("user_id"));
        e.setName(rs.getString("name"));
        e.setContentType(rs.getString("content_type"));
        e.setSizeBytes(rs.getInt("size_bytes"));
        e.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return e;
    };

    private final JdbcTemplate jdbcTemplate;

    public RunArtifactJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public long insert(RunArtifactEntity entity) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO run_artifact (run_id, user_id, name, content_type, size_bytes, data) VALUES (?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, entity.getRunId());
            ps.setLong(2, entity.getUserId());
            ps.setString(3, entity.getName());
            ps.setString(4, entity.getContentType());
            ps.setInt(5, entity.getSizeBytes());
            ps.setBytes(6, entity.getData());
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    @Override
    public List<RunArtifactEntity> findByRun(Long runId, Long userId) {
        return jdbcTemplate.query(
                "SELECT " + META_COLUMNS + " FROM run_artifact WHERE run_id=? AND user_id=? ORDER BY id",
                META_MAPPER, runId, userId);
    }

    @Override
    public RunArtifactEntity findByIdAndUser(Long id, Long userId) {
        List<RunArtifactEntity> rows = jdbcTemplate.query(
                "SELECT id, run_id, user_id, name, content_type, size_bytes, created_at, data"
                        + " FROM run_artifact WHERE id=? AND user_id=?",
                (rs, i) -> {
                    RunArtifactEntity e = META_MAPPER.mapRow(rs, i);
                    e.setData(rs.getBytes("data"));
                    return e;
                }, id, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

}
