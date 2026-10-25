package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.dal.entity.AgentRunEntity;
import com.lemon.lemonagent.dal.entity.RunFeedbackEntity;
import com.lemon.lemonagent.dal.repository.AgentRunRepository;
import com.lemon.lemonagent.dal.repository.RunFeedbackRepository;
import com.lemon.lemonagent.model.runtime.ArtifactData;
import com.lemon.lemonagent.model.runtime.ArtifactsData;
import com.lemon.lemonagent.model.runtime.FeedbackData;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 产物与反馈（v3.8，PRD FR-17 最小版）。
 *
 * <p>产物 = 有最终产出的运行（COMPLETED 且 output 非空），列表实时读 {@code agent_run}——
 * 产物不另存一份事实；反馈一人一运行一条（upsert），挂在 run 上而不是复制输出。</p>
 */
@Service
public class ArtifactService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private final AgentRunRepository runRepository;

    private final RunFeedbackRepository feedbackRepository;

    public ArtifactService(AgentRunRepository runRepository, RunFeedbackRepository feedbackRepository) {
        this.runRepository = runRepository;
        this.feedbackRepository = feedbackRepository;
    }

    /**
     * 产物列表（我的已完成运行，按时间倒序）。
     *
     * @param userId 入参：用户
     * @param page   入参：页码
     * @param size   入参：页大小
     * @return 出参：列表
     */
    public ArtifactsData list(Long userId, int page, int size) {
        ArtifactsData data = new ArtifactsData();
        data.setTotal(runRepository.countByUser(userId, "COMPLETED", null, null));
        List<ArtifactData> items = new ArrayList<>();
        for (AgentRunEntity run : runRepository.findByUser(userId, "COMPLETED", null, null,
                (page - 1) * size, size)) {
            ArtifactData item = new ArtifactData();
            item.setRunId(run.getId());
            item.setSessionId(run.getSessionId());
            item.setAgentId(run.getAgentId());
            item.setInput(null);
            item.setOutput(run.getOutputText());
            item.setStatus(run.getStatus());
            item.setInputTokens(run.getInputTokens());
            item.setOutputTokens(run.getOutputTokens());
            item.setEndedAt(run.getEndedAt() == null ? null : run.getEndedAt().atOffset(ZoneOffset.UTC).format(ISO));
            RunFeedbackEntity fb = feedbackRepository.findByRunAndUser(run.getId(), userId);
            if (fb != null) {
                item.setMyRating(fb.getRating());
                item.setMyComment(fb.getComment());
            }
            items.add(item);
        }
        data.setItems(items);
        return data;
    }

    /**
     * 提交反馈（upsert）。
     *
     * @param userId  入参：用户
     * @param runId   入参：运行 ID
     * @param rating  入参：UP / DOWN
     * @param comment 入参：评论（可空）
     * @return 出参：落库后的反馈
     */
    public FeedbackData submit(Long userId, Long runId, String rating, String comment) {
        String value = rating == null ? "" : rating.trim().toUpperCase();
        if (!"UP".equals(value) && !"DOWN".equals(value)) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "rating 只能是 UP 或 DOWN");
        }
        AgentRunEntity run = runRepository.findByIdAndUser(runId, userId);
        if (run == null) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "运行不存在");
        }
        RunFeedbackEntity entity = new RunFeedbackEntity();
        entity.setRunId(runId);
        entity.setUserId(userId);
        entity.setRating(value);
        entity.setComment(comment == null ? null : comment.trim());
        feedbackRepository.upsert(entity);

        RunFeedbackEntity saved = feedbackRepository.findByRunAndUser(runId, userId);
        FeedbackData data = new FeedbackData();
        data.setRating(saved == null ? value : saved.getRating());
        data.setComment(saved == null ? entity.getComment() : saved.getComment());
        data.setUpdatedAt(saved == null ? null
                : saved.getUpdatedAt().atOffset(ZoneOffset.UTC).format(ISO));
        return data;
    }

}
