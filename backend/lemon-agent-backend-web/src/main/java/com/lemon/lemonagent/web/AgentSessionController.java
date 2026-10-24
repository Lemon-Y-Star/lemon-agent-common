package com.lemon.lemonagent.web;

import com.lemon.lemonagent.model.runtime.CreateSessionReq;
import com.lemon.lemonagent.model.runtime.CreateSessionResp;
import com.lemon.lemonagent.model.runtime.DeleteSessionReq;
import com.lemon.lemonagent.model.runtime.DeleteSessionResp;
import com.lemon.lemonagent.model.runtime.RenameSessionReq;
import com.lemon.lemonagent.model.runtime.RenameSessionResp;
import com.lemon.lemonagent.model.runtime.SessionDetailData;
import com.lemon.lemonagent.model.runtime.SessionListData;
import com.lemon.lemonagent.servicecore.runtime.AgentRuntimeService;
import com.lemon.architecture.model.BaseResult;
import com.lemon.architecture.service.util.opt.OptUtil;
import com.lemon.architecture.util.validation.AssertUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会话接口（契约 4.2 / 4.3 / 4.4）。创建走框架 Handler 链，查询直调领域服务。
 */
@RestController
@RequestMapping("/api/v1/agent/sessions")
public class AgentSessionController {

    /** 框架操作入口。 */
    @Resource
    private OptUtil optUtil;

    /** 运行时领域服务。 */
    @Resource
    private AgentRuntimeService agentRuntimeService;

    /**
     * 创建会话。
     *
     * @param request 标题（可空）
     * @param userId  归属用户
     * @param source  调用来源
     * @return 会话数据
     */
    @PostMapping
    public CreateSessionResp create(@RequestBody(required = false) CreateSessionReq request,
                                    @RequestAttribute("lemon.userId") Long userId,
                                    @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        if (request == null) {
            request = new CreateSessionReq();
        }
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new CreateSessionResp(), req -> { });
    }

    /**
     * 会话列表（lastActiveAt 倒序）。
     *
     * @param userId 归属用户
     * @param page   页码（1 起）
     * @param size   页大小（默认 20，最大 100）
     * @return 列表数据
     */
    @GetMapping
    public BaseResult<SessionListData> list(@RequestAttribute("lemon.userId") Long userId,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return BaseResult.success(agentRuntimeService.listSessions(userId, page, Math.min(size, 100)));
    }

    /**
     * 重命名会话。
     *
     * @param sessionId 会话 ID
     * @param request   新标题
     * @param userId    归属用户
     * @param source    调用来源
     * @return 会话数据
     */
    @PostMapping("/{sessionId}/rename")
    public RenameSessionResp rename(@PathVariable Long sessionId,
                                    @RequestBody RenameSessionReq request,
                                    @RequestAttribute("lemon.userId") Long userId,
                                    @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        request.setSessionId(sessionId);
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new RenameSessionResp(), req -> {
            AssertUtils.notBlank(req.getTitle(), "标题不能为空");
            AssertUtils.isTrue(req.getTitle().length() <= 100, "标题不能超过 100 字符");
        });
    }

    /**
     * 删除会话（级联删除消息/事件/运行；有活跃运行时 409）。
     *
     * @param sessionId 会话 ID
     * @param userId    归属用户
     * @param source    调用来源
     * @return 空数据成功响应
     */
    @PostMapping("/{sessionId}/delete")
    public DeleteSessionResp delete(@PathVariable Long sessionId,
                                    @RequestAttribute("lemon.userId") Long userId,
                                    @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        DeleteSessionReq request = new DeleteSessionReq();
        request.setSessionId(sessionId);
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new DeleteSessionResp(), req ->
                AssertUtils.notNull(req.getSessionId(), "会话 ID 不能为空"));
    }

    /**
     * 会话消息 + 运行快照（刷新恢复数据源）。
     *
     * @param sessionId 会话 ID
     * @param userId    归属用户
     * @return 消息与运行
     */
    @GetMapping("/{sessionId}/messages")
    public BaseResult<SessionDetailData> messages(@PathVariable Long sessionId,
                                                  @RequestAttribute("lemon.userId") Long userId) {
        return BaseResult.success(agentRuntimeService.sessionDetail(userId, sessionId));
    }

}
