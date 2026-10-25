package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.dal.entity.AgentConfigEntity;
import com.lemon.lemonagent.dal.entity.AgentGraphEntity;
import com.lemon.lemonagent.dal.entity.AgentRunEntity;
import com.lemon.lemonagent.dal.entity.EvalBatchEntity;
import com.lemon.lemonagent.dal.entity.EvalItemEntity;
import com.lemon.lemonagent.dal.repository.AgentConfigRepository;
import com.lemon.lemonagent.dal.repository.AgentGraphRepository;
import com.lemon.lemonagent.dal.repository.AgentRunRepository;
import com.lemon.lemonagent.dal.repository.EvalBatchRepository;
import com.lemon.lemonagent.dal.repository.EvalItemRepository;
import com.lemon.lemonagent.model.runtime.EvalBatchData;
import com.lemon.lemonagent.model.runtime.EvalBatchesData;
import com.lemon.lemonagent.model.runtime.EvalItemData;
import com.lemon.lemonagent.model.runtime.GraphStateData;
import com.lemon.lemonagent.model.runtime.RunData;
import com.lemon.lemonagent.model.runtime.RunStatus;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 评测回归服务（v3.5）：把「一批用例」按**当前执行图版本**跑成一组运行，
 * 输出/状态实时从 {@code agent_run} 读——评测不复制运行时的事实，只存"用例 → 运行"的绑定。
 *
 * <p>典型用法：改图前跑一批当基线，改完再跑一批，两个批次并排看哪条用例的输出/分支变了。</p>
 */
@Service
public class EvalService {

    /** 单批用例上限：再多就是压测，不是回归。 */
    private static final int MAX_CASES = 10;

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private final EvalBatchRepository batchRepository;

    private final EvalItemRepository itemRepository;

    private final AgentGraphRepository agentGraphRepository;

    private final AgentConfigRepository agentConfigRepository;

    private final WorkflowService workflowService;

    private final AgentRunRepository runRepository;

    private final AgentRuntimeService agentRuntimeService;

    public EvalService(EvalBatchRepository batchRepository,
                       EvalItemRepository itemRepository,
                       AgentGraphRepository agentGraphRepository,
                       AgentConfigRepository agentConfigRepository,
                       WorkflowService workflowService,
                       AgentRunRepository runRepository,
                       AgentRuntimeService agentRuntimeService) {
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.agentGraphRepository = agentGraphRepository;
        this.agentConfigRepository = agentConfigRepository;
        this.workflowService = workflowService;
        this.runRepository = runRepository;
        this.agentRuntimeService = agentRuntimeService;
    }

