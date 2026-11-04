package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.List;

/** 分享页公开信息（v4.6.0）：只出门面字段，instruction/tools 永不出库。 */
@Data
public class ShareInfoData {
    /** Agent 名称 */
    private String name;
    /** 描述 */
    private String description;
    /** 开场白（可空） */
    private String openingStatement;
    /** 建议问题（可空） */
    private List<String> suggestedQuestions;
}
