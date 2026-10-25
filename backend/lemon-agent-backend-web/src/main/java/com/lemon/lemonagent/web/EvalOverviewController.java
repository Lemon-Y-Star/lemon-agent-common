package com.lemon.lemonagent.web;

import com.lemon.lemonagent.model.runtime.EvalBatchesData;
import com.lemon.lemonagent.servicecore.runtime.EvalService;
import com.lemon.architecture.model.BaseResult;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 我的评测（v3.8）：跨 Agent 的评测批次总览——每批在哪个 Agent、哪版图、跑完没有。
 */
@RestController
@RequestMapping("/api/v1/workspace/evals")
public class EvalOverviewController {

    /** 评测领域服务。 */
    @Resource
    private EvalService evalService;

    /**
     * 批次总览（最近 20 批）。
     *
     * @param userId 当前用户
     * @return 列表
     */
    @GetMapping
    public BaseResult<EvalBatchesData> overview(@RequestAttribute("lemon.userId") Long userId) {
        return BaseResult.success(evalService.listAll(userId));
    }

}
