package com.lemon.lemonagent.model.runtime;

/**
 * 知识库检索命中。
 *
 * @param title   文档标题
 * @param snippet 片段
 * @param source  命中来源：keyword（关键词降级）/ vector（向量召回）
 */
public record KbSearchHit(String title, String snippet, String source) {
}
