package com.lemon.lemonagent.model.auth;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/**
 * 修改密码请求（PRD FR-2）。
 *
 * <p>客户端已按 FR-2.4 的分支顺序做本地拦截（先判不一致、再判长度），服务端
 * {@code PasswordPolicy} 做同口径校验作为最后一道防线。</p>
 */
@Getter
@Setter
public class ChangePasswordReq extends BaseReq {

    /** 用户 ID（服务端由认证拦截器解析回填，客户端不可传） */
    private Long userId;

    /** 当前口令 */
    private String currentPassword;

    /** 新口令（≥10 位，UTF-8 ≤72 字节） */
    private String newPassword;

    /**
     * 填充路由三元组。
     *
     * @param source 调用来源（X-Source 请求头）
     */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.AUTH_PASSWORD_CHANGE);
    }

}
