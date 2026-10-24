package com.lemon.lemonagent.model.runtime;

import lombok.Data;

/** 消息条目（契约 4.4，messageId 升序）。 */
@Data
public class MessageItem {

    /** 消息 ID */
    private Long messageId;

    /** USER / ASSISTANT */
    private String role;

    /** 正文 */
    private String content;

    /** ASSISTANT 消息关联的运行 */
    private Long runId;

    /** 时间 */
    private String createdAt;

}
