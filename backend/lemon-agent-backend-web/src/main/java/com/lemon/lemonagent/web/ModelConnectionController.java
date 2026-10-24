package com.lemon.lemonagent.web;

import com.lemon.lemonagent.model.modelconn.CreateModelConnectionReq;
import com.lemon.lemonagent.model.modelconn.CreateModelConnectionResp;
import com.lemon.lemonagent.model.modelconn.DeleteModelConnectionReq;
import com.lemon.lemonagent.model.modelconn.DeleteModelConnectionResp;
import com.lemon.lemonagent.model.modelconn.ModelConnectionsData;
import com.lemon.lemonagent.model.modelconn.UpdateModelConnectionReq;
import com.lemon.lemonagent.model.modelconn.UpdateModelConnectionResp;
import com.lemon.lemonagent.servicecore.conn.ModelConnectionService;
import com.lemon.architecture.model.BaseResult;
import com.lemon.architecture.service.util.opt.OptUtil;
import com.lemon.architecture.util.validation.AssertUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型连接接口（PRD FR-13）。
 *
 * <p>查询直调领域服务；命令（建 / 改 / 删）走框架 Handler 路由链——
 * 归属校验在仓储层按 (id, userId) 双键收口，越权表现为 NOT_FOUND。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/model-connections")
public class ModelConnectionController {

    /** 框架操作入口：校验 → 分发 → 日志 → 异常收口。 */
    @Resource
    private OptUtil optUtil;

    /** 模型连接领域服务。 */
    @Resource
    private ModelConnectionService modelConnectionService;

    /**
     * 连接列表（含平台侧配置：主机白名单、辅助用途说明）。
     *
     * @param userId 拦截器解析出的用户 ID
     * @return 列表页数据
     */
    @GetMapping
    public BaseResult<ModelConnectionsData> list(@RequestAttribute("lemon.userId") Long userId) {
        return BaseResult.success(modelConnectionService.list(userId));
    }

    /**
     * 新建连接。
     *
     * @param request 连接配置
     * @param userId  归属用户
     * @param source  调用来源
     * @return 创建后的连接
     */
    @PostMapping
    public CreateModelConnectionResp create(@RequestBody CreateModelConnectionReq request,
                                            @RequestAttribute("lemon.userId") Long userId,
                                            @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new CreateModelConnectionResp(), req -> {
            AssertUtils.notBlank(req.getName(), "连接名称不能为空");
            AssertUtils.notBlank(req.getBaseUrl(), "端点不能为空");
            AssertUtils.notBlank(req.getApiKey(), "API 密钥不能为空");
        });
    }

    /**
     * 更新连接（apiKey 为空表示保留原密钥）。
     *
     * @param id      连接 ID
     * @param request 连接配置
     * @param userId  归属用户
     * @param source  调用来源
     * @return 更新后的连接
     */
    @PutMapping("/{id}")
    public UpdateModelConnectionResp update(@PathVariable Long id,
                                            @RequestBody UpdateModelConnectionReq request,
                                            @RequestAttribute("lemon.userId") Long userId,
                                            @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        request.setId(id);
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new UpdateModelConnectionResp(), req -> {
            AssertUtils.notNull(req.getId(), "连接 ID 不能为空");
            AssertUtils.notBlank(req.getName(), "连接名称不能为空");
            AssertUtils.notBlank(req.getBaseUrl(), "端点不能为空");
        });
    }

    /**
     * 删除连接。
     *
     * @param id      连接 ID
     * @param userId  归属用户
     * @param source  调用来源
     * @return 空数据成功响应
     */
    @DeleteMapping("/{id}")
    public DeleteModelConnectionResp delete(@PathVariable Long id,
                                            @RequestAttribute("lemon.userId") Long userId,
                                            @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        DeleteModelConnectionReq request = new DeleteModelConnectionReq();
        request.setId(id);
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new DeleteModelConnectionResp(), req ->
                AssertUtils.notNull(req.getId(), "连接 ID 不能为空"));
    }

}
