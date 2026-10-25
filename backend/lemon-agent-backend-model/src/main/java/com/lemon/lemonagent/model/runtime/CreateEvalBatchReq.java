package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 发起评测批次请求。 */
@Getter
@Setter
public class CreateEvalBatchReq extends BaseReq {

    /** 归属用户（服务端回填） */
    private Long userId;

    /** Agent ID（路径参数回填） */
    private Long agentId;

    /** 用例输入（一行一条，最多 10 条） */
    private List<String> inputs;

    /** 备注（可空） */
    private String note;

    /** 填充路由三元组。 */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.EVAL_CREATE);
    }

}
