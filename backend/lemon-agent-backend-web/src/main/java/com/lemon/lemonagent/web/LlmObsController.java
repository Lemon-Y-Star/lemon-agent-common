package com.lemon.lemonagent.web;

import com.lemon.lemonagent.model.runtime.LlmOverviewData;
import com.lemon.lemonagent.servicecore.runtime.LlmObservabilityService;
import com.lemon.architecture.model.BaseResult;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * LLM 观测（v3.8）：按模型聚合的调用量 / token / 时长 / 失败率 + 最近调用。
 */
@RestController
@RequestMapping("/api/v1/workspace/llm")
public class LlmObsController {

    /** 观测领域服务。 */
    @Resource
    private LlmObservabilityService llmObservabilityService;

    /**
     * 观测总览。
     *
     * @param userId 当前用户
     * @param days   时间窗天数（默认 7，0 = 全部）
     * @return 总览
     */
    @GetMapping("/overview")
    public BaseResult<LlmOverviewData> overview(@RequestAttribute("lemon.userId") Long userId,
                                                @RequestParam(defaultValue = "7") int days) {
        return BaseResult.success(llmObservabilityService.overview(userId, days));
    }

}
