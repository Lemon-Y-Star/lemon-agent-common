package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.dal.entity.AgentConfigEntity;
import com.lemon.lemonagent.dal.entity.AgentGraphEntity;
import com.lemon.lemonagent.dal.entity.WorkflowEntity;
import com.lemon.lemonagent.dal.entity.WorkflowVersionEntity;
import com.lemon.lemonagent.dal.repository.AgentConfigRepository;
import com.lemon.lemonagent.dal.repository.AgentGraphRepository;
import com.lemon.lemonagent.dal.repository.WorkflowRepository;
import com.lemon.lemonagent.model.runtime.AgentGraphData;
import com.lemon.lemonagent.model.runtime.WorkflowData;
import com.lemon.lemonagent.model.runtime.WorkflowDetailData;
import com.lemon.lemonagent.model.runtime.WorkflowVersionData;
import com.lemon.lemonagent.model.runtime.WorkflowsData;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流领域服务（v3.7，PRD FR-25 最小闭环）。
 *
 * <p>工作流是与 Agent 解耦的独立实体：一份图定义（版本化）可被多个 Agent 挂载，
 * Agent 自己的 {@code agent_graph} 仍然保留（不挂载时用它）。
 * 「另存为工作流」= 把某个 Agent 的当前执行图复制成工作流的 v1（或下一版本），
 * 复制之后两边互不影响——这是刻意的：工作流是复用资产，Agent 图是个体的。</p>
 */
@Service
public class WorkflowService {

    private final WorkflowRepository workflowRepository;

    private final AgentGraphRepository agentGraphRepository;

    private final AgentConfigRepository agentConfigRepository;

    private final ToolRegistry toolRegistry;

    public WorkflowService(WorkflowRepository workflowRepository,
                           AgentGraphRepository agentGraphRepository,
                           AgentConfigRepository agentConfigRepository,
                           ToolRegistry toolRegistry) {
        this.workflowRepository = workflowRepository;
        this.agentGraphRepository = agentGraphRepository;
        this.agentConfigRepository = agentConfigRepository;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 当前用户的工作流列表。
     *
     * @param userId 入参：当前用户
     * @return 出参：列表
     */
    public WorkflowsData list(Long userId) {
        List<WorkflowData> items = new ArrayList<>();
        for (WorkflowEntity wf : workflowRepository.findByUser(userId)) {
            items.add(toData(wf, workflowRepository.findVersions(wf.getId())));
        }
        WorkflowsData data = new WorkflowsData();
        data.setWorkflows(items);
        return data;
    }

    /**
     * 工作流详情（含版本列表）。
     *
     * @param userId     入参：当前用户
     * @param workflowId 入参：工作流 ID
     * @return 出参：详情
     */
    public WorkflowDetailData detail(Long userId, Long workflowId) {
        WorkflowEntity wf = requireOwned(userId, workflowId);
        List<WorkflowVersionEntity> versions = workflowRepository.findVersions(workflowId);
        return toDetail(wf, versions);
    }

    /**
     * 创建工作流；{@code sourceAgentId} 非空时把该 Agent 的当前执行图另存为 v1。
     *
     * @param userId        入参：当前用户
     * @param name          入参：名称
     * @param description   入参：描述
     * @param sourceAgentId 入参：来源 Agent（可空 = 建空工作流）
     * @return 出参：详情
     */
    public WorkflowDetailData create(Long userId, String name, String description, Long sourceAgentId) {
        String title = name == null ? null : name.trim();
        if (title == null || title.isEmpty()) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "工作流名称不能为空");
        }
        WorkflowEntity wf = new WorkflowEntity();
        wf.setUserId(userId);
        wf.setName(title);
        wf.setDescription(description);
        wf.setStatus(1);
        workflowRepository.insert(wf);

