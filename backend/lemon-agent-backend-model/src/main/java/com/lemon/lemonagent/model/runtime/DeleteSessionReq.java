package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/** 删除会话请求：级联删除会话下的消息、运行与事件（用户数据删除语义）。 */
@Getter
@Setter
public class DeleteSessionReq extends BaseReq {

    /** 归属用户（服务端由认证拦截器回填） */
    private Long userId;

    /** 会话 ID */
    private Long sessionId;

    /** 填充路由三元组。 */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.SESSION_DELETE);
    }

}
