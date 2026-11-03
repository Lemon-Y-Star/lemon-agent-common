package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.List;

/** 知识库文档列表 + 检索能力说明。 */
@Data
public class KbDocsData {

    /** 文档 */
    private List<KbDocumentData> docs;

    /** 是否已配置向量检索（false = 关键词降级） */
    private boolean vectorEnabled;

    /** 文档总数（v4.5.6 分页配套） */
    private int total;
}
