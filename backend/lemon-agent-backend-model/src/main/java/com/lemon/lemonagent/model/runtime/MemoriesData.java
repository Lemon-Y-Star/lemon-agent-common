package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.List;

/** 长期记忆列表。 */
@Data
public class MemoriesData {

    /** 条目（倒序） */
    private List<MemoryData> items;

    /** 运行时最多注入多少条（给前端提示） */
    private int injectLimit;
}
