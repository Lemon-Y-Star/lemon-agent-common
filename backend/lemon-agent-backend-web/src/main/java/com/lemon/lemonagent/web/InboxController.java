package com.lemon.lemonagent.web;

import com.lemon.lemonagent.model.runtime.InboxData;
import com.lemon.lemonagent.servicecore.runtime.AgentRuntimeService;
import com.lemon.architecture.model.BaseResult;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 收件箱接口（PRD FR-21 简化版）：通知由现有数据派生，无新增表。
 */
@RestController
@RequestMapping("/api/v1/workspace/notifications")
public class InboxController {

    /** 运行时领域服务。 */
    @Resource
    private AgentRuntimeService agentRuntimeService;

    /**
     * 收件箱条目（待审批 + 近 7 天失败）。
     *
     * @param userId 归属用户
     * @return 收件箱数据
     */
    @GetMapping
    public BaseResult<InboxData> inbox(@RequestAttribute("lemon.userId") Long userId) {
        return BaseResult.success(agentRuntimeService.inbox(userId));
    }

}
