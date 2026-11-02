package com.lemon.lemonagent.servicecore.runtime.agent;

import com.lemon.lemonagent.dal.entity.AgentConfigEntity;
import com.lemon.lemonagent.dal.entity.ModelConnectionEntity;
import com.lemon.lemonagent.dal.entity.WorkflowEntity;
import com.lemon.lemonagent.dal.entity.WorkflowVersionEntity;
import com.lemon.lemonagent.dal.repository.AgentConfigRepository;
import com.lemon.lemonagent.dal.repository.ModelConnectionRepository;
import com.lemon.lemonagent.dal.repository.WorkflowRepository;
import com.lemon.lemonagent.model.runtime.AgentConfigData;
import com.lemon.lemonagent.model.runtime.AgentConfigsData;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import com.lemon.lemonagent.servicecore.runtime.tool.ToolRegistry;

/**
 * Agent 配置领域服务（PRD FR-6 最小充分版）：
 * 平台公共只读、个人自建增删改；运行挂载跟随当前已启用版本（PRD S2 工具语义）。
 *
 * <p>v3.0 三要素：人格（instruction）+ 可用工具（tools 白名单）+ 模型来源（modelConnectionId）。</p>
 */
@Service
public class AgentConfigService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    /** 模型连接「清除绑定」约定值：update 传 0 表示回落用户最新启用连接。 */
    private static final long CLEAR_MODEL_CONNECTION = 0L;

    /** 合法运行策略（与 model 层 Strategy 枚举同口径）。 */
    private static final List<String> STRATEGIES = List.of("auto", "plan", "custom", "fixed");

    /** 短期记忆轮数上限：再多 prompt 就无界膨胀，成本线性增长。 */
    private static final int MAX_HISTORY_TURNS = 50;

    private final AgentConfigRepository repository;

    private final ModelConnectionRepository modelConnectionRepository;

    private final ToolRegistry toolRegistry;

    private final com.lemon.lemonagent.dal.repository.AgentGraphRepository agentGraphRepository;

    private final WorkflowRepository workflowRepository;

    private final tools.jackson.databind.ObjectMapper objectMapper = new tools.jackson.databind.ObjectMapper();

    public AgentConfigService(AgentConfigRepository repository,
                              ModelConnectionRepository modelConnectionRepository,
                              ToolRegistry toolRegistry,
                              com.lemon.lemonagent.dal.repository.AgentGraphRepository agentGraphRepository,
                              WorkflowRepository workflowRepository) {
        this.repository = repository;
        this.modelConnectionRepository = modelConnectionRepository;
        this.toolRegistry = toolRegistry;
        this.agentGraphRepository = agentGraphRepository;
        this.workflowRepository = workflowRepository;
    }

    /**
     * Agent 列表：平台公共在前，个人自建在后。
     *
     * @param userId 入参：归属用户
     * @return 出参：列表数据
     */
    public AgentConfigsData list(Long userId) {
        AgentConfigsData data = new AgentConfigsData();
        data.setPlatform(repository.findPlatform().stream().map(this::toData).toList());
        data.setMine(repository.findByUser(userId).stream().map(this::toData).toList());
        return data;
    }

    /**
     * 创建个人 Agent。
     *
     * @param userId    入参：归属用户
     * @param name      入参：名称
     * @param description 入参：描述
     * @param instruction 入参：系统提示词
     * @param tools     入参：可用工具白名单（null = 全部）
     * @param modelConnectionId 入参：绑定连接（可空）
     * @param workflowId        入参：挂载工作流（可空）
     * @param workflowVersion   入参：挂载的工作流版本（可空 = 跟随工作流当前生效版本）
     * @return 出参：创建后的 Agent
     */
    public AgentConfigData create(Long userId, String name, String description,
                                  String instruction, List<String> tools, String strategy,
                                  Integer historyTurns,
                                  Long modelConnectionId, Long workflowId, Integer workflowVersion) {
        validate(name, instruction);
        AgentConfigEntity entity = new AgentConfigEntity();
        entity.setUserId(userId);
        entity.setName(name.trim());
        entity.setDescription(defaultIfBlank(description, ""));
        entity.setInstruction(instruction.trim());
        entity.setToolsJson(normalizeTools(tools));
        entity.setStrategy(normalizeStrategy(strategy));
        entity.setHistoryTurns(normalizeHistoryTurns(historyTurns));
        entity.setModelConnectionId(requireModelConnection(userId, modelConnectionId));
        Long wf = requireWorkflow(userId, workflowId);
        entity.setWorkflowId(wf);
        entity.setWorkflowVersion(wf == null ? null : resolveWorkflowVersion(wf, workflowVersion));
        entity.setStatus(1);
        entity.setVersion(1);
        repository.insert(entity);
        return toData(entity);
    }

    /**
     * 更新个人 Agent。
     *
     * @param tools 入参：可用工具白名单（null = 全部）
     * @param modelConnectionId 入参：绑定连接（null = 保持不变；0 = 清除绑定）
     * @return 出参：更新后的 Agent
     */
    public AgentConfigData update(Long userId, Long agentId, String name, String description,
                                  String instruction, List<String> tools, String strategy,
                                  Integer historyTurns,
                                  Long modelConnectionId, Boolean enabled,
                                  Long workflowId, Integer workflowVersion) {
        validate(name, instruction);
        AgentConfigEntity entity = requireOwned(userId, agentId);
        entity.setName(name.trim());
        entity.setDescription(defaultIfBlank(description, ""));
        entity.setInstruction(instruction.trim());
        entity.setToolsJson(normalizeTools(tools));
        if (strategy != null) {
            // null = 保持不变（与 modelConnectionId 的 update 语义一致）
            entity.setStrategy(normalizeStrategy(strategy));
        }
        // historyTurns 无条件覆盖：前端表单总是携带该字段（清空 = null = 恢复默认 10 轮），
        // 与 strategy 的「null = 保持不变」不同——这里 null 是一个合法的目标状态
        entity.setHistoryTurns(normalizeHistoryTurns(historyTurns));
        if (modelConnectionId != null) {
            entity.setModelConnectionId(modelConnectionId == CLEAR_MODEL_CONNECTION
                    ? null : requireModelConnection(userId, modelConnectionId));
        }
        if (workflowId != null) {
            // 0 = 取消挂载（回落 Agent 自己的执行图）；非 0 校验归属与启用状态
            Long wf = requireWorkflow(userId, workflowId);
            entity.setWorkflowId(wf);
            entity.setWorkflowVersion(wf == null ? null : resolveWorkflowVersion(wf, workflowVersion));
        } else if (workflowVersion != null) {
            // 只调版本：沿用当前已挂载的工作流；0 = 清除版本（跟随最新）
            entity.setWorkflowVersion(entity.getWorkflowId() == null ? null
                    : resolveWorkflowVersion(entity.getWorkflowId(), workflowVersion));
        }
        if (enabled != null) {
            entity.setStatus(enabled ? 1 : 0);
        }
        repository.update(entity);
        // version 由 SQL 自增、updated_at 由库默认值刷新，必须回读才是真实结果（否则响应体版本号不动）
        AgentConfigEntity updated = repository.findById(agentId);
        return updated == null ? toData(entity) : toData(updated);
    }

    /**
     * 删除个人 Agent。
     */
    public void delete(Long userId, Long agentId) {
        requireOwned(userId, agentId);
        repository.delete(agentId, userId);
        // 级联清理执行图：图是 Agent 的附属物，不留下孤儿版本
        agentGraphRepository.deleteByAgent(agentId);
    }

    /**
     * 用户可用的默认 Agent：优先其最新启用的自建 Agent，否则平台第一个启用的公共 Agent。
     *
     * @param userId 入参：归属用户
     * @return 出参：Agent 实体；一个都没有时返回 null（调用方报错指引创建）
     */
    public AgentConfigEntity firstEnabledForUser(Long userId) {
        List<AgentConfigEntity> mine = repository.findByUser(userId).stream()
                .filter(a -> a.getStatus() != null && a.getStatus() == 1)
                .toList();
        if (!mine.isEmpty()) {
            return mine.get(0);
        }
        return repository.findPlatform().stream()
                .filter(a -> a.getStatus() != null && a.getStatus() == 1)
                .findFirst()
                .orElse(null);
    }

    /**
     * 运行挂载校验：Agent 必须存在、对用户可见且启用。
     *
     * @param userId  入参：用户
     * @param agentId 入参：Agent ID
     * @return 出参：Agent 实体
     */
    public AgentConfigEntity requireUsableForRun(Long userId, Long agentId) {
        AgentConfigEntity entity = repository.findByIdVisible(agentId, userId);
        if (entity == null) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "挂载的 Agent 不存在");
        }
        if (entity.getStatus() == null || entity.getStatus() != 1) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "挂载的 Agent 已停用，无法发起运行");
        }
        return entity;
    }

    private AgentConfigEntity requireOwned(Long userId, Long agentId) {
        AgentConfigEntity entity = repository.findByIdVisible(agentId, userId);
        if (entity == null) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "Agent 不存在");
        }
        if (entity.getUserId() == null) {
            throw new BusinessException(ResultCodes.FORBIDDEN, "平台公共 Agent 不可编辑");
        }
        return entity;
    }

    private void validate(String name, String instruction) {
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "名称不能为空且不超过 100 字符");
        }
        if (instruction == null || instruction.isBlank() || instruction.length() > 4000) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "系统提示词不能为空且不超过 4000 字符");
        }
    }

    /**
     * 策略规整：null/空 = auto（不落库，查询侧兜底）；非法值报 VALIDATION_ERROR。
     *
     * @param strategy 入参：请求里的策略
     * @return 出参：落库策略（null = auto）
     */
    private String normalizeStrategy(String strategy) {
        if (strategy == null || strategy.isBlank()) {
            return null;
        }
        String value = strategy.trim().toLowerCase();
        if (!STRATEGIES.contains(value)) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR,
                    "未知策略：" + strategy + "，可选 auto / plan / custom / fixed");
        }
        return value;
    }

    /**
     * 短期记忆轮数归一化：负数报错、越界收敛到边界（0..50）、null 保持默认。
     *
     * @param historyTurns 入参：请求里的轮数
     * @return 出参：落库轮数（null = 默认 10 轮）
     */
    private Integer normalizeHistoryTurns(Integer historyTurns) {
        if (historyTurns == null) {
            return null;
        }
        if (historyTurns < 0) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "短期记忆轮数不能为负数");
        }
        return Math.min(historyTurns, MAX_HISTORY_TURNS);
    }

    /**
     * 校验工具白名单并编码：未知工具名报错；去重保序。
     *
     * <p><b>不把「显式全选」归一成 NULL</b>：两者语义不同——NULL = 跟随注册表（将来新增工具自动可用），
     * 显式列表 = 精确授权（新增工具不自动放开）。归一化会悄悄扩大用户授权范围。</p>
     *
     * @param tools 入参：请求里的工具名（null = 全部）
     * @return 出参：落库字符串
     */
    private String normalizeTools(List<String> tools) {
        if (tools == null) {
            return null;
        }
        List<String> known = toolRegistry.names();
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String raw : tools) {
            String name = raw == null ? "" : raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            if (!known.contains(name)) {
                throw new BusinessException(ResultCodes.VALIDATION_ERROR, "未知工具：" + name);
            }
            unique.add(name);
        }
        return AgentToolCodec.encode(new ArrayList<>(unique));
    }

    /**
     * 绑定连接校验：必须属于本人且已启用。
     *
     * @param userId            入参：归属用户
     * @param modelConnectionId 入参：连接 ID（可空）
     * @return 出参：校验通过的连接 ID
     */
    private Long requireModelConnection(Long userId, Long modelConnectionId) {
        if (modelConnectionId == null) {
            return null;
        }
        ModelConnectionEntity conn = modelConnectionRepository.findByIdAndUser(modelConnectionId, userId);
        if (conn == null) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "绑定的模型连接不存在");
        }
        if (conn.getStatus() == null || conn.getStatus() != 1) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "绑定的模型连接已停用，请先启用");
        }
        return modelConnectionId;
    }

    /**
     * 挂载工作流校验：必须属于本人且已启用；0 = 取消挂载（回落 Agent 自己的执行图）。
     *
     * @param userId     入参：归属用户
     * @param workflowId 入参：工作流 ID（可空 / 0 = 取消挂载）
     * @return 出参：校验通过的工作流 ID；取消挂载则为 null
     */
    private Long requireWorkflow(Long userId, Long workflowId) {
        if (workflowId == null || workflowId == CLEAR_MODEL_CONNECTION) {
            return null;
        }
        WorkflowEntity wf = workflowRepository.findById(workflowId);
        if (wf == null) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "挂载的工作流不存在");
        }
        if (!wf.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodes.FORBIDDEN, "只能挂载自己的工作流");
        }
        if (wf.getStatus() == null || wf.getStatus() != 1) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "该工作流已停用，请先启用");
        }
        return workflowId;
    }

    /**
     * 工作流版本解析：null / 0 = 跟随工作流当前生效版本；非 0 校验版本存在。
     *
     * @param workflowId 入参：工作流 ID
     * @param version    入参：版本号
     * @return 出参：生效版本号；跟随最新则为 null
     */
    private Integer resolveWorkflowVersion(Long workflowId, Integer version) {
        if (version == null || version == 0 || workflowId == null) {
            return null;
        }
        WorkflowVersionEntity v = workflowRepository.findVersion(workflowId, version);
        if (v == null) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "工作流版本不存在：" + version);
        }
        return version;
    }

    private AgentConfigData toData(AgentConfigEntity entity) {
        AgentConfigData data = new AgentConfigData();
        data.setId(entity.getId());
        data.setUserId(entity.getUserId());
        data.setName(entity.getName());
        data.setDescription(entity.getDescription());
        // 平台公共不下发 instruction 明文（避免用户照抄平台提示词后自建同名 Agent 绕过版本管理）
        data.setInstruction(entity.getUserId() == null ? null : entity.getInstruction());
        data.setTools(AgentToolCodec.decode(entity.getToolsJson()));
        // NULL = auto：对外统一回显具体策略，前端不用判空
        data.setStrategy(entity.getStrategy() == null ? "auto" : entity.getStrategy());
        data.setHistoryTurns(entity.getHistoryTurns());
        data.setModelConnectionId(entity.getModelConnectionId());
        boolean enabled = entity.getStatus() != null && entity.getStatus() == 1;
        data.setEnabled(enabled);
        data.setPlatformPublic(entity.getUserId() == null);
        data.setUsable(enabled);
        data.setVersion(entity.getVersion());
        com.lemon.lemonagent.dal.entity.AgentGraphEntity graph = agentGraphRepository.findActive(entity.getId());
        data.setGraphVersion(graph == null ? 0 : graph.getVersion());
        data.setWorkflowId(entity.getWorkflowId());
        data.setWorkflowVersion(entity.getWorkflowVersion());
        if (entity.getWorkflowId() != null) {
            WorkflowEntity wf = workflowRepository.findById(entity.getWorkflowId());
            data.setWorkflowName(wf == null ? null : wf.getName());
        }
        data.setCreatedAt(format(entity.getCreatedAt()));
        data.setUpdatedAt(format(entity.getUpdatedAt()));
        return data;
    }

    private static String defaultIfBlank(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s.trim();
    }

    private static String format(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC).format(ISO);
    }

}
