package com.lemon.lemonagent.dal.repository.jdbc;

import com.lemon.lemonagent.dal.entity.EvalBatchEntity;
import com.lemon.lemonagent.dal.entity.EvalItemEntity;
import com.lemon.lemonagent.dal.repository.EvalBatchRepository;
import com.lemon.lemonagent.dal.repository.EvalItemRepository;
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

/** 评测批次 / 用例项的 JDBC 实现（两张表共用一个小仓储，避免为 4 个方法开两个类）。 */
@Repository
public class EvalJdbcRepository implements EvalBatchRepository, EvalItemRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<EvalBatchEntity> batchMapper = new RowMapper<>() {
        @Override
        public EvalBatchEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            EvalBatchEntity e = new EvalBatchEntity();
            e.setId(rs.getLong("id"));
            e.setUserId(rs.getLong("user_id"));
            e.setAgentId(rs.getLong("agent_id"));
            int version = rs.getInt("graph_version");
            e.setGraphVersion(rs.wasNull() ? null : version);
            e.setNote(rs.getString("note"));
            Timestamp created = rs.getTimestamp("created_at");
            e.setCreatedAt(created == null ? null : created.toInstant());
            return e;
        }
    };

    private final RowMapper<EvalItemEntity> itemMapper = new RowMapper<>() {
        @Override
        public EvalItemEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            EvalItemEntity e = new EvalItemEntity();
            e.setId(rs.getLong("id"));
            e.setBatchId(rs.getLong("batch_id"));
            e.setSeq(rs.getInt("seq"));
            e.setRunId(rs.getLong("run_id"));
            e.setInput(rs.getString("input"));
            return e;
        }
    };

    public EvalJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int insert(EvalBatchEntity e) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO eval_batch (user_id, agent_id, graph_version, note) VALUES (?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, e.getUserId());
            ps.setLong(2, e.getAgentId());
            if (e.getGraphVersion() == null) {
                ps.setNull(3, java.sql.Types.INTEGER);
            } else {
                ps.setInt(3, e.getGraphVersion());
            }
            ps.setString(4, e.getNote() == null ? "" : e.getNote());
            return ps;
        }, keys);
        e.setId(keys.getKey().longValue());
        return 1;
    }

    @Override
    public EvalBatchEntity findByIdAndUser(Long id, Long userId) {
        List<EvalBatchEntity> list = jdbcTemplate.query(
                "SELECT id, user_id, agent_id, graph_version, note, created_at FROM eval_batch "
                        + "WHERE id = ? AND user_id = ?", batchMapper, id, userId);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public List<EvalBatchEntity> listByAgent(Long agentId, Long userId, int limit) {
        return jdbcTemplate.query(
                "SELECT id, user_id, agent_id, graph_version, note, created_at FROM eval_batch "
                        + "WHERE agent_id = ? AND user_id = ? ORDER BY id DESC LIMIT ?",
                batchMapper, agentId, userId, limit);
    }

    @Override
    public List<EvalBatchEntity> listByUser(Long userId, int limit) {
        return jdbcTemplate.query(
                "SELECT id, user_id, agent_id, graph_version, note, created_at FROM eval_batch "
                        + "WHERE user_id = ? ORDER BY id DESC LIMIT ?",
                batchMapper, userId, limit);
    }

    @Override
    public int insert(EvalItemEntity e) {
        return jdbcTemplate.update(
                "INSERT INTO eval_item (batch_id, seq, run_id, input) VALUES (?,?,?,?)",
                e.getBatchId(), e.getSeq(), e.getRunId(), e.getInput());
    }

    @Override
    public List<EvalItemEntity> listByBatch(Long batchId) {
        return jdbcTemplate.query(
                "SELECT id, batch_id, seq, run_id, input FROM eval_item WHERE batch_id = ? ORDER BY seq",
                itemMapper, batchId);
    }

}
