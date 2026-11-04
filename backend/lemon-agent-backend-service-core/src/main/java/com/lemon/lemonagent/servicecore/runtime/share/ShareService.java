package com.lemon.lemonagent.servicecore.runtime.share;

import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import com.lemon.lemonagent.dal.entity.AgentConfigEntity;
import com.lemon.lemonagent.dal.entity.AgentRunEntity;
import com.lemon.lemonagent.dal.entity.AgentSessionEntity;
import com.lemon.lemonagent.dal.entity.AgentShareEntity;
import com.lemon.lemonagent.dal.repository.AgentRunRepository;
import com.lemon.lemonagent.dal.repository.AgentSessionRepository;
import com.lemon.lemonagent.dal.repository.AgentShareRepository;
import com.lemon.lemonagent.model.runtime.ShareInfoData;
import com.lemon.lemonagent.servicecore.runtime.engine.AgentRuntimeService;
import com.lemon.lemonagent.model.runtime.RunStatus;
import com.lemon.lemonagent.servicecore.runtime.graph.GraphCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 分享（v4.6.0）：免登录公开对话页的领域服务。
 *
 * <p><b>安全边界</b>：公开页只暴露 Agent 的门面信息（名称/描述/开场白/建议问题），
 * instruction 与工具配置永不出库；匿名会话挂在 Agent 主人名下（share_id 标记，主人侧栏可见）；
 * 内存滑动窗口频控（每 shareCode+IP 每分钟 8 条）兜底滥用；口令可选。</p>
 */
