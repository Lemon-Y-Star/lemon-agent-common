package com.lemon.lemonagent.servicecore.runtime.agent;

import com.lemon.lemonagent.dal.entity.AgentConfigEntity;
import com.lemon.lemonagent.dal.entity.AgentRunEntity;
import com.lemon.lemonagent.dal.entity.AgentSessionEntity;
import com.lemon.lemonagent.dal.repository.AgentRunRepository;
import com.lemon.lemonagent.dal.repository.AgentSessionRepository;
import com.lemon.lemonagent.model.runtime.AgentLogsData;
import com.lemon.lemonagent.model.runtime.AgentMetricsData;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 运营数据服务（v4.5.2，Dify「日志 / 监控」页同款）：
 * 按会话聚合的日志视图 + 按天聚合的监控指标。数据全部来自 agent_run 既有表，无新表。
 */
@Service
public class AgentOpsService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    /** 日志页最多返回的会话数。 */
    private static final int MAX_SESSIONS = 50;

    /** 时间窗口上限（天）：防全表聚合。 */
    private static final int MAX_DAYS = 90;

    private final AgentConfigService agentConfigService;

    private final AgentRunRepository agentRunRepository;

    private final AgentSessionRepository agentSessionRepository;

    public AgentOpsService(AgentConfigService agentConfigService,
                           AgentRunRepository agentRunRepository,
                           AgentSessionRepository agentSessionRepository) {
        this.agentConfigService = agentConfigService;
        this.agentRunRepository = agentRunRepository;
        this.agentSessionRepository = agentSessionRepository;
    }

    /**
     * 会话维度日志：该 Agent 时间窗内的运行按会话聚合。
     *
     * @param userId  入参：归属用户
     * @param agentId 入参：Agent ID
     * @param days    入参：时间窗（天，1-90）
     * @return 出参：会话日志列表（最近活跃在前）
     */
    public AgentLogsData logs(Long userId, Long agentId, int days) {
        requireOwned(userId, agentId);
        Instant since = Instant.now().minus(Math.min(Math.max(days, 1), MAX_DAYS), ChronoUnit.DAYS);
        List<AgentRunEntity> runs = agentRunRepository.findByUser(userId, null, null, null, agentId, 0, 500);

        Map<Long, AgentLogsData.SessionLog> bySession = new LinkedHashMap<>();
        for (AgentRunEntity r : runs) {
            if (r.getStartedAt() != null && r.getStartedAt().isBefore(since)) {
                continue;
            }
            AgentLogsData.SessionLog log = bySession.computeIfAbsent(r.getSessionId(), k -> {
                AgentLogsData.SessionLog s = new AgentLogsData.SessionLog();
                s.setSessionId(k);
                return s;
            });
            log.setRunCount(log.getRunCount() + 1);
            if ("COMPLETED".equals(r.getStatus())) {
                log.setOkCount(log.getOkCount() + 1);
            }
            long tokens = (r.getInputTokens() == null ? 0 : r.getInputTokens())
                    + (r.getOutputTokens() == null ? 0 : r.getOutputTokens());
            log.setTotalTokens(log.getTotalTokens() + tokens);
            if (log.getFirstAt() == null || r.getStartedAt().toString().compareTo(log.getFirstAt()) < 0) {
                log.setFirstAt(r.getStartedAt().toString());
            }
            if (log.getLastAt() == null || r.getStartedAt().toString().compareTo(log.getLastAt()) > 0) {
                log.setLastAt(r.getStartedAt().toString());
            }
        }
        // 会话标题补齐（不存在的会话容错为空标题）
        AgentLogsData data = new AgentLogsData();
        List<AgentLogsData.SessionLog> list = new ArrayList<>(bySession.values());
        for (AgentLogsData.SessionLog s : list) {
            AgentSessionEntity session = agentSessionRepository.findByIdAndUser(s.getSessionId(), userId);
            s.setTitle(session == null ? "（会话已删除）" : session.getTitle());
        }
        list.sort((a, b) -> String.valueOf(b.getLastAt()).compareTo(String.valueOf(a.getLastAt())));
        data.setSessions(list.size() > MAX_SESSIONS ? list.subList(0, MAX_SESSIONS) : list);
        return data;
    }

    /**
     * 监控指标：按天聚合运行/会话/token/成功率/好评率。
     *
     * @param userId  入参：归属用户
     * @param agentId 入参：Agent ID
     * @param days    入参：时间窗（天，1-90）
     * @return 出参：日指标 + 汇总
     */
    public AgentMetricsData metrics(Long userId, Long agentId, int days) {
        requireOwned(userId, agentId);
        Instant since = Instant.now().minus(Math.min(Math.max(days, 1), MAX_DAYS), ChronoUnit.DAYS);
        List<Map<String, Object>> rows = agentRunRepository.sumByDayByAgent(userId, agentId, since);

        AgentMetricsData data = new AgentMetricsData();
        List<AgentMetricsData.DayMetric> dayList = new ArrayList<>();
        long totalRuns = 0;
        long totalSessions = 0;
        long totalTokens = 0;
        long totalOk = 0;
        long totalUp = 0;
        long totalRated = 0;
        DateTimeFormatter dayFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
        for (Map<String, Object> row : rows) {
            AgentMetricsData.DayMetric d = new AgentMetricsData.DayMetric();
            Object day = row.get("d");
            // DATE() 在 MySQL 驱动返回 java.sql.Date（部分路径返回 Timestamp/LocalDate），统一走 toString 兜底
            d.setDate(day == null ? "unknown"
                    : day instanceof java.sql.Timestamp ts ? dayFmt.format(ts.toInstant())
                            : day instanceof java.sql.Date dt ? dt.toString()
                                    : day instanceof LocalDate ld ? ld.toString()
                                            : String.valueOf(day));
            long runs = ((Number) row.getOrDefault("runs", 0)).longValue();
            long sessions = ((Number) row.getOrDefault("sessions", 0)).longValue();
            long tin = ((Number) row.getOrDefault("input_tokens", 0)).longValue();
            long tout = ((Number) row.getOrDefault("output_tokens", 0)).longValue();
            d.setRuns(runs);
            d.setSessions(sessions);
            d.setTokens(tin + tout);
            dayList.add(d);
            totalRuns += runs;
            totalSessions += sessions;
            totalTokens += tin + tout;
            totalOk += ((Number) row.getOrDefault("ok", 0)).longValue();
            totalUp += ((Number) row.getOrDefault("up", 0)).longValue();
            totalRated += ((Number) row.getOrDefault("rated", 0)).longValue();
        }
        data.setDays(dayList);
        data.setTotalRuns(totalRuns);
        data.setTotalSessions(totalSessions);
        data.setTotalTokens(totalTokens);
        data.setSuccessRate(totalRuns == 0 ? 0 : Math.round(totalOk * 1000.0 / totalRuns) / 10.0);
        data.setLikeRate(totalRated == 0 ? 0 : Math.round(totalUp * 1000.0 / totalRated) / 10.0);
        return data;
    }

    /** 归属校验：只能看自己的 Agent（平台公共无运营数据）。 */
    private void requireOwned(Long userId, Long agentId) {
        AgentConfigEntity agent = agentConfigService.findById(agentId);
        if (agent == null || agent.getUserId() == null || !agent.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "Agent 不存在或不属于你");
        }
    }
}
