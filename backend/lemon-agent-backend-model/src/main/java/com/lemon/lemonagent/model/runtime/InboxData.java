package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.List;

/**
 * 收件箱数据（PRD FR-21 简化版）：通知由现有数据派生（待审批 + 近 7 天失败），
 * 不新增通知表——「已读」语义由前端 localStorage 的最后查看时间承载。
 */
@Data
public class InboxData {

    /** 通知条目（倒序） */
    private List<Item> items;

    /** 未读数（createdAt > 前端 lastReadAt 的条目数，服务端不存已读态） */
    private int unreadHint;

    /** 通知条目。 */
    @Data
    public static class Item {

        /** 类型：APPROVAL（待审批）/ RUN_FAILED（失败）/ RUN_COMPLETED（完成） */
        private String kind;

        /** 运行 ID */
        private Long runId;

        /** 会话 ID（深链跳转用） */
        private Long sessionId;

        /** 展示文案 */
        private String text;

        /** 时间（ISO-8601） */
        private String createdAt;
    }

}
