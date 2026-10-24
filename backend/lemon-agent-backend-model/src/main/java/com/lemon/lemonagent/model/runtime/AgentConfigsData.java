package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.List;

/** Agent 列表数据：平台公共在前，个人自建在后。 */
@Data
public class AgentConfigsData {

    /** 平台公共 Agent */
    private List<AgentConfigData> platform;

    /** 我的 Agent */
    private List<AgentConfigData> mine;

}
