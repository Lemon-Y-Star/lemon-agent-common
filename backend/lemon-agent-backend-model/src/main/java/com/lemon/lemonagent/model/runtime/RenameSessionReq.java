package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/** 重命名会话请求。 */
@Getter
@Setter
public class RenameSessionReq extends BaseReq {

    /** 归属用户（服务端由认证拦截器回填） */
    private Long userId;

    /** 会话 ID */
    private Long sessionId;

    /** 新标题（1..100 字符） */
    private String title;

    /** 填充路由三元组。 */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.SESSION_RENAME);
    }

}
