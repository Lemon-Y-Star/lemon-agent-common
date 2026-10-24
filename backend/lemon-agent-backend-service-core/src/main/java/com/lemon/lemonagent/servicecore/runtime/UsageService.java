package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.dal.repository.AgentRunRepository;
import com.lemon.lemonagent.model.runtime.UsageOverviewData;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用量领域服务（PRD FR-16/FR-17 简化版）：按天聚合 token 用量与状态分布。
 *
 * <p>聚合直接走 SQL（GROUP BY 日期），数据源是 agent_run 的用量列——
 * 运行完成时已写入，无需额外打点。</p>
 */
@Service
public class UsageService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AgentRunRepository runRepository;

    private final JdbcTemplate jdbcTemplate;

    public UsageService(AgentRunRepository runRepository, JdbcTemplate jdbcTemplate) {
        this.runRepository = runRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 近 N 天用量总览。
     *
     * @param userId 入参：归属用户
     * @param days   入参：统计窗口（1..30）
     * @return 出参：总览数据
     */
    public UsageOverviewData overview(Long userId, int days) {
        LocalDate today = LocalDate.now();
        LocalDate since = today.minusDays(days - 1L);
        String sinceStr = since.toString();

        UsageOverviewData data = new UsageOverviewData();
        data.setDays(days);

        // 状态分布
        List<UsageOverviewData.StatusCount> statusCounts = new ArrayList<>();
        jdbcTemplate.query(
                "SELECT status, COUNT(*) AS cnt FROM agent_run WHERE user_id = ? "
                        + "AND started_at >= ? GROUP BY status ORDER BY cnt DESC",
                rs -> {
                    UsageOverviewData.StatusCount sc = new UsageOverviewData.StatusCount();
                    sc.setStatus(rs.getString("status"));
                    sc.setCount(rs.getLong("cnt"));
                    statusCounts.add(sc);
                }, userId, sinceStr);
        data.setStatusCounts(statusCounts);
        data.setTotalRuns(statusCounts.stream().mapToLong(UsageOverviewData.StatusCount::getCount).sum());

        // 按天聚合（MySQL：DATE(started_at)）
        Map<String, UsageOverviewData.DailyUsage> byDay = new HashMap<>();
        jdbcTemplate.query(
                "SELECT DATE(started_at) AS d, COUNT(*) AS runs, "
                        + "COALESCE(SUM(input_tokens),0) AS in_tok, COALESCE(SUM(output_tokens),0) AS out_tok "
                        + "FROM agent_run WHERE user_id = ? AND started_at >= ? GROUP BY DATE(started_at)",
                rs -> {
                    String day = rs.getDate("d").toString();
                    UsageOverviewData.DailyUsage du = new UsageOverviewData.DailyUsage();
                    du.setDate(day);
                    du.setRuns(rs.getLong("runs"));
                    du.setInputTokens(rs.getLong("in_tok"));
                    du.setOutputTokens(rs.getLong("out_tok"));
                    byDay.put(day, du);
                }, userId, sinceStr);

        // 缺失天补零，保证前端柱状图连续
        List<UsageOverviewData.DailyUsage> daily = new ArrayList<>();
        long totalIn = 0;
        long totalOut = 0;
        for (int i = 0; i < days; i++) {
            String day = since.plusDays(i).format(DAY);
            UsageOverviewData.DailyUsage du = byDay.getOrDefault(day, emptyDay(day));
            daily.add(du);
            totalIn += du.getInputTokens();
            totalOut += du.getOutputTokens();
        }
        data.setDaily(daily);
        data.setTotalInputTokens(totalIn);
        data.setTotalOutputTokens(totalOut);
        return data;
    }

    private UsageOverviewData.DailyUsage emptyDay(String day) {
        UsageOverviewData.DailyUsage du = new UsageOverviewData.DailyUsage();
        du.setDate(day);
        du.setRuns(0);
        du.setInputTokens(0);
        du.setOutputTokens(0);
        return du;
    }

}
