package com.lemon.lemonagent.model.runtime;

import java.util.List;

/**
 * 知识库检索详细结果（检索测试页用）：hits + 重排前候选（v4.1 重排可诊断）。
 *
 * @param hits         最终命中（重排可用时为重排后结果，source=rerank）
 * @param beforeRerank 重排前的合并候选（按原始排序；未启用重排为 null）
 * @param reranked     本次检索是否经过重排
 */
public record KbSearchData(List<KbSearchHit> hits, List<KbSearchHit> beforeRerank, boolean reranked) {
}
