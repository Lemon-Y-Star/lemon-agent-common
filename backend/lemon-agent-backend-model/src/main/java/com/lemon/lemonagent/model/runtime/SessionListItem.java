package com.lemon.lemonagent.model.runtime;

import lombok.Data;

/** 会话列表条目（契约 4.3，按 lastActiveAt 倒序）。 */
@Data
public class SessionListItem {

    /** 会话 ID */
    private Long sessionId;

    /** 标题 */
    private String title;

    /** 最近活跃时间 */
    private String lastActiveAt;

    /** 消息条数 */
    private int messageCount;

}
