package com.lemon.lemonagent.dal.repository.jdbc;

import com.lemon.lemonagent.dal.entity.RunFeedbackEntity;
import com.lemon.lemonagent.dal.repository.RunFeedbackRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/** 运行反馈仓储的 JDBC 实现。 */
@Repository
public class RunFeedbackJdbcRepository implements RunFeedbackRepository {

    private static final String COLUMNS = "id, run_id, user_id, rating, comment, created_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<RunFeedbackEntity> mapper = new RowMapper<>() {
        @Override
        public RunFeedbackEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            RunFeedbackEntity e = new RunFeedbackEntity();
            e.setId(rs.getLong("id"));
            e.setRunId(rs.getLong("run_id"));
            e.setUserId(rs.getLong("user_id"));
            e.setRating(rs.getString("rating"));
            e.setComment(rs.getString("comment"));
            e.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
            e.setUpdatedAt(toInstant(rs.getTimestamp("updated_at")));
            return e;
        }
    };

    public RunFeedbackJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public RunFeedbackEntity findByRunAndUser(Long runId, Long userId) {
        var list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM run_feedback WHERE run_id = ? AND user_id = ?",
                mapper, runId, userId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public int upsert(RunFeedbackEntity e) {
        return jdbcTemplate.update(
                "INSERT INTO run_feedback (run_id, user_id, rating, comment) VALUES (?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE rating = VALUES(rating), comment = VALUES(comment)",
                e.getRunId(), e.getUserId(), e.getRating(), e.getComment());
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

}
