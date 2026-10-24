package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/** 创建会话请求（契约 4.2）：title 留空则由首条消息前 20 字生成。 */
@Getter
@Setter
public class CreateSessionReq extends BaseReq {

    /** 归属用户（服务端由认证拦截器回填） */
    private Long userId;

    /** 会话标题（可空） */
    private String title;

    /** 填充路由三元组。 */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.SESSION_CREATE);
    }

}
