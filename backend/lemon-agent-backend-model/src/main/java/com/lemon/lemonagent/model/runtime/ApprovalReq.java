package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/** 提交审批决定请求（契约 4.8；工具审批与节点审批共用本接口）。 */
@Getter
@Setter
public class ApprovalReq extends BaseReq {

    /** 归属用户（服务端由认证拦截器回填） */
    private Long userId;

    /** 运行 ID */
    private Long runId;

    /** 审批 ID（必须与 pending_approval_json.approvalId 一致） */
    private String approvalId;

    /** APPROVE / REJECT */
    private String decision;

    /** 审批备注（≤200） */
    private String comment;

    /** 填充路由三元组。 */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.APPROVAL_SUBMIT);
    }

}
