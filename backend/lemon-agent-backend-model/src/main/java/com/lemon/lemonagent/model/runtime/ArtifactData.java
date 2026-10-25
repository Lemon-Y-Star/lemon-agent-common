package com.lemon.lemonagent.model.runtime;

import lombok.Data;

/** 产物（一条有最终产出的运行）+ 我对它的反馈。 */
@Data
public class ArtifactData {

    /** 运行 ID */
    private Long runId;

    /** 会话 ID */
    private Long sessionId;

    /** Agent ID */
    private Long agentId;

    /** 用户输入 */
    private String input;

    /** 最终产出（run_completed 权威正文） */
    private String output;

    /** 状态 */
    private String status;

    /** 输入 tokens */
    private Integer inputTokens;

    /** 输出 tokens */
    private Integer outputTokens;

    /** 结束时间 */
    private String endedAt;

    /** 我的反馈：UP / DOWN / null（未反馈） */
    private String myRating;

    /** 我的评论 */
    private String myComment;

}
