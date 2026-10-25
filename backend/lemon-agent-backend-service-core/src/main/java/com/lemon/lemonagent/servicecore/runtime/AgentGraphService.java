package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.dal.entity.AgentConfigEntity;
import com.lemon.lemonagent.dal.entity.AgentGraphEntity;
import com.lemon.lemonagent.dal.repository.AgentConfigRepository;
import com.lemon.lemonagent.dal.repository.AgentGraphRepository;
import com.lemon.lemonagent.model.runtime.AgentGraphData;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/**
 * Agent 执行图领域服务（v3.2.0）：读取当前版本、保存新版本（校验后 version+1）。
 *
 * <p>图是 Agent 的附属物：平台公共 Agent 的图只读，用户自建 Agent 可编辑；
 * 保存即新版本，旧版本保留（status=0），运行按提交时冻结的快照执行。</p>
 */
@Service
public class AgentGraphService {

    private final AgentGraphRepository graphRepository;

    private final AgentConfigRepository agentConfigRepository;

    private final ToolRegistry toolRegistry;

    public AgentGraphService(AgentGraphRepository graphRepository,
                             AgentConfigRepository agentConfigRepository,
                             ToolRegistry toolRegistry) {
        this.graphRepository = graphRepository;
        this.agentConfigRepository = agentConfigRepository;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 读取当前执行图（没有则返回空图 + version=0）。
     *
     * @param userId  入参：当前用户
     * @param agentId 入参：Agent ID
     * @return 出参：图
     */
    public AgentGraphData get(Long userId, Long agentId) {
        AgentConfigEntity agent = requireVisible(userId, agentId);
        AgentGraphEntity entity = graphRepository.findActive(agentId);
        AgentGraphData graph = entity == null ? null : GraphCodec.decodeGraph(entity.getGraphJson());
        if (graph == null) {
            graph = new AgentGraphData();
            graph.setNodes(new ArrayList<>());
            graph.setEdges(new ArrayList<>());
        }
        graph.setVersion(entity == null ? 0 : entity.getVersion());
        graph.setEditable(agent.getUserId() != null);
        return graph;
    }

    /**
     * 保存执行图：校验（类型/拓扑/工具存在）→ 旧版本置历史 → 插入新版本。
     *
     * @param userId  入参：当前用户
     * @param agentId 入参：Agent ID
     * @param graph   入参：图（nodes + edges）
     * @return 出参：带新版本的图
     */
    public AgentGraphData save(Long userId, Long agentId, AgentGraphData graph) {
        AgentConfigEntity agent = requireVisible(userId, agentId);
        if (agent.getUserId() == null) {
            throw new BusinessException(ResultCodes.FORBIDDEN, "平台公共 Agent 的执行图不可编辑");
        }
        GraphValidator.validate(graph, toolRegistry);

        AgentGraphEntity current = graphRepository.findActive(agentId);
        int version = current == null ? 1 : current.getVersion() + 1;
        graphRepository.deactivateAll(agentId);

        AgentGraphEntity entity = new AgentGraphEntity();
        entity.setAgentId(agentId);
        entity.setVersion(version);
        entity.setGraphJson(GraphCodec.encodeGraph(graph));
        entity.setStatus(1);
        graphRepository.insert(entity);

        graph.setVersion(version);
        graph.setEditable(true);
        return graph;
    }

    private AgentConfigEntity requireVisible(Long userId, Long agentId) {
        AgentConfigEntity agent = agentConfigRepository.findByIdVisible(agentId, userId);
        if (agent == null) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "Agent 不存在");
        }
        return agent;
    }

}
