package com.lemon.lemonagent.web;

import com.lemon.lemonagent.model.auth.ChangePasswordReq;
import com.lemon.lemonagent.model.auth.ChangePasswordResp;
import com.lemon.lemonagent.model.auth.LoginReq;
import com.lemon.lemonagent.model.auth.LoginResp;
import com.lemon.lemonagent.model.auth.UserData;
import com.lemon.lemonagent.servicecore.auth.AuthService;
import com.lemon.architecture.model.BaseResult;
import com.lemon.architecture.service.util.opt.OptUtil;
import com.lemon.architecture.util.validation.AssertUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（PRD FR-1 / FR-2）。
 *
 * <p>命令型请求（登录 / 改密）走框架标准链路：{@code req.fillRoute(source)} →
 * {@code optUtil.exec} → Handler；登录失败等业务错误由 Handler 链路统一收口为
 * 包络 {@code code}。登录以外的请求由 AuthInterceptor 校验 Bearer 令牌，
 * 无效令牌直接 HTTP 401（传输层未认证语义）。</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    /** 框架操作入口：校验 → 分发 → 日志 → 异常收口。 */
    @Resource
    private OptUtil optUtil;

    /** 认证领域服务（查询类接口直调，不走 Handler 路由）。 */
    @Resource
    private AuthService authService;

    /**
     * 登录。
     *
     * @param request 用户名与口令
     * @param source  调用来源（X-Source 请求头）
     * @return 令牌与身份
     */
    @PostMapping("/login")
    public LoginResp login(@RequestBody LoginReq request,
                           @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        request.fillRoute(source);
        return optUtil.exec(request, new LoginResp(), req -> {
            AssertUtils.notBlank(req.getUsername(), "用户名不能为空");
            AssertUtils.notBlank(req.getPassword(), "密码不能为空");
        });
    }

    /**
     * 登出：吊销当前令牌。
     *
     * @param token 当前令牌（Authorization 头）
     * @return 空数据成功响应
     */
    @PostMapping("/logout")
    public BaseResult<Void> logout(@RequestHeader("Authorization") String token) {
        authService.logout(extractToken(token));
        return BaseResult.success(null);
    }

    /**
     * 当前登录身份。
     *
     * @param userId 拦截器解析出的用户 ID
     * @return 身份信息
     */
    @GetMapping("/me")
    public BaseResult<UserData> me(@RequestAttribute("lemon.userId") Long userId) {
        return BaseResult.success(authService.me(userId));
    }

    /**
     * 修改密码（PRD FR-2.4 校验链的服务端实现见 PasswordPolicy）。
     *
     * @param request 当前口令与新口令
     * @param userId  拦截器解析出的用户 ID
     * @param source  调用来源
     * @return 空数据成功响应
     */
    @PostMapping("/password")
    public ChangePasswordResp changePassword(@RequestBody ChangePasswordReq request,
                                             @RequestAttribute("lemon.userId") Long userId,
                                             @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        // userId 由服务端回填，客户端传什么都不算数
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new ChangePasswordResp(), req -> {
            AssertUtils.notBlank(req.getCurrentPassword(), "当前密码不能为空");
            AssertUtils.notBlank(req.getNewPassword(), "新密码不能为空");
        });
    }

    private static String extractToken(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return authorization;
    }

}
