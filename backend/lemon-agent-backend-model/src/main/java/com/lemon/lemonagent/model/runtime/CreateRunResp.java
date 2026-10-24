package com.lemon.lemonagent.model.runtime;

import com.lemon.architecture.model.BaseResult;

/**
 * 发起运行响应。
 *
 * <p>data 通常为 {@link RunData}；会话互斥（409 SESSION_BUSY）时为
 * {@code {"activeRunId": <long>}}（契约 4.5 错误包络带出），因此泛型为 Object。</p>
 */
public class CreateRunResp extends BaseResult<Object> {
}
