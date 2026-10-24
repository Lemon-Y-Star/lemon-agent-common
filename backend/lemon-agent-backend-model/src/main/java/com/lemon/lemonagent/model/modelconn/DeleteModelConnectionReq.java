package com.lemon.lemonagent.model.modelconn;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/**
 * 删除个人模型连接。
 */
@Getter
@Setter
public class DeleteModelConnectionReq extends BaseReq {

    /** 归属用户（服务端由认证拦截器解析回填，客户端不可传） */
    private Long userId;

    /** 连接 ID */
    private Long id;

    /**
     * 填充路由三元组。
     *
     * @param source 调用来源（X-Source 请求头）
     */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.MODEL_CONN_DELETE);
    }

}
