package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/** 提交运行反馈（upsert：一人一运行一条）。 */
@Getter
@Setter
public class SubmitFeedbackReq extends BaseReq {

    /** 归属用户（服务端回填） */
    private Long userId;

    /** 运行 ID（路径参数回填） */
    private Long runId;

    /** UP / DOWN */
    private String rating;

    /** 文字反馈（可空） */
    private String comment;
    /** 标准答案（v4.5.10 可空；差评时填写 → 自动沉淀知识库文档） */
    private String expectedAnswer;

    /** 填充路由三元组。 */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.RUN_FEEDBACK);
    }

}
