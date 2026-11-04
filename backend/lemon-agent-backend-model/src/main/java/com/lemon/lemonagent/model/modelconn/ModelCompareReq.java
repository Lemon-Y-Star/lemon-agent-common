package com.lemon.lemonagent.model.modelconn;

import lombok.Data;

import java.util.List;

/**
 * 多模型对比请求（v4.8）：同一问题并发发给多个已启用连接，并排比较回答质量。
 */
@Data
public class ModelCompareReq {

    /** 对比用的问题 */
    private String query;

    /** 参与对比的连接 ID（按此顺序返回结果，最多 4 个） */
    private List<Long> connectionIds;

    /** 可选系统提示词（留空用默认「你是 AI 助手」） */
    private String systemPrompt;

}
