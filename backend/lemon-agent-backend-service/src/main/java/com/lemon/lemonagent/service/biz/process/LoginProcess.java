package com.lemon.lemonagent.service.biz.process;

import com.lemon.lemonagent.model.auth.LoginData;
import com.lemon.lemonagent.model.auth.LoginReq;
import com.lemon.lemonagent.model.auth.LoginResp;
import com.lemon.lemonagent.servicecore.auth.AuthService;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;
import com.lemon.architecture.service.util.annotation.HandlerRoute;
import com.lemon.architecture.service.util.handler.Handler;
import jakarta.annotation.Resource;

/**
 * 登录处理器：路由三元组 {@code ANY / ANY / AUTH_LOGIN}。
 *
 * <p>前置的参数校验、幂等、api 日志、异常收口都由框架完成，{@code doExecute} 只写业务：
 * 校验口令、签发令牌。</p>
 */
@HandlerRoute(source = SourceType.ANY, business = BusinessType.ANY, operation = "AUTH_LOGIN")
public class LoginProcess extends Handler<LoginReq, LoginResp> {

    @Resource
    private AuthService authService;

    /**
     * 执行登录。
     *
     * @param request  已通过校验的请求
     * @param response 待填充的响应（成功码 / traceId 由框架回填）
     */
    @Override
    protected void doExecute(LoginReq request, LoginResp response) {
        response.setData(authService.login(request.getUsername(), request.getPassword()));
        log.info("登录成功 username:{}", request.getUsername());
    }

}
