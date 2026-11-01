package com.lemon.lemonagent.model.runtime;

import java.util.List;

/**
 * 知识库检索命中。
 *
 * @param title   文档标题
 * @param snippet 片段
 * @param source  命中来源：keyword（关键词降级）/ vector（向量召回）/ rerank（重排精排）
 * @param images  文档关联图片 URL（多模态知识库：markdown 图链随文带出；可空）
 */
public record KbSearchHit(String title, String snippet, String source, List<String> images) {

    /** 兼容旧三参构造（无图片）。 */
    public KbSearchHit(String title, String snippet, String source) {
        this(title, snippet, source, null);
    }
}
