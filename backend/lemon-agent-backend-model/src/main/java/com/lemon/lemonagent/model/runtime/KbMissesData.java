package com.lemon.lemonagent.model.runtime;

import java.util.List;

/**
 * 知识库检索未命中队列（v4.1 运营闭环：0 命中 query 沉淀，指导补文档）。
 */
public record KbMissesData(List<MissItem> misses, int total) {

    /**
     * 单条未命中。
     *
     * @param query      检索语句
     * @param hits       累计未命中次数
     * @param lastSeenAt 最近一次未命中时间（ISO）
     */
    public record MissItem(String query, int hits, String lastSeenAt) {
    }
}