    /**
     * 发起一批评测：按当前执行图逐条创建运行（各自独立会话，互不抢锁）。
     *
     * @param userId  入参：用户
     * @param agentId 入参：Agent
     * @param inputs  入参：用例输入
     * @param note    入参：备注
     * @return 出参：批次（运行可能仍在跑）
     */
    public EvalBatchData create(Long userId, Long agentId, List<String> inputs, String note) {
        List<String> cases = inputs == null ? List.of() : inputs.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .toList();
        if (cases.isEmpty()) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "用例不能为空");
        }
        if (cases.size() > MAX_CASES) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "单批用例不能超过 " + MAX_CASES + " 条");
        }
        // v3.7：挂载了工作流就按工作流的版本做基线，否则按 Agent 自己的执行图
        AgentConfigEntity agent = agentConfigRepository.findByIdVisible(agentId, userId);
        Integer graphVersion;
        if (agent != null && agent.getWorkflowId() != null) {
            com.lemon.lemonagent.dal.entity.WorkflowVersionEntity wfVersion =
                    workflowService.resolveVersion(agent.getWorkflowId(), agent.getWorkflowVersion());
            if (wfVersion == null) {
                throw new BusinessException(ResultCodes.VALIDATION_ERROR,
                        "该 Agent 挂载的工作流没有可用版本，无法做图评测");
            }
            graphVersion = wfVersion.getVersion();
        } else {
            AgentGraphEntity graph = agentGraphRepository.findActive(agentId);
            if (graph == null) {
                throw new BusinessException(ResultCodes.VALIDATION_ERROR, "该 Agent 还没有执行图，无法做图评测");
            }
            graphVersion = graph.getVersion();
        }

        EvalBatchEntity batch = new EvalBatchEntity();
        batch.setUserId(userId);
        batch.setAgentId(agentId);
        batch.setGraphVersion(graphVersion);
        batch.setNote(note == null ? "" : note.trim());
        batchRepository.insert(batch);

        for (int i = 0; i < cases.size(); i++) {
            RunData run = agentRuntimeService.createRun(userId, null, agentId, cases.get(i), "custom",
                    "eval_" + batch.getId() + "_" + i + "_" + UUID.randomUUID().toString().substring(0, 8), null);
            EvalItemEntity item = new EvalItemEntity();
            item.setBatchId(batch.getId());
            item.setSeq(i);
            item.setRunId(run.getRunId());
            item.setInput(cases.get(i));
            itemRepository.insert(item);
        }
        return get(userId, agentId, batch.getId());
    }

    /**
     * 批次详情（输出实时取自运行）。
     *
     * @param userId  入参：用户
     * @param agentId 入参：Agent
     * @param batchId 入参：批次
     * @return 出参：批次详情
     */
    public EvalBatchData get(Long userId, Long agentId, Long batchId) {
        EvalBatchEntity batch = batchRepository.findByIdAndUser(batchId, userId);
        if (batch == null || !batch.getAgentId().equals(agentId)) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "评测批次不存在");
        }
        return toData(batch, itemRepository.listByBatch(batchId), userId);
    }

    /**
     * 某 Agent 的最近批次（含明细，供前端并排对比）。
     *
     * @param userId  入参：用户
     * @param agentId 入参：Agent
     * @return 出参：批次列表
     */
    public EvalBatchesData list(Long userId, Long agentId) {
        List<EvalBatchData> list = new ArrayList<>();
        for (EvalBatchEntity batch : batchRepository.listByAgent(agentId, userId, 10)) {
            list.add(toData(batch, itemRepository.listByBatch(batch.getId()), userId));
        }
        EvalBatchesData data = new EvalBatchesData();
        data.setBatches(list);
        return data;
    }

    /**
     * 跨 Agent 的批次总览（「我的评测」页数据源）：最近 20 批，附 Agent 名称。
     *
     * @param userId 入参：用户
     * @return 出参：批次列表
     */
    public EvalBatchesData listAll(Long userId) {
        List<EvalBatchData> list = new ArrayList<>();
        for (EvalBatchEntity batch : batchRepository.listByUser(userId, 20)) {
            EvalBatchData data = toData(batch, itemRepository.listByBatch(batch.getId()), userId);
            AgentConfigEntity agent = agentConfigRepository.findById(batch.getAgentId());
            data.setAgentName(agent == null ? "Agent #" + batch.getAgentId() : agent.getName());
            list.add(data);
        }
        EvalBatchesData data = new EvalBatchesData();
        data.setBatches(list);
        return data;
    }

    private EvalBatchData toData(EvalBatchEntity batch, List<EvalItemEntity> items, Long userId) {
        EvalBatchData data = new EvalBatchData();
        data.setId(batch.getId());
        data.setAgentId(batch.getAgentId());
        data.setGraphVersion(batch.getGraphVersion());
        data.setNote(batch.getNote());
        data.setCreatedAt(batch.getCreatedAt() == null ? null
                : batch.getCreatedAt().atOffset(ZoneOffset.UTC).format(ISO));
        data.setTotal(items.size());

        List<EvalItemData> details = new ArrayList<>();
        int finished = 0;
        for (EvalItemEntity item : items) {
            EvalItemData d = new EvalItemData();
            d.setSeq(item.getSeq() == null ? 0 : item.getSeq());
            d.setInput(item.getInput());
            d.setRunId(item.getRunId());
            AgentRunEntity run = runRepository.findByIdAndUser(item.getRunId(), userId);
            if (run != null) {
                d.setStatus(run.getStatus());
                d.setOutput(run.getOutputText());
                d.setInputTokens(run.getInputTokens() == null ? 0 : run.getInputTokens());
                d.setOutputTokens(run.getOutputTokens() == null ? 0 : run.getOutputTokens());
                GraphStateData state = GraphCodec.decodeState(run.getGraphStateJson());
                d.setSkippedNodes(state.getSkipped());
                if (RunStatus.valueOf(run.getStatus()).terminal()) {
                    finished++;
                }
            }
            details.add(d);
        }
        data.setFinished(finished);
        data.setItems(details);
        return data;
    }

}
