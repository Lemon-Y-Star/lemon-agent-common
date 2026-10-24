package com.lemon.lemonagent.integration.retrieval;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 检索切片配置（application.yml 的 lemon.retrieval.*）。
 *
 * <p>embedding 凭据是平台级的（不同于用户 BYOK 连接）：对话模型代理未必有
 * embeddings 渠道，检索是平台能力，由平台统一持有凭据。未配置时
 * search_docs 工具不注册（对话不受影响，只是没有检索能力）。</p>
 */
@ConfigurationProperties(prefix = "lemon.retrieval")
public class PgVectorProperties {

    /** OpenAI 兼容 embeddings 端点（如 https://api.siliconflow.cn/v1） */
    private String embeddingBaseUrl;

    /** embeddings 凭据 */
    private String embeddingApiKey;

    /** embedding 模型名（维度须为 1024，与 doc_chunk.embedding vector(1024) 锁定） */
    private String embeddingModel;

    /** PgVector JDBC 地址（NAS middleware-pgvector 容器） */
    private String pgvectorUrl;

    /** PgVector 用户名 */
    private String pgvectorUsername;

    /** PgVector 密码 */
    private String pgvectorPassword;

    public String getEmbeddingBaseUrl() {
        return embeddingBaseUrl;
    }

    public void setEmbeddingBaseUrl(String embeddingBaseUrl) {
        this.embeddingBaseUrl = embeddingBaseUrl;
    }

    public String getEmbeddingApiKey() {
        return embeddingApiKey;
    }

    public void setEmbeddingApiKey(String embeddingApiKey) {
        this.embeddingApiKey = embeddingApiKey;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getPgvectorUrl() {
        return pgvectorUrl;
    }

    public void setPgvectorUrl(String pgvectorUrl) {
        this.pgvectorUrl = pgvectorUrl;
    }

    public String getPgvectorUsername() {
        return pgvectorUsername;
    }

    public void setPgvectorUsername(String pgvectorUsername) {
        this.pgvectorUsername = pgvectorUsername;
    }

    public String getPgvectorPassword() {
        return pgvectorPassword;
    }

    public void setPgvectorPassword(String pgvectorPassword) {
        this.pgvectorPassword = pgvectorPassword;
    }

}
