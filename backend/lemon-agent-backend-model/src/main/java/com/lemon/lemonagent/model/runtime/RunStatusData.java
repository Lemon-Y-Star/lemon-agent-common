package com.lemon.lemonagent.model.runtime;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 取消 / 审批后返回的运行状态（契约 4.8 / 4.9）。 */
@Data
@AllArgsConstructor
public class RunStatusData {

    /** 运行 ID */
    private Long runId;

    /** 状态 */
    private String status;

    /** 事件序号游标（前端拿它续流） */
    private long lastSeq;

}
