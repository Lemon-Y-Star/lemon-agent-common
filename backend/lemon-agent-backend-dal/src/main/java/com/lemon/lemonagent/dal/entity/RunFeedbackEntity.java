package com.lemon.lemonagent.dal.entity;

import lombok.Data;

import java.time.Instant;

/** 运行反馈（表 run_feedback）：一人一运行一条（upsert）。 */
@Data
public class RunFeedbackEntity {

    /** 主键 */
    private Long id;

    /** 运行 ID */
    private Long runId;

    /** 反馈人 */
    private Long userId;

    /** UP / DOWN */
    private String rating;

    /** 文字反馈 */
    private String comment;

    /** 创建时间 */
    private Instant createdAt;

    /** 更新时间 */
    private Instant updatedAt;

}
