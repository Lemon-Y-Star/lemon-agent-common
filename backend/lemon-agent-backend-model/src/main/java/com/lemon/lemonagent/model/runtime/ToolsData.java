package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.List;

/** 可授权工具清单（v3.0 Agent 三要素：工具授权）。 */
@Data
public class ToolsData {

    /** 工具列表（注册顺序：只读 → 检索 → 写操作） */
    private List<ToolInfoData> tools;

}
