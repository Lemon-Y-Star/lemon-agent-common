package com.lemon.lemonagent.model.runtime;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 创建会话返回的数据（契约 4.2）。 */
@Data
@AllArgsConstructor
public class SessionData {

    /** 会话 ID */
    private Long sessionId;

    /** 创建时间（ISO-8601） */
    private String createdAt;

}
