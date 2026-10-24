package com.lemon.lemonagent.web;

import com.lemon.lemonagent.model.runtime.UsageOverviewData;
import com.lemon.lemonagent.servicecore.runtime.UsageService;
import com.lemon.architecture.model.BaseResult;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用量与观测接口（PRD FR-16/FR-17 简化版）：个人维度的 token 用量与运行统计。
 */
@RestController
@RequestMapping("/api/v1/workspace/usage")
public class UsageController {

    /** 用量领域服务。 */
    @Resource
    private UsageService usageService;

    /**
     * 近 N 天用量总览（默认 7 天，最大 30）。
     *
     * @param userId 归属用户
     * @param days   统计窗口天数
     * @return 总览数据
     */
    @GetMapping
    public BaseResult<UsageOverviewData> overview(@RequestAttribute("lemon.userId") Long userId,
                                                  @RequestParam(defaultValue = "7") int days) {
        return BaseResult.success(usageService.overview(userId, Math.min(Math.max(days, 1), 30)));
    }

}
