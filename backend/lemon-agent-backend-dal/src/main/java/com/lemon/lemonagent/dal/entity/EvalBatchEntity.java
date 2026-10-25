package com.lemon.lemonagent.dal.entity;

import lombok.Data;

import java.time.Instant;

/** 评测批次（表 eval_batch）。 */
@Data
public class EvalBatchEntity {

    /** 批次 ID */
    private Long id;

    /** 归属用户 */
    private Long userId;

    /** 被测 Agent */
    private Long agentId;

    /** 发起时的图版本 */
    private Integer graphVersion;

    /** 备注 */
    private String note;

    /** 创建时间 */
    private Instant createdAt;

}
