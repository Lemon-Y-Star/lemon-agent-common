package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.List;

/** 会话消息 + 运行快照（契约 4.4，刷新恢复数据源）。 */
@Data
public class SessionDetailData {

    /** 会话 ID */
    private Long sessionId;

    /** 消息（升序） */
    private List<MessageItem> messages;

    /** 该会话下全部运行 */
    private List<RunBrief> runs;

}
