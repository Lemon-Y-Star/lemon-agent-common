package com.lemon.lemonagent.model.auth;

import com.lemon.architecture.model.BaseResult;

/**
 * 登录响应：继承 {@link BaseResult} 获得 code / message / traceId 标准结构。
 *
 * <p>不要用 {@code BaseResult.success(data)} 静态构造：走 Handler 路由时，
 * 响应对象由 Controller 传入 {@code OptUtil.exec}，成功码与 traceId 由框架统一回填。</p>
 */
public class LoginResp extends BaseResult<LoginData> {
}