        if (sourceAgentId != null) {
            AgentGraphData graph = requireAgentGraph(userId, sourceAgentId);
            GraphValidator.validate(graph, toolRegistry);
            insertVersion(wf.getId(), 1, graph, defaultNote(sourceAgentId));
        }
        return toDetail(wf, workflowRepository.findVersions(wf.getId()));
    }

    /**
     * 为工作流保存新版本（从某个 Agent 的当前执行图另存）。
     *
     * @param userId        入参：当前用户
     * @param workflowId    入参：工作流 ID
     * @param sourceAgentId 入参：来源 Agent
     * @param note          入参：版本说明（可空）
     * @return 出参：详情
     */
    public WorkflowDetailData saveVersion(Long userId, Long workflowId, Long sourceAgentId, String note) {
        WorkflowEntity wf = requireOwned(userId, workflowId);
        if (sourceAgentId == null) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "必须指定来源 Agent（当前只支持从执行图另存）");
        }
        AgentGraphData graph = requireAgentGraph(userId, sourceAgentId);
        GraphValidator.validate(graph, toolRegistry);

        WorkflowVersionEntity current = workflowRepository.findActive(workflowId);
        int version = current == null ? 1 : current.getVersion() + 1;
        workflowRepository.deactivateAll(workflowId);
        String text = note == null || note.isBlank() ? defaultNote(sourceAgentId) : note.trim();
        insertVersion(workflowId, version, graph, text);
        return toDetail(wf, workflowRepository.findVersions(workflowId));
    }

    /**
     * 取工作流当前生效版本的图（供运行时冻结快照用）。
     *
     * @param workflowId 入参：工作流 ID
     * @param version    入参：指定版本（null = 当前生效版本）
     * @return 出参：版本实体；没有则 null
     */
    public WorkflowVersionEntity resolveVersion(Long workflowId, Integer version) {
        if (version != null) {
            return workflowRepository.findVersion(workflowId, version);
        }
        return workflowRepository.findActive(workflowId);
    }

    private void insertVersion(Long workflowId, int version, AgentGraphData graph, String note) {
        WorkflowVersionEntity v = new WorkflowVersionEntity();
        v.setWorkflowId(workflowId);
        v.setVersion(version);
        v.setGraphJson(GraphCodec.encodeGraph(graph));
        v.setNote(note);
        v.setStatus(1);
        workflowRepository.insertVersion(v);
    }

    private AgentGraphData requireAgentGraph(Long userId, Long agentId) {
        AgentConfigEntity agent = agentConfigRepository.findByIdVisible(agentId, userId);
        if (agent == null) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "来源 Agent 不存在");
        }
        if (agent.getUserId() == null) {
            throw new BusinessException(ResultCodes.FORBIDDEN, "平台公共 Agent 的执行图不可另存为工作流");
        }
        AgentGraphEntity entity = agentGraphRepository.findActive(agentId);
        if (entity == null) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "该 Agent 还没有执行图，无法另存为工作流");
        }
        AgentGraphData graph = GraphCodec.decodeGraph(entity.getGraphJson());
        if (graph == null || graph.getNodes() == null || graph.getNodes().isEmpty()) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "该 Agent 的执行图是空的，无法另存为工作流");
        }
        return graph;
    }

    private String defaultNote(Long sourceAgentId) {
        AgentConfigEntity agent = agentConfigRepository.findById(sourceAgentId);
        String name = agent == null ? String.valueOf(sourceAgentId) : agent.getName();
        return "来源：Agent「" + name + "」";
    }

    private WorkflowEntity requireOwned(Long userId, Long workflowId) {
        WorkflowEntity wf = workflowRepository.findById(workflowId);
        if (wf == null) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "工作流不存在");
        }
        if (!wf.getUserId().equals(userId)) {
            throw new BusinessException(ResultCodes.FORBIDDEN, "只能操作自己的工作流");
        }
        return wf;
    }

    private WorkflowData toData(WorkflowEntity wf, List<WorkflowVersionEntity> versions) {
        WorkflowData data = new WorkflowData();
        data.setId(wf.getId());
        data.setName(wf.getName());
        data.setDescription(wf.getDescription());
        data.setEnabled(wf.getStatus() != null && wf.getStatus() == 1);
        data.setVersionCount(versions == null ? 0 : versions.size());
        WorkflowVersionEntity active = versions == null ? null
                : versions.stream().filter(v -> v.getStatus() != null && v.getStatus() == 1)
                        .findFirst().orElse(null);
        data.setCurrentVersion(active == null ? 0 : active.getVersion());
        data.setCreatedAt(fmt(wf.getCreatedAt()));
        data.setUpdatedAt(fmt(wf.getUpdatedAt()));
        return data;
    }

    private WorkflowDetailData toDetail(WorkflowEntity wf, List<WorkflowVersionEntity> versions) {
        WorkflowDetailData data = new WorkflowDetailData();
        data.setId(wf.getId());
        data.setName(wf.getName());
        data.setDescription(wf.getDescription());
        data.setEnabled(wf.getStatus() != null && wf.getStatus() == 1);
        List<WorkflowVersionData> list = new ArrayList<>();
        int current = 0;
        if (versions != null) {
            for (WorkflowVersionEntity v : versions) {
                WorkflowVersionData item = new WorkflowVersionData();
                item.setVersion(v.getVersion());
                item.setNote(v.getNote());
                boolean active = v.getStatus() != null && v.getStatus() == 1;
                item.setActive(active);
                if (active) {
                    current = v.getVersion();
                }
                AgentGraphData graph = GraphCodec.decodeGraph(v.getGraphJson());
                item.setNodeCount(graph == null || graph.getNodes() == null ? 0 : graph.getNodes().size());
                item.setCreatedAt(fmt(v.getCreatedAt()));
                list.add(item);
            }
        }
        data.setCurrentVersion(current);
        data.setVersions(list);
        data.setCreatedAt(fmt(wf.getCreatedAt()));
        data.setUpdatedAt(fmt(wf.getUpdatedAt()));
        return data;
    }

    private static String fmt(java.time.Instant instant) {
        return instant == null ? null : instant.toString();
    }

}