@Slf4j
@Service
public class ShareService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789";
    /** 频控窗口（毫秒）与窗口内消息上限。 */
    private static final long RATE_WINDOW_MS = 60_000L;
    private static final int RATE_LIMIT = 8;

    private final AgentShareRepository shareRepository;
    private final AgentSessionRepository sessionRepository;
    private final AgentRunRepository runRepository;
    private final JdbcTemplate jdbcTemplate;
    private final AgentRuntimeService agentRuntimeService;

    /** 频控桶：shareCode:ip → 窗口内时间戳队列。 */
    private final Map<String, Deque<Long>> rateBuckets = new ConcurrentHashMap<>();

    public ShareService(AgentShareRepository shareRepository,
                        AgentSessionRepository sessionRepository,
                        AgentRunRepository runRepository,
                        JdbcTemplate jdbcTemplate,
                        AgentRuntimeService agentRuntimeService) {
        this.shareRepository = shareRepository;
        this.sessionRepository = sessionRepository;
        this.runRepository = runRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.agentRuntimeService = agentRuntimeService;
    }

    /**
     * 设置分享（owner 校验 + custom 表单拦截）：每 Agent 一条，upsert。
     *
     * @param userId     入参：当前用户（须为 Agent 归属者）
     * @param agentId    入参：Agent
     * @param enabled    入参：开关
     * @param accessCode 入参：访问口令（可空 = 无需口令）
     * @return 出参：分享配置
     */
    public AgentShareEntity setShare(Long userId, Long agentId, boolean enabled, String accessCode) {
        AgentConfigEntity agent = agentConfigRepository_getOwned(userId, agentId);
        String code = accessCode == null || accessCode.isBlank() ? null : accessCode.trim();
        // custom 策略且执行图 START 配了表单的 Agent 不支持分享：匿名调用无表单交互，
        // createRun 的 resolveFormValues 会在必填校验处直接拒绝（v4.5.4 语义）
        if (enabled && "custom".equals(agent.getStrategy())) {
            if (startFormExists(agent.getId())) {
                throw new BusinessException(ResultCodes.VALIDATION_ERROR,
                        "该 Agent 的执行图开始节点配置了输入表单，暂不支持分享（表单无法在公开页收集）");
            }
        }
        AgentShareEntity entity = new AgentShareEntity();
        entity.setUserId(userId);
        entity.setAgentId(agentId);
        entity.setShareCode(randomCode());
        entity.setAccessCode(code);
        entity.setEnabled(enabled ? 1 : 0);
        shareRepository.upsert(entity);
        log.info("分享已设置 userId:{} agentId:{} enabled:{} code:{}", userId, agentId, enabled, entity.getShareCode());
        return entity;
    }

    /** 查分享配置（owner 视角，返回 share_code 供拼链接）。 */
    public AgentShareEntity getShare(Long userId, Long agentId) {
        AgentConfigEntity agent = agentConfigRepository_getOwned(userId, agentId);
        AgentShareEntity share = shareRepository.findByAgentId(agent.getId());
        return share == null ? null : share;
    }

    /** 公开信息（免登录）：只出门面字段，instruction/tools 永不出库。 */
    public ShareInfoData publicInfo(String shareCode, String accessCode) {
        AgentShareEntity share = requireEnabled(shareCode, accessCode);
        Map<String, Object> agent = queryAgent(share.getAgentId());
        ShareInfoData data = new ShareInfoData();
        data.setName(String.valueOf(agent.getOrDefault("name", "")));
        data.setDescription(String.valueOf(agent.getOrDefault("description", "")));
        data.setOpeningStatement((String) agent.get("openingStatement"));
        Object questions = agent.get("suggestedQuestions");
        if (questions instanceof java.util.List<?> list) {
            data.setSuggestedQuestions(list.stream().map(String::valueOf).toList());
        }
        return data;
    }

    /**
     * 公开发起运行（免登录）：频控 → 校验 → 以主人身份建分享会话 + 运行。
     *
     * @return 出参：runId + sessionId（前端轮询/订阅事件流用）
     */
    public Map<String, Object> publicRun(String shareCode, String accessCode, String message, String clientIp) {
        AgentShareEntity share = requireEnabled(shareCode, accessCode);
        rateLimit(shareCode + ":" + clientIp);
        Long ownerId = share.getUserId();
        // 分享会话：挂在主人名下（配额/运营可见），share_id 标记供 SSE 校验与主人辨识
        AgentSessionEntity session = new AgentSessionEntity();
        session.setUserId(ownerId);
        session.setTitle("分享 · " + (message.length() > 20 ? message.substring(0, 20) : message));
        sessionRepository.insert(session);
        sessionRepository.markShare(session.getId(), share.getId());
        var run = agentRuntimeService.createRun(ownerId, session.getId(), share.getAgentId(),
                message, null, "share_" + java.util.UUID.randomUUID(), null, null, null);
        return Map.of("runId", run.getRunId(), "sessionId", run.getSessionId());
    }

    /** 公开 SSE 订阅：校验 run 属于该分享的会话后按游标续流（归属校验由 shareId 关系承担）。 */
    public SseEmitter publicSubscribe(String shareCode, Long runId, Long afterQuery, String lastEventId) {
        AgentShareEntity share = requireEnabled(shareCode, null);
        AgentRunEntity run = runRepository.findById(runId);
        Long sessionShareId = run == null ? null : querySessionShareId(run.getSessionId());
        log.info("公开SSE校验 shareCode:{} runId:{} runFound:{} sessionShareId:{} shareId:{}",
                shareCode, runId, run != null, sessionShareId, share.getId());
        if (run == null) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "运行不存在");
        }
        if (sessionShareId == null || !sessionShareId.equals(share.getId())) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "运行不存在");
        }
        return agentRuntimeService.subscribeShared(runId, afterQuery, lastEventId, share.getId());
    }

    /** 运行归属校验（分享页取消/状态查询用）：run 必须属于该分享的会话。 */
    public boolean ownsRun(String shareCode, Long runId) {
        AgentShareEntity share = shareRepository.findByCode(shareCode);
        if (share == null) {
            return false;
        }
        AgentRunEntity run = runRepository.findById(runId);
        if (run == null) {
            return false;
        }
        Long sessionShareId = querySessionShareId(run.getSessionId());
        return share.getId().equals(sessionShareId);
    }

    // ---------- 内部 ----------

    private AgentShareEntity requireEnabled(String shareCode, String accessCode) {
        AgentShareEntity share = shareRepository.findByCode(shareCode);
        if (share == null || share.getEnabled() == null || share.getEnabled() != 1) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "分享不存在或已关闭");
        }
        if (share.getAccessCode() != null && !share.getAccessCode().isBlank()
                && !share.getAccessCode().equals(accessCode == null ? "" : accessCode.trim())) {
            throw new BusinessException(ResultCodes.FORBIDDEN, "访问口令不正确");
        }
        return share;
    }

    private void rateLimit(String key) {
        long now = System.currentTimeMillis();
        Deque<Long> bucket = rateBuckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && now - bucket.peekFirst() > RATE_WINDOW_MS) {
                bucket.pollFirst();
            }
            if (bucket.size() >= RATE_LIMIT) {
                throw new BusinessException(ResultCodes.VALIDATION_ERROR,
                        "该分享页提问过于频繁，请稍后再试");
            }
            bucket.addLast(now);
        }
    }

    /** owner 视角取 Agent（归属校验，防横向越权设置他人分享）。 */
    private AgentConfigEntity agentConfigRepository_getOwned(Long userId, Long agentId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT id, user_id FROM agent_config WHERE id = ?", agentId);
        Long ownerId = ((Number) row.get("user_id")).longValue();
        if (!ownerId.equals(userId)) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "Agent 不存在");
        }
        AgentConfigEntity entity = new AgentConfigEntity();
        entity.setId(agentId);
        entity.setUserId(ownerId);
        Object strategy = jdbcTemplate.queryForMap(
                "SELECT strategy FROM agent_config WHERE id = ?", agentId).get("strategy");
        entity.setStrategy(strategy == null ? null : String.valueOf(strategy));
        return entity;
    }

    private Map<String, Object> queryAgent(Long agentId) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT name, description, opening_statement, suggested_questions "
                        + "FROM agent_config WHERE id = ?", agentId);
        // queryForMap 的 key 是列名（下划线），转驼峰供 ShareInfoData 使用
        Map<String, Object> agent = new java.util.HashMap<>();
        agent.put("name", row.get("name"));
        agent.put("description", row.get("description"));
        agent.put("openingStatement", row.get("opening_statement"));
        Object sq = row.get("suggested_questions");
        if (sq instanceof String json && !json.isBlank()) {
            try {
                agent.put("suggestedQuestions", new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(json, java.util.List.class));
            } catch (Exception e) {
                agent.put("suggestedQuestions", java.util.List.of());
            }
        }
        return agent;
    }

    private Long querySessionShareId(Long sessionId) {
        try {
            Object v = jdbcTemplate.queryForMap(
                    "SELECT share_id FROM agent_session WHERE id = ?", sessionId).get("share_id");
            return v == null ? null : ((Number) v).longValue();
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    private boolean startFormExists(Long agentId) {
        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(
                    "SELECT graph_json FROM agent_graph WHERE agent_id = ? ORDER BY version DESC LIMIT 1", agentId);
            String graphJson = String.valueOf(row.get("graph_json"));
            com.lemon.lemonagent.model.runtime.AgentGraphData graph = GraphCodec.decodeGraph(graphJson);
            return graph.getNodes().stream()
                    .anyMatch(n -> "START".equals(n.getType())
                            && n.getFormSchema() != null && !n.getFormSchema().isEmpty());
        } catch (Exception e) {
            // 图缺失/解析失败按无表单处理（custom 运行时本就会失败，不放大到这里）
            return false;
        }
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
        }
        return sb.toString();
    }
}
