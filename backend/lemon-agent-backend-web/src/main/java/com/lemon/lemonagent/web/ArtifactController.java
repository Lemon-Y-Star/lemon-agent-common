package com.lemon.lemonagent.web;

import com.lemon.lemonagent.model.runtime.ArtifactsData;
import com.lemon.lemonagent.model.runtime.FeedbackData;
import com.lemon.lemonagent.model.runtime.SubmitFeedbackReq;
import com.lemon.lemonagent.model.runtime.SubmitFeedbackResp;
import com.lemon.lemonagent.servicecore.runtime.ArtifactService;
import com.lemon.architecture.model.BaseResult;
import com.lemon.architecture.service.util.opt.OptUtil;
import com.lemon.architecture.util.validation.AssertUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 产物与反馈（v3.8）：我的有产出运行 + 运行级反馈（点赞点踩 + 评论，upsert）。
 */
@RestController
@RequestMapping("/api/v1/workspace/artifacts")
public class ArtifactController {

    /** 框架操作入口。 */
    @Resource
    private OptUtil optUtil;

    /** 产物领域服务。 */
    @Resource
    private ArtifactService artifactService;

    /**
     * 产物列表。
     *
     * @param userId 当前用户
     * @param page   页码
     * @param size   页大小
     * @return 列表
     */
    @GetMapping
    public BaseResult<ArtifactsData> list(@RequestAttribute("lemon.userId") Long userId,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size) {
        return BaseResult.success(artifactService.list(userId, page, Math.min(size, 50)));
    }

    /**
     * 提交反馈（upsert）。
     *
     * @param runId  路径参数：运行 ID
     * @param request 反馈
     * @param userId 当前用户
     * @param source 调用来源
     * @return 反馈
     */
    @PostMapping("/{runId}/feedback")
    public SubmitFeedbackResp feedback(@PathVariable Long runId,
                                       @RequestBody SubmitFeedbackReq request,
                                       @RequestAttribute("lemon.userId") Long userId,
                                       @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        request.setRunId(runId);
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new SubmitFeedbackResp(), req -> {
            AssertUtils.notNull(req.getRunId(), "运行 ID 不能为空");
            AssertUtils.notBlank(req.getRating(), "rating 不能为空");
        });
    }

}
