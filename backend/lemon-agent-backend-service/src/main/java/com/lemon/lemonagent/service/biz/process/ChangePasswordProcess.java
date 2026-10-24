package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.auth.ChangePasswordReq;
import com.lemon.lemonagent.model.auth.ChangePasswordResp;
import com.lemon.lemonagent.servicecore.auth.AuthService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/**
 * 修改密码处理器：路由三元组 {@code ANY / ANY / AUTH_PASSWORD_CHANGE}。
 *
 * <p>{@code userId} 由认证拦截器解析出并回填到请求（客户端不可传），见 AuthController。</p>
 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "AUTH_PASSWORD_CHANGE")
public class ChangePasswordProcess extends Handler<ChangePasswordReq, ChangePasswordResp> {

    @Resource
    private AuthService authService;

    /**
     * 执行改密。
     *
     * @param request  已通过校验的请求（userId 由服务端填充）
     * @param response 待填充的响应
     */
    @Override
    protected void doExecute(ChangePasswordReq request, ChangePasswordResp response) {
        authService.changePassword(request.getUserId(), request.getCurrentPassword(), request.getNewPassword());
        log.info("密码修改成功 userId:{}", request.getUserId());
    }

}
