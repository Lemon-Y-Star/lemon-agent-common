package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.List;

/** 评测批次列表。 */
@Data
public class EvalBatchesData {

    /** 批次（倒序，最多 10 条） */
    private List<EvalBatchData> batches;

}
