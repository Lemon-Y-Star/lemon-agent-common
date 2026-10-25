package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.dal.entity.AgentRunEntity;
import com.lemon.lemonagent.dal.repository.AgentRunRepository;
import com.lemon.lemonagent.model.runtime.LlmOverviewData;
import com.lemon.lemonagent.model.runtime.LlmSummaryData;
import com.lemon.lemonagent.model.runtime.ModelUsageData;
import com.lemon.lemonagent.model.runtime.RunListItem;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LLM 观测（v3.8，PRD FR-16 观测切片）：按模型聚合的调用量 / token / 时长 / 失败率。
 *
 * <p>数据源就是 {@code agent_run}：执行器解析连接时把「实际用的模型名」回填到 run 上
 * （{@code model_name / model_conn_id}），观测不另建事实表——与评测同一条纪律：
 * 派生视图实时算，不复制运行时的事实。</p>
 */
@Service
public class LlmObservabilityService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private final AgentRunRepository runRepository;

    public LlmObservabilityService(AgentRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    /**
     * 观测总览。
     *
     * @param userId 入参：用户
     * @param days   入参：时间窗天数（<=0 = 全部）
     * @return 出参：汇总 + 按模型 + 最近调用
     */
    public LlmOverviewData overview(Long userId, int days) {
        Instant since = days > 0 ? Instant.now().minus(Duration.ofDays(days)) : null;
        LlmOverviewData data = new LlmOverviewData();
        data.setSummary(toSummary(runRepository.summarizeByUser(userId, since)));

        List<ModelUsageData> byModel = new ArrayList<>();
        for (Map<String, Object> row : runRepository.sumByModel(userId, since)) {
            byModel.add(toUsage(row));
        }
        data.setByModel(byModel);

        List<RunListItem> recent = new ArrayList<>();
        for (AgentRunEntity run : runRepository.findByUser(userId, null, null, null, 0, 15)) {
            recent.add(toItem(run));
        }
        data.setRecent(recent);
        return data;
    }

    private LlmSummaryData toSummary(Map<String, Object> row) {
        LlmSummaryData data = new LlmSummaryData();
        data.setCalls(num(row.get("calls")));
        data.setFailed(num(row.get("failed")));
        data.setInputTokens(num(row.get("input_tokens")));
        data.setOutputTokens(num(row.get("output_tokens")));
        data.setAvgLatencyMs(Math.round(dbl(row.get("avg_latency_ms"))));
        return data;
    }

    private ModelUsageData toUsage(Map<String, Object> row) {
        ModelUsageData data = new ModelUsageData();
        data.setModelName(String.valueOf(row.get("model_name")));
        data.setCalls(num(row.get("calls")));
        data.setFailed(num(row.get("failed")));
        data.setInputTokens(num(row.get("input_tokens")));
        data.setOutputTokens(num(row.get("output_tokens")));
        data.setAvgLatencyMs(Math.round(dbl(row.get("avg_latency_ms"))));
        return data;
    }

    private RunListItem toItem(AgentRunEntity run) {
        RunListItem item = new RunListItem();
        item.setRunId(run.getId());
        item.setStatus(run.getStatus());
        item.setStrategy(run.getStrategy());
        item.setErrorCode(run.getErrorCode());
        item.setTraceId(run.getTraceId());
        item.setLastSeq(run.getLastSeq());
        item.setStartedAt(run.getStartedAt() == null ? null : run.getStartedAt().atOffset(ZoneOffset.UTC).format(ISO));
        item.setEndedAt(run.getEndedAt() == null ? null : run.getEndedAt().atOffset(ZoneOffset.UTC).format(ISO));
        item.setModelName(run.getModelName());
        item.setInputTokens(run.getInputTokens());
        item.setOutputTokens(run.getOutputTokens());
        if (run.getStartedAt() != null && run.getEndedAt() != null) {
            item.setLatencyMs(Duration.between(run.getStartedAt(), run.getEndedAt()).toMillis());
        }
        return item;
    }

    private static long num(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    private static double dbl(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0d;
    }

}
