package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.List;

/** 评测批次（一批用例 = 按某一版执行图跑的一组运行）。 */
@Data
public class EvalBatchData {

    /** 批次 ID */
    private Long id;

    /** 被测 Agent */
    private Long agentId;

    /** 发起时的图版本 */
    private Integer graphVersion;

    /** 备注 */
    private String note;

    /** 创建时间 */
    private String createdAt;

    /** 用例总数 */
    private int total;

    /** 已完成数（含失败） */
    private int finished;

    /** 用例明细 */
    private List<EvalItemData> items;

}
