package com.lemon.lemonagent.model.auth;

import com.lemon.architecture.model.BaseResult;

/**
 * 改密响应：成功时无业务数据（前端按 PRD 清空会话并提示重新登录，页面不自动跳转）。
 */
public class ChangePasswordResp extends BaseResult<Void> {
}
