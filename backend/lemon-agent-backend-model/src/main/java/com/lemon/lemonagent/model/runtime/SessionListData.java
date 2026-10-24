package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.List;

/** 会话列表数据。 */
@Data
public class SessionListData {

    /** 总数 */
    private long total;

    /** 条目 */
    private List<SessionListItem> items;

}
