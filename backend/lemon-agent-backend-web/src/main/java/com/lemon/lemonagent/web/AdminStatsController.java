package com.lemon.lemonagent.web;

import com.lemon.lemonagent.dal.entity.UserAccountEntity;
import com.lemon.lemonagent.dal.repository.DemoTicketRepository;
import com.lemon.lemonagent.dal.repository.UserAccountRepository;
import com.lemon.lemonagent.model.runtime.AdminStatsData;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.BaseResult;
import com.lemon.architecture.model.ResultCodes;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端统计（PRD FR-22.1 简化版）：仅 admin 角色可访问的平台总量统计。
 */
@RestController
@RequestMapping("/api/v1/admin/stats")
public class AdminStatsController {

    /** 用户仓储（角色校验）。 */
    @Resource
    private UserAccountRepository userAccountRepository;

    /** 工单仓储。 */
    @Resource
    private DemoTicketRepository ticketRepository;

    /** MySQL 直查（管理端聚合，主数据源）。 */
    @Resource
    private JdbcTemplate jdbcTemplate;

    /**
     * 平台统计总览（admin only）。
     *
     * @param userId 当前用户（校验 admin 角色）
     * @return 统计数据
     */
    @GetMapping
    public BaseResult<AdminStatsData> stats(@RequestAttribute("lemon.userId") Long userId) {
        UserAccountEntity user = userAccountRepository.findById(userId);
        if (user == null || !"admin".equals(user.getRole())) {
            throw new BusinessException(ResultCodes.FORBIDDEN, "仅管理员可访问");
        }
        AdminStatsData data = new AdminStatsData();
        data.setUsers(count("user_account"));
        data.setSessions(count("agent_session"));
        data.setRuns(count("agent_run"));
        data.setMessages(count("agent_message"));
        data.setTickets(count("demo_ticket"));
        Map<String, Long> runsByStatus = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT status, COUNT(*) AS cnt FROM agent_run GROUP BY status ORDER BY cnt DESC");
        for (Map<String, Object> row : rows) {
            runsByStatus.put(String.valueOf(row.get("status")), ((Number) row.get("cnt")).longValue());
        }
        data.setRunsByStatus(runsByStatus);
        Long tokens = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(input_tokens + output_tokens), 0) FROM agent_run", Long.class);
        data.setTotalTokens(tokens == null ? 0 : tokens);
        return BaseResult.success(data);
    }

    private long count(String table) {
        Long n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return n == null ? 0 : n;
    }

}
