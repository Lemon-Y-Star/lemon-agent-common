package com.lemon.lemonagent.web;

import com.lemon.lemonagent.model.runtime.ApiKeyData;
import com.lemon.lemonagent.model.runtime.ApiKeysData;
import com.lemon.lemonagent.model.runtime.CreateApiKeyReq;
import com.lemon.lemonagent.model.runtime.CreateApiKeyResp;
import com.lemon.lemonagent.model.runtime.RevokeApiKeyReq;
import com.lemon.lemonagent.model.runtime.RevokeApiKeyResp;
import com.lemon.lemonagent.servicecore.runtime.ApiKeyService;
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
 * API 分发密钥（v3.8）：创建（明文仅返回一次）/ 列表（前缀）/ 吊销。
 */
@RestController
@RequestMapping("/api/v1/workspace/apikeys")
public class ApiKeyController {

    /** 框架操作入口。 */
    @Resource
    private OptUtil optUtil;

    /** 密钥领域服务。 */
    @Resource
    private ApiKeyService apiKeyService;

    /**
     * 密钥列表。
     *
     * @param userId 当前用户
     * @return 列表
     */
    @GetMapping
    public BaseResult<ApiKeysData> list(@RequestAttribute("lemon.userId") Long userId) {
        return BaseResult.success(apiKeyService.list(userId));
    }

    /**
     * 创建密钥。
     *
     * @param request 请求
     * @param userId  当前用户
     * @param source  调用来源
     * @return 含一次性明文
     */
    @PostMapping
    public CreateApiKeyResp create(@RequestBody CreateApiKeyReq request,
                                   @RequestAttribute("lemon.userId") Long userId,
                                   @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new CreateApiKeyResp(), req ->
                AssertUtils.notBlank(req.getName(), "密钥名称不能为空"));
    }

    /**
     * 吊销密钥。
     *
     * @param keyId  路径参数：密钥 ID
     * @param userId 当前用户
     * @param source 调用来源
     * @return 空
     */
    @PostMapping("/{keyId}/revoke")
    public RevokeApiKeyResp revoke(@PathVariable Long keyId,
                                   @RequestAttribute("lemon.userId") Long userId,
                                   @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        RevokeApiKeyReq request = new RevokeApiKeyReq();
        request.setKeyId(keyId);
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new RevokeApiKeyResp(), req ->
                AssertUtils.notNull(req.getKeyId(), "密钥 ID 不能为空"));
    }

}
