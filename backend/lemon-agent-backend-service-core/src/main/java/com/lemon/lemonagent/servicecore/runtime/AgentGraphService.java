package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.dal.entity.AgentConfigEntity;
import com.lemon.lemonagent.dal.entity.AgentGraphEntity;
import com.lemon.lemonagent.dal.repository.AgentConfigRepository;
import com.lemon.lemonagent.dal.repository.AgentGraphRepository;
import com.lemon.lemonagent.model.runtime.AgentGraphData;
import com.lemon.lemonagent.model.runtime.GraphDiffData;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
     * 版本号列表（v4.1，倒序；diff/回滚的下拉数据）。
     *
     * @param userId  入参：当前用户
     * @param agentId 入参：Agent ID
     * @return 出参：版本号列表
     */
    public List<Integer> versions(Long userId, Long agentId) {
        requireVisible(userId, agentId);
        return graphRepository.findVersions(agentId).stream()
                .map(AgentGraphEntity::getVersion).toList();
    }

    /**
     * 版本回滚（v4.1）：把历史版本复制为新版本（非破坏式，回滚本身也是版本记录）。
     *
     * @param userId  入参：当前用户
     * @param agentId 入参：Agent ID
     * @param version 入参：要回滚到的历史版本
     * @return 出参：回滚后的当前图（带新版本号）
     */
    public AgentGraphData rollback(Long userId, Long agentId, int version) {
        AgentConfigEntity agent = requireVisible(userId, agentId);
        if (agent.getUserId() == null) {
            throw new BusinessException(ResultCodes.FORBIDDEN, "平台公共 Agent 的执行图不可编辑");
        }
        AgentGraphEntity source = graphRepository.findVersion(agentId, version);
        if (source == null) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "版本不存在：" + version);
        }
        AgentGraphEntity current = graphRepository.findActive(agentId);
        if (current != null && current.getVersion() == version) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "该版本已是当前生效版本，无需回滚");
        }
        AgentGraphData graph = GraphCodec.decodeGraph(source.getGraphJson());
        GraphValidator.validate(graph, toolRegistry);
        List<AgentGraphEntity> history = graphRepository.findVersions(agentId);
        int newVersion = history.isEmpty() ? 1 : history.get(0).getVersion() + 1;
        graphRepository.deactivateAll(agentId);
        AgentGraphEntity entity = new AgentGraphEntity();
        entity.setAgentId(agentId);
        entity.setVersion(newVersion);
        entity.setGraphJson(GraphCodec.encodeGraph(graph));
        entity.setStatus(1);
        graphRepository.insert(entity);
        graph.setVersion(newVersion);
        graph.setEditable(true);
        return graph;
    }

    /**
     * 版本 diff（v4.1）：两版图的节点/边增删改对比。
     *
     * @param userId      入参：当前用户
     * @param agentId     入参：Agent ID
     * @param fromVersion 入参：基线版本
     * @param toVersion   入参：对比版本
     * @return 出参：结构化 diff
     */
    public GraphDiffData diff(Long userId, Long agentId, int fromVersion, int toVersion) {
        requireVisible(userId, agentId);
        AgentGraphEntity from = graphRepository.findVersion(agentId, fromVersion);
        AgentGraphEntity to = graphRepository.findVersion(agentId, toVersion);
        if (from == null || to == null) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "版本不存在");
        }
        return GraphDiffer.diff(fromVersion, toVersion, from.getGraphJson(), to.getGraphJson());
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

        // 新版本号取「全部版本最大值 +1」而不是「生效版本 +1」：无生效版本但历史版本存在时
        // （异常中断留下的中间态）从 1 重算会撞 uk_agent_version 唯一键（v4.1 修复）
        List<AgentGraphEntity> history = graphRepository.findVersions(agentId);
        int version = history.isEmpty() ? 1 : history.get(0).getVersion() + 1;
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
