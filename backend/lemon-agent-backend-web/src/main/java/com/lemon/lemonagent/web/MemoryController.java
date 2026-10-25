package com.lemon.lemonagent.web;

import com.lemon.lemonagent.model.runtime.CreateMemoryReq;
import com.lemon.lemonagent.model.runtime.CreateMemoryResp;
import com.lemon.lemonagent.model.runtime.DeleteMemoryReq;
import com.lemon.lemonagent.model.runtime.DeleteMemoryResp;
import com.lemon.lemonagent.model.runtime.MemoriesData;
import com.lemon.lemonagent.model.runtime.ToggleMemoryReq;
import com.lemon.lemonagent.model.runtime.ToggleMemoryResp;
import com.lemon.lemonagent.servicecore.runtime.MemoryService;
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
 * 长期记忆（v3.6）：用户级事实/偏好，运行时自动拼进 system prompt。
 */
@RestController
@RequestMapping("/api/v1/workspace/memories")
public class MemoryController {

    @Resource
    private OptUtil optUtil;

    @Resource
    private MemoryService memoryService;

    /**
     * 记忆列表。
     *
     * @param userId 当前用户
     * @return 列表
     */
    @GetMapping
    public BaseResult<MemoriesData> list(@RequestAttribute("lemon.userId") Long userId) {
        return BaseResult.success(memoryService.list(userId));
    }

    /**
     * 新增记忆。
     *
     * @param request 内容
     * @param userId  当前用户
     * @param source  调用来源
     * @return 新记忆
     */
    @PostMapping
    public CreateMemoryResp create(@RequestBody CreateMemoryReq request,
                                   @RequestAttribute("lemon.userId") Long userId,
                                   @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new CreateMemoryResp(),
                req -> AssertUtils.notBlank(req.getContent(), "记忆内容不能为空"));
    }

    /**
     * 启用/停用。
     *
     * @param memoryId 记忆 ID
     * @param request  开关
     * @param userId   当前用户
     * @param source   调用来源
     * @return 空
     */
    @PutMapping("/{memoryId}")
    public ToggleMemoryResp toggle(@PathVariable Long memoryId, @RequestBody ToggleMemoryReq request,
                                   @RequestAttribute("lemon.userId") Long userId,
                                   @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        request.setMemoryId(memoryId);
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new ToggleMemoryResp(), req -> AssertUtils.notNull(req.getEnabled(), "enabled 不能为空"));
    }

    /**
     * 删除记忆。
     *
     * @param memoryId 记忆 ID
     * @param userId   当前用户
     * @param source   调用来源
     * @return 空
     */
    @DeleteMapping("/{memoryId}")
    public DeleteMemoryResp delete(@PathVariable Long memoryId,
                                   @RequestAttribute("lemon.userId") Long userId,
                                   @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        DeleteMemoryReq request = new DeleteMemoryReq();
        request.setMemoryId(memoryId);
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new DeleteMemoryResp(), req -> AssertUtils.notNull(req.getMemoryId(), "记忆 ID 不能为空"));
    }

}
