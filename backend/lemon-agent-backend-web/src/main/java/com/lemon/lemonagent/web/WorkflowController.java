package com.lemon.lemonagent.web;

import com.lemon.lemonagent.model.runtime.CreateWorkflowReq;
import com.lemon.lemonagent.model.runtime.CreateWorkflowResp;
import com.lemon.lemonagent.model.runtime.DeleteWorkflowReq;
import com.lemon.lemonagent.model.runtime.DeleteWorkflowResp;
import com.lemon.lemonagent.model.runtime.SaveWorkflowVersionReq;
import com.lemon.lemonagent.model.runtime.SaveWorkflowVersionResp;
import com.lemon.lemonagent.model.runtime.WorkflowDetailData;
import com.lemon.lemonagent.model.runtime.WorkflowsData;
import com.lemon.lemonagent.servicecore.runtime.WorkflowService;
import com.lemon.architecture.model.BaseResult;
import com.lemon.architecture.service.util.opt.OptUtil;
import com.lemon.architecture.util.validation.AssertUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作流接口（v3.7）：列表 / 创建（可从 Agent 执行图另存）/ 详情 / 保存新版本。
 *
 * <p>挂载/取消挂载走 {@link AgentConfigController} 的 Agent 更新接口（workflowId / workflowVersion）。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/workflows")
public class WorkflowController {

    /** 框架操作入口。 */
    @Resource
    private OptUtil optUtil;

    /** 工作流领域服务。 */
    @Resource
    private WorkflowService workflowService;

    /**
     * 我的工作流列表。
     *
     * @param userId 当前用户
     * @return 列表
     */
    @GetMapping
    public BaseResult<WorkflowsData> list(@RequestAttribute("lemon.userId") Long userId) {
        return BaseResult.success(workflowService.list(userId));
    }

    /**
     * 创建工作流（sourceAgentId 非空 = 从该 Agent 的当前执行图另存为 v1）。
     *
     * @param request 请求
     * @param userId  当前用户
     * @param source  调用来源
     * @return 详情
     */
    @PostMapping
    public CreateWorkflowResp create(@RequestBody CreateWorkflowReq request,
                                     @RequestAttribute("lemon.userId") Long userId,
                                     @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new CreateWorkflowResp(), req -> {
            AssertUtils.notBlank(req.getName(), "工作流名称不能为空");
        });
    }

    /**
     * 工作流详情（含版本列表）。
     *
     * @param workflowId 路径参数：工作流 ID
     * @param userId     当前用户
     * @return 详情
     */
    @GetMapping("/{workflowId}")
    public BaseResult<WorkflowDetailData> detail(@PathVariable Long workflowId,
                                                 @RequestAttribute("lemon.userId") Long userId) {
        return BaseResult.success(workflowService.detail(userId, workflowId));
    }

    /**
     * 保存新版本（从来源 Agent 的当前执行图另存）。
     *
     * @param workflowId 路径参数：工作流 ID
     * @param request    请求
     * @param userId     当前用户
     * @param source     调用来源
     * @return 详情
     */
    @PutMapping("/{workflowId}/versions")
    public SaveWorkflowVersionResp saveVersion(@PathVariable Long workflowId,
                                               @RequestBody SaveWorkflowVersionReq request,
                                               @RequestAttribute("lemon.userId") Long userId,
                                               @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        request.setWorkflowId(workflowId);
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new SaveWorkflowVersionResp(), req -> {
            AssertUtils.notNull(req.getSourceAgentId(), "必须指定来源 Agent");
        });
    }

    /**
     * 删除工作流（被 Agent 挂载时禁止删除，PRD P17）。
     *
     * @param workflowId 路径参数：工作流 ID
     * @param userId     当前用户
     * @param source     调用来源
     * @return 空
     */
    @DeleteMapping("/{workflowId}")
    public DeleteWorkflowResp delete(@PathVariable Long workflowId,
                                     @RequestAttribute("lemon.userId") Long userId,
                                     @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        DeleteWorkflowReq request = new DeleteWorkflowReq();
        request.setWorkflowId(workflowId);
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new DeleteWorkflowResp(), req ->
                AssertUtils.notNull(req.getWorkflowId(), "工作流 ID 不能为空"));
    }

}
