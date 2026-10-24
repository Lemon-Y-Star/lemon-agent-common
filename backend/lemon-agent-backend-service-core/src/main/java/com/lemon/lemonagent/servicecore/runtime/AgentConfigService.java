package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.dal.entity.AgentConfigEntity;
import com.lemon.lemonagent.dal.entity.ModelConnectionEntity;
import com.lemon.lemonagent.dal.repository.AgentConfigRepository;
import com.lemon.lemonagent.dal.repository.ModelConnectionRepository;
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

    private final AgentConfigRepository repository;

    private final ModelConnectionRepository modelConnectionRepository;

    private final ToolRegistry toolRegistry;

    private final tools.jackson.databind.ObjectMapper objectMapper = new tools.jackson.databind.ObjectMapper();

    public AgentConfigService(AgentConfigRepository repository,
                              ModelConnectionRepository modelConnectionRepository,
                              ToolRegistry toolRegistry) {
        this.repository = repository;
        this.modelConnectionRepository = modelConnectionRepository;
        this.toolRegistry = toolRegistry;
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
     * @return 出参：创建后的 Agent
     */
    public AgentConfigData create(Long userId, String name, String description,
                                  String instruction, List<String> tools, Long modelConnectionId) {
        validate(name, instruction);
        AgentConfigEntity entity = new AgentConfigEntity();
        entity.setUserId(userId);
        entity.setName(name.trim());
        entity.setDescription(defaultIfBlank(description, ""));
        entity.setInstruction(instruction.trim());
        entity.setToolsJson(normalizeTools(tools));
        entity.setModelConnectionId(requireModelConnection(userId, modelConnectionId));
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
                                  String instruction, List<String> tools, Long modelConnectionId,
                                  Boolean enabled) {
        validate(name, instruction);
        AgentConfigEntity entity = requireOwned(userId, agentId);
        entity.setName(name.trim());
        entity.setDescription(defaultIfBlank(description, ""));
        entity.setInstruction(instruction.trim());
        entity.setToolsJson(normalizeTools(tools));
        if (modelConnectionId != null) {
            entity.setModelConnectionId(modelConnectionId == CLEAR_MODEL_CONNECTION
                    ? null : requireModelConnection(userId, modelConnectionId));
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

    private AgentConfigData toData(AgentConfigEntity entity) {
        AgentConfigData data = new AgentConfigData();
        data.setId(entity.getId());
        data.setUserId(entity.getUserId());
        data.setName(entity.getName());
        data.setDescription(entity.getDescription());
        // 平台公共不下发 instruction 明文（避免用户照抄平台提示词后自建同名 Agent 绕过版本管理）
        data.setInstruction(entity.getUserId() == null ? null : entity.getInstruction());
        data.setTools(AgentToolCodec.decode(entity.getToolsJson()));
        data.setModelConnectionId(entity.getModelConnectionId());
        boolean enabled = entity.getStatus() != null && entity.getStatus() == 1;
        data.setEnabled(enabled);
        data.setPlatformPublic(entity.getUserId() == null);
        data.setUsable(enabled);
        data.setVersion(entity.getVersion());
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
