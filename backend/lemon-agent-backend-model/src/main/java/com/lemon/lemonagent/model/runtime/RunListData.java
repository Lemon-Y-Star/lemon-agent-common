package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.List;

/** 运行列表数据。 */
@Data
public class RunListData {

    /** 总数 */
    private long total;

    /** 条目 */
    private List<RunListItem> items;

}
