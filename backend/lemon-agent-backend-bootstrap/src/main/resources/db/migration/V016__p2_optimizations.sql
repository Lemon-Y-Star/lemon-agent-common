-- v4.1 市面对比优化包（P0+P1+P2）：未命中队列 / 多模态图片 / 健康探测列 / 全文索引

-- ① 知识库检索未命中队列（运营闭环：0 命中的 query 沉淀，指导补文档）
CREATE TABLE kb_miss (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    query VARCHAR(200) NOT NULL,
    hits INT NOT NULL DEFAULT 1 COMMENT '累计未命中次数',
    last_seen_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_user_query (user_id, query),
    KEY idx_user_hits (user_id, hits DESC)
) COMMENT '知识库检索未命中队列';

-- ② 多模态知识库：文档内 markdown 图片随文档入库，检索命中随文带出
CREATE TABLE kb_image (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    doc_id BIGINT NOT NULL,
    url VARCHAR(1000) NOT NULL,
    alt VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    KEY idx_doc (doc_id)
) COMMENT '知识库文档图片（markdown 图链提取）';

-- ③ 模型连接健康探测（定时探活：GET /models + 时延）
ALTER TABLE model_connection
    ADD COLUMN health_status VARCHAR(16) NULL COMMENT '健康状态：ok / fail / unknown',
    ADD COLUMN health_latency_ms INT NULL COMMENT '最近一次探测时延 ms',
    ADD COLUMN health_checked_at DATETIME(6) NULL COMMENT '最近探测时间';

-- ④ 关键词路升级：ngram 全文索引（中文分词，BOOLEAN MODE 短语匹配替代 LIKE 全表扫）
ALTER TABLE kb_document ADD FULLTEXT INDEX ft_kb_title_content (title, content) WITH PARSER ngram;
