package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.dal.entity.AgentConfigEntity;
import com.lemon.lemonagent.dal.repository.AgentConfigRepository;
import com.lemon.lemonagent.model.runtime.AgentConfigData;
import com.lemon.lemonagent.model.runtime.AgentConfigsData;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 配置领域服务（PRD FR-6 最小充分版）：
 * 平台公共只读、个人自建增删改；运行挂载跟随当前已启用版本（PRD S2 工具语义）。
 */
@Service
public class AgentConfigService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private final AgentConfigRepository repository;

    public AgentConfigService(AgentConfigRepository repository) {
        this.repository = repository;
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
     * @param modelConnectionId 入参：绑定连接（可空）
     * @return 出参：创建后的 Agent
     */
    public AgentConfigData create(Long userId, String name, String description,
                                  String instruction, Long modelConnectionId) {
        validate(name, instruction);
        AgentConfigEntity entity = new AgentConfigEntity();
        entity.setUserId(userId);
        entity.setName(name.trim());
        entity.setDescription(defaultIfBlank(description, ""));
        entity.setInstruction(instruction.trim());
        entity.setModelConnectionId(modelConnectionId);
        entity.setStatus(1);
        entity.setVersion(1);
        repository.insert(entity);
        return toData(entity);
    }

    /**
     * 更新个人 Agent。
     *
     * @return 出参：更新后的 Agent
     */
    public AgentConfigData update(Long userId, Long agentId, String name, String description,
                                  String instruction, Long modelConnectionId, Boolean enabled) {
        validate(name, instruction);
        AgentConfigEntity entity = requireOwned(userId, agentId);
        entity.setName(name.trim());
        entity.setDescription(defaultIfBlank(description, ""));
        entity.setInstruction(instruction.trim());
        if (modelConnectionId != null) {
            entity.setModelConnectionId(modelConnectionId);
        }
        if (enabled != null) {
            entity.setStatus(enabled ? 1 : 0);
        }
        int rows = repository.update(entity);
        if (rows == 0) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "Agent 不存在");
        }
        // version 由 SQL 自增、updated_at 由库默认值刷新，必须回读才是真实结果（否则响应体版本号不动）
        AgentConfigEntity updated = repository.findById(agentId);
        return toData(updated == null ? entity : updated);
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

    private AgentConfigData toData(AgentConfigEntity entity) {
        AgentConfigData data = new AgentConfigData();
        data.setId(entity.getId());
        data.setUserId(entity.getUserId());
        data.setName(entity.getName());
        data.setDescription(entity.getDescription());
        // 平台公共不下发 instruction 明文（避免用户照抄平台提示词后自建同名 Agent 绕过版本管理）
        data.setInstruction(entity.getUserId() == null ? null : entity.getInstruction());
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
