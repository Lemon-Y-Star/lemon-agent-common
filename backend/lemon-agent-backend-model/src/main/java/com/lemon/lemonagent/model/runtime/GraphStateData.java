package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行图进度（落 {@code agent_run.graph_state_json}）。
 *
 * <p>无内存态：节点审批挂起、进程重启后都按这里的 done/outputs 续跑，不会重复执行已完成的节点。</p>
 */
@Data
public class GraphStateData {

    /** 已完成节点 ID（按完成顺序） */
    private List<String> done = new ArrayList<>();

    /** 节点产出（LLM 文本 / TOOL 结果摘要），供下游节点拼接上下文 */
    private Map<String, String> outputs = new LinkedHashMap<>();

    /** 当前正在执行的节点 ID（空 = 无） */
    private String current;

    /** 累计输入 tokens（跨节审批恢复也累加） */
    private long inputTokens;

    /** 累计输出 tokens */
    private long outputTokens;

}
