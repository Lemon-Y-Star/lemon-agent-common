package com.lemon.lemonagent.web;

import com.lemon.lemonagent.model.runtime.CreateEvalBatchReq;
import com.lemon.lemonagent.model.runtime.CreateEvalBatchResp;
import com.lemon.lemonagent.model.runtime.EvalBatchData;
import com.lemon.lemonagent.model.runtime.EvalBatchesData;
import com.lemon.lemonagent.servicecore.runtime.EvalService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * 图评测回归接口（v3.5）：发起批次 / 查批次 / 列批次。
 *
 * <p>输出不落在评测表里，而是实时读运行——保证「评测看到的就是运行时的事实」。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/agents")
public class EvalController {

    /** 框架操作入口。 */
    @Resource
    private OptUtil optUtil;

    /** 评测服务。 */
    @Resource
    private EvalService evalService;

    /**
     * 发起一批评测（按当前执行图版本逐条跑）。
     *
     * @param agentId 路径参数：Agent
     * @param request 用例
     * @param userId  当前用户
     * @param source  调用来源
     * @return 批次
     */
    @PostMapping("/{agentId}/eval/batches")
    public CreateEvalBatchResp create(@PathVariable Long agentId,
                                      @RequestBody CreateEvalBatchReq request,
                                      @RequestAttribute("lemon.userId") Long userId,
                                      @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        request.setAgentId(agentId);
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new CreateEvalBatchResp(), req -> {
            AssertUtils.notNull(req.getAgentId(), "Agent ID 不能为空");
            AssertUtils.isTrue(req.getInputs() != null && !req.getInputs().isEmpty(), "用例不能为空");
        });
    }

    /**
     * 批次列表（最近 10 批，含明细）。
     *
     * @param agentId 路径参数：Agent
     * @param userId  当前用户
     * @return 批次列表
     */
    @GetMapping("/{agentId}/eval/batches")
    public BaseResult<EvalBatchesData> list(@PathVariable Long agentId,
                                            @RequestAttribute("lemon.userId") Long userId) {
        return BaseResult.success(evalService.list(userId, agentId));
    }

    /**
     * 批次详情。
     *
     * @param agentId 路径参数：Agent
     * @param batchId 路径参数：批次
     * @param userId  当前用户
     * @return 批次
     */
    @GetMapping("/{agentId}/eval/batches/{batchId}")
    public BaseResult<EvalBatchData> get(@PathVariable Long agentId, @PathVariable Long batchId,
                                         @RequestAttribute("lemon.userId") Long userId) {
        return BaseResult.success(evalService.get(userId, agentId, batchId));
    }

}
