package com.lemon.lemonagent.dal.entity;

import lombok.Data;

import java.time.Instant;

/** Agent 分享配置（v4.6.0）：每 Agent 一条，公开对话页凭 share_code 访问。 */
@Data
public class AgentShareEntity {
    /** 主键 */
    private Long id;
    /** 分享人（=Agent 归属） */
    private Long userId;
    /** Agent */
    private Long agentId;
    /** 公开标识（URL 用） */
    private String shareCode;
    /** 访问口令（空 = 无需口令） */
    private String accessCode;
    /** 1=开启 / 0=关闭 */
    private Integer enabled;
    /** 创建时间 */
    private Instant createdAt;
}
