package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.List;

/** LLM 观测总览：汇总 + 按模型聚合 + 最近调用。 */
@Data
public class LlmOverviewData {

    /** 时间窗汇总 */
    private LlmSummaryData summary;

    /** 按模型聚合 */
    private List<ModelUsageData> byModel;

    /** 最近调用（含模型名 / tokens / 时长） */
    private List<RunListItem> recent;

}
