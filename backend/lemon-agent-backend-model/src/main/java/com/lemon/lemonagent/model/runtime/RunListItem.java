package com.lemon.lemonagent.model.runtime;

import lombok.Data;

/** 运行列表条目（契约 4.10，必须 user_id 过滤）。 */
@Data
public class RunListItem {

    /** 运行 ID */
    private Long runId;

    /** 状态 */
    private String status;

    /** 策略 */
    private String strategy;

    /** 失败归因 */
    private String errorCode;

    /** 全链路 ID */
    private String traceId;

    /** 事件序号游标 */
    private long lastSeq;

    /** 开始时间 */
    private String startedAt;

    /** 结束时间 */
    private String endedAt;

}
