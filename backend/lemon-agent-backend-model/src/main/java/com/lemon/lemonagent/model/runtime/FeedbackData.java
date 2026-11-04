package com.lemon.lemonagent.model.runtime;

import lombok.Data;

/** 单条运行反馈。 */
@Data
public class FeedbackData {

    /** UP / DOWN */
    private String rating;

    /** 文字反馈 */
    private String comment;

    /** 更新时间 */
    private String updatedAt;
    /** 自动沉淀的知识库文档 ID（v4.5.10：差评 + 标准答案时返回；无 = 未沉淀） */
    private Long createdDocId;

}
