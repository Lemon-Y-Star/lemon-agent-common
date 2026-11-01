package com.lemon.lemonagent.model.runtime;

import java.util.List;

/**
 * 知识检索防腐端口（BUILD-SPEC §7.6）：embed(query) → 向量库余弦召回 → 组装 hits。
 *
 * <p>实现在 integration（OpenAI 兼容 embeddings + PgVector）。
 * <b>空召回必须显式返回空列表</b>，不抛异常、不兜底（AC-25 幻觉防护第一道闸）。</p>
 */
public interface RetrievalPort {

    /**
     * 向量检索。
     *
     * @param query 入参：查询语句（调用方负责 1..1000 字符约束）
     * @param topK  入参：召回条数（1..10）
     * @return 出参：命中列表（按相似度降序）；无命中返回空列表
     * @throws IllegalStateException embedding 服务不可用或向量库连接失败时抛出（工具执行层转为失败结果）
     */
    List<Hit> search(String query, int topK);

    /**
     * 检索命中（溯源展示的最小充分集）。
     *
     * @param title   源文档标题（引用溯源要显示给用户）
     * @param snippet 片段正文（截断）
     * @param score   相似度（1 - 余弦距离，越大越相似）
     * @param images  文档关联图片（多模态随文带出，可空）
     */
    record Hit(String title, String snippet, double score, List<String> images) {

        /** 兼容旧三参构造（无图片）。 */
        public Hit(String title, String snippet, double score) {
            this(title, snippet, score, null);
        }
    }

    /**
     * 检索能力是否已配置（embedding 端点与向量库就绪）；未配置时 search_docs 不注册。
     *
     * @return 出参：true 表示可用
     */
    default boolean available() {
        return true;
    }

    /**
     * 重排能力是否已配置（lemon.retrieval.rerank-*）；未配置时检索不做精排（降级不报错）。
     *
     * @return 出参：true 表示可用
     */
    default boolean rerankAvailable() {
        return false;
    }

    /**
     * 重排序（v4.1）：对召回候选按与 query 的相关度精排，取前 topK。
     * 未配置重排时默认原样截断（保持降级语义）。
     *
     * @param query      入参：原始查询
     * @param candidates 入参：召回候选（关键词+向量合并去重后）
     * @param topK       入参：精排后保留条数
     * @return 出参：精排后的命中
     */
    default List<Hit> rerank(String query, List<Hit> candidates, int topK) {
        return candidates.stream().limit(Math.max(1, topK)).toList();
    }

}
