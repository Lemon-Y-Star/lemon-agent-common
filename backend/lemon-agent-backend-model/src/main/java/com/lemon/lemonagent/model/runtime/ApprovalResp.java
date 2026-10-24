package com.lemon.lemonagent.model.runtime;

import com.lemon.architecture.model.BaseResult;

/** 提交审批决定响应：data 为提交后的运行状态（APPROVE → RUNNING，前端拿 lastSeq 续流）。 */
public class ApprovalResp extends BaseResult<RunStatusData> {
}
