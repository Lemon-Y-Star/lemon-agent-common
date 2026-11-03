package com.lemon.lemonagent.servicecore.runtime.obs;

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

    /** 业务日界统一东八（v4.5.5）：容器 JVM 可能是 UTC，LocalDate.now() 不带 zone 会把「今天」算错 8 小时。 */
    private static final java.time.ZoneId EAST8 = java.time.ZoneId.of("Asia/Shanghai");

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
        // v4.5.5：今天按东八算（容器 JVM=UTC 时 LocalDate.now() 会让今天 0-16 点不进窗口）
        LocalDate today = LocalDate.now(EAST8);
        LocalDate since = today.minusDays(days - 1L);
        String sinceStr = since.toString();

        UsageOverviewData data = new UsageOverviewData();
        data.setDays(days);

        // 状态分布（窗口边界：TIMESTAMP 列与日期字符串比较按 MySQL 会话时区（东八）解释，边界=东八午夜）
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

        // 按天聚合（v4.5.5 时区口径：MySQL 会话时区=东八，DATE() 分桶本来就对；改 DATE_FORMAT
        // 在 SQL 端直接出字符串，绕开 java.sql.Date.toString() 按 JVM 默认时区格式化的坑——
        // 部署容器 JVM 若是 UTC 会把日期差一天）
        Map<String, UsageOverviewData.DailyUsage> byDay = new HashMap<>();
        jdbcTemplate.query(
                "SELECT DATE_FORMAT(started_at,'%Y-%m-%d') AS d, COUNT(*) AS runs, "
                        + "COALESCE(SUM(input_tokens),0) AS in_tok, COALESCE(SUM(output_tokens),0) AS out_tok "
                        + "FROM agent_run WHERE user_id = ? AND started_at >= ? "
                        + "GROUP BY d",
                rs -> {
                    String day = rs.getString("d");
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
