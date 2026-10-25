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

}
