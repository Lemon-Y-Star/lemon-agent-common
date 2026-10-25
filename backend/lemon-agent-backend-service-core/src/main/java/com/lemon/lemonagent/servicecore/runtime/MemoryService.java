package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.dal.entity.AgentMemoryEntity;
import com.lemon.lemonagent.dal.repository.KnowledgeRepository;
import com.lemon.lemonagent.model.runtime.MemoriesData;
import com.lemon.lemonagent.model.runtime.MemoryData;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 长期记忆（v3.6）：用户级事实/偏好，运行时拼进 system prompt。
 *
 * <p>克制点：<b>只注入最近 8 条、总量 1200 字以内</b>——记忆是「补足人格」，不是把知识库塞进提示词；
 * 超限宁可不注入，也不要让 prompt 膨胀到影响指令遵循。停用的记忆不注入但保留（可随时恢复）。</p>
 */
@Service
public class MemoryService {

    /** 运行时最多注入条数。 */
    static final int INJECT_LIMIT = 8;

    /** 注入文本长度上限。 */
    private static final int INJECT_MAX_CHARS = 1200;

    private static final int MAX_CONTENT = 500;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private static final Logger log = LoggerFactory.getLogger(MemoryService.class);

    private final KnowledgeRepository repository;

    public MemoryService(KnowledgeRepository repository) {
        this.repository = repository;
    }

    /**
     * 记忆列表。
     *
     * @param userId 入参：用户
     * @return 出参：列表
     */
    public MemoriesData list(Long userId) {
        List<MemoryData> items = new ArrayList<>();
        for (AgentMemoryEntity e : repository.listMemories(userId)) {
            MemoryData d = new MemoryData();
            d.setId(e.getId());
            d.setContent(e.getContent());
            d.setEnabled(e.getEnabled() != null && e.getEnabled() == 1);
            d.setCreatedAt(e.getCreatedAt() == null ? null : e.getCreatedAt().atOffset(ZoneOffset.UTC).format(ISO));
            items.add(d);
        }
        MemoriesData data = new MemoriesData();
        data.setItems(items);
        data.setInjectLimit(INJECT_LIMIT);
        return data;
    }

    /**
     * 新增记忆。
     *
     * @param userId  入参：用户
     * @param content 入参：内容
     * @return 出参：新记忆
     */
    public MemoryData create(Long userId, String content) {
        if (content == null || content.isBlank() || content.length() > MAX_CONTENT) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "记忆内容不能为空且不超过 500 字");
        }
        AgentMemoryEntity e = new AgentMemoryEntity();
        e.setUserId(userId);
        e.setContent(content.trim());
        repository.insertMemory(e);
        MemoryData d = new MemoryData();
        d.setId(e.getId());
        d.setContent(e.getContent());
        d.setEnabled(true);
        return d;
    }

    /**
     * 启用/停用。
     *
     * @param userId   入参：用户
     * @param memoryId 入参：记忆
     * @param enabled  入参：是否生效
     */
    public void toggle(Long userId, Long memoryId, Boolean enabled) {
        if (enabled == null) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "enabled 不能为空");
        }
        if (repository.toggleMemory(memoryId, userId, enabled) == 0) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "记忆不存在");
        }
    }

    /**
     * 删除。
     *
     * @param userId   入参：用户
     * @param memoryId 入参：记忆
     */
    public void delete(Long userId, Long memoryId) {
        if (repository.deleteMemory(memoryId, userId) == 0) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "记忆不存在");
        }
    }

    /**
     * 把生效记忆拼到 system prompt 末尾（无记忆时原样返回）。
     *
     * @param userId     入参：用户
     * @param basePrompt 入参：原始人格/指令
     * @return 出参：拼装后的提示词
     */
    public String augment(Long userId, String basePrompt) {
        if (userId == null) {
            return basePrompt;
        }
        List<AgentMemoryEntity> memories = repository.listEnabledMemories(userId, INJECT_LIMIT);
        log.debug("记忆注入 userId={} 命中 {} 条", userId, memories.size());
        if (memories.isEmpty()) {
            return basePrompt;
        }
        StringBuilder sb = new StringBuilder(basePrompt == null ? "" : basePrompt);
        sb.append("\n\n【用户长期记忆】以下是用户希望你在所有对话中记住的事实与偏好，回答时遵循：\n");
        int used = 0;
        for (AgentMemoryEntity memory : memories) {
            String line = "- " + memory.getContent() + "\n";
            if (used + line.length() > INJECT_MAX_CHARS) {
                break;
            }
            sb.append(line);
            used += line.length();
        }
        return sb.toString();
    }

}
