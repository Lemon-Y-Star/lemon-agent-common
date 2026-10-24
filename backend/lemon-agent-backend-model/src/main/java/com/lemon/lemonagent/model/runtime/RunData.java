package com.lemon.lemonagent.model.runtime;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 发起运行返回的数据（契约 4.5）。 */
@Data
@AllArgsConstructor
public class RunData {

    /** 运行 ID */
    private Long runId;

    /** 会话 ID（懒创建时 = 新建会话） */
    private Long sessionId;

    /** 全链路追踪 ID（32 位 hex；响应头 X-Trace-Id 同值） */
    private String traceId;

    /** 状态：创建即 RUNNING */
    private String status;

    /** 事件序号游标（run_started 已落库，通常 = 1） */
    private long lastSeq;

    /** 幂等命中：true = 返回原 run，不二次调模型 */
    private boolean duplicated;

    /** 开始时间（ISO-8601） */
    private String startedAt;

}
