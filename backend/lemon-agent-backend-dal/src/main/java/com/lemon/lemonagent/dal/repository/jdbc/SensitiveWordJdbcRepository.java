package com.lemon.lemonagent.dal.repository.jdbc;

import com.lemon.lemonagent.dal.entity.SensitiveWordEntity;
import com.lemon.lemonagent.dal.repository.SensitiveWordRepository;
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

/** 敏感词仓储的 JDBC 实现。 */
@Repository
public class SensitiveWordJdbcRepository implements SensitiveWordRepository {

    private static final String COLUMNS = "id, word, created_at";

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<SensitiveWordEntity> mapper = new RowMapper<>() {
        @Override
        public SensitiveWordEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
            SensitiveWordEntity e = new SensitiveWordEntity();
            e.setId(rs.getLong("id"));
            e.setWord(rs.getString("word"));
            Timestamp ts = rs.getTimestamp("created_at");
            e.setCreatedAt(ts == null ? null : ts.toInstant());
            return e;
        }
    };

    public SensitiveWordJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SensitiveWordEntity> findAll() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM sensitive_word ORDER BY word", mapper);
    }

    @Override
    public SensitiveWordEntity findByWord(String word) {
        var list = jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM sensitive_word WHERE word = ?", mapper, word);
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    public int insert(SensitiveWordEntity e) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO sensitive_word (word) VALUES (?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, e.getWord());
            return ps;
        }, keys);
        e.setId(keys.getKey().longValue());
        return 1;
    }

    @Override
    public int delete(Long id) {
        return jdbcTemplate.update("DELETE FROM sensitive_word WHERE id = ?", id);
    }

}
