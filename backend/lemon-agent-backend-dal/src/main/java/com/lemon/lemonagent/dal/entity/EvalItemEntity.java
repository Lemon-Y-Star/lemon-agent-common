package com.lemon.lemonagent.dal.entity;

import lombok.Data;

/** 评测用例项（表 eval_item）：把用例与运行 ID 绑定，输出仍从 agent_run 读。 */
@Data
public class EvalItemEntity {

    /** 主键 */
    private Long id;

    /** 批次 ID */
    private Long batchId;

    /** 用例序号（0 起） */
    private Integer seq;

    /** 对应的运行 ID */
    private Long runId;

    /** 用例输入 */
    private String input;

}
