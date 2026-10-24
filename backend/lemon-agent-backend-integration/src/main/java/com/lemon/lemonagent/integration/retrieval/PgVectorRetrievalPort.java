package com.lemon.lemonagent.integration.retrieval;

import com.lemon.lemonagent.model.runtime.RetrievalPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link RetrievalPort} 的实现：OpenAI 兼容 embeddings + PgVector 余弦召回
 * （BUILD-SPEC §7.6：一次 search = 1 次 embedding HTTP + 1 次向量查询）。
 *
 * <p>两个刻意的设计决策：
 * ① embedding 走<b>平台级配置</b>（lemon.retrieval.embedding-*）而不是用户 BYOK 连接——
 * 用户连的是对话模型代理，未必有 embeddings 渠道；检索是平台能力，凭据由平台统一持有。
 * ② JDBC 用<b>直连封装而不是 Spring JdbcTemplate bean</b>——Boot 4.1 会给每个
 * JdbcTemplate bean 注入 Flyway 初始化依赖，第二个数据源的 JdbcTemplate 会把 MySQL
 * 迁移误跑到 PostgreSQL 上（实测踩坑）；检索 SQL 只有一条，直连封装彻底脱离
 * Spring bean 生命周期。</p>
 *
 * <p>SQL 的 {@code <=>} 是余弦距离运算符，与索引 opclass {@code vector_cosine_ops} 一致
 * （不一致索引不生效——§3.3 的常见坑）；相似度 = 1 - 距离。</p>
 */
@Component
public class PgVectorRetrievalPort implements RetrievalPort {

    private static final Logger log = LoggerFactory.getLogger(PgVectorRetrievalPort.class);

    private static final String SEARCH_SQL =
            "SELECT title, substring(content, 1, 200) AS snippet, "
                    + "1 - (embedding <=> ?::vector) AS score "
                    + "FROM doc_chunk ORDER BY embedding <=> ?::vector LIMIT ?";

    private final PgVectorProperties properties;

    public PgVectorRetrievalPort(PgVectorProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean available() {
        var p = properties;
        return p.getEmbeddingBaseUrl() != null && !p.getEmbeddingBaseUrl().isBlank()
                && p.getEmbeddingApiKey() != null && !p.getEmbeddingApiKey().isBlank()
                && p.getEmbeddingModel() != null && !p.getEmbeddingModel().isBlank()
                && p.getPgvectorUrl() != null && !p.getPgvectorUrl().isBlank();
    }

    @Override
    public List<Hit> search(String query, int topK) {
        if (!available()) {
            throw new IllegalStateException("检索能力未配置（lemon.retrieval.embedding-*）");
        }
        float[] vector = embed(query);
        String vectorLiteral = toVectorLiteral(vector);

        // 空召回显式返回空列表（AC-25 第一道闸）：不抛异常、不兜底
        List<Hit> hits = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(
                properties.getPgvectorUrl(),
                properties.getPgvectorUsername(),
                properties.getPgvectorPassword());
             PreparedStatement ps = conn.prepareStatement(SEARCH_SQL)) {
            ps.setString(1, vectorLiteral);
            ps.setString(2, vectorLiteral);
            ps.setInt(3, Math.max(1, Math.min(topK, 10)));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    hits.add(new Hit(
                            rs.getString("title"),
                            rs.getString("snippet"),
                            rs.getDouble("score")));
                }
            }
            return hits;
        } catch (Exception e) {
            log.error("向量检索失败: {}", e.getMessage());
            throw new IllegalStateException("向量检索失败：" + e.getMessage(), e);
        }
    }

    /** 1 次 embedding HTTP：OpenAI 兼容 /embeddings，按请求构造（平台级凭据）。 */
    private float[] embed(String query) {
        OpenAiEmbeddingModel embeddingModel = OpenAiEmbeddingModel.builder()
                .options(OpenAiEmbeddingOptions.builder()
                        .baseUrl(properties.getEmbeddingBaseUrl())
                        .apiKey(properties.getEmbeddingApiKey())
                        .model(properties.getEmbeddingModel())
                        .build())
                .build();
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(query));
        if (response.getResults().isEmpty() || response.getResults().get(0).getOutput() == null) {
            throw new IllegalStateException("embedding 服务返回空结果");
        }
        return response.getResults().get(0).getOutput();
    }

    /** float[] → "[0.1,0.2,...]" 字面量（pgvector 的 text→vector 输入格式）。 */
    private static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

}
