package com.lemon.lemonagent.dal.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 运行文件产物（表 {@code run_artifact}，v4.4.4）。
 *
 * <p>沙箱执行产生的文件（图表 png / 数据 csv / 报告 html）落库为运行附属产物，
 * 内容直存 BLOB——单机部署量级下最简且权限校验天然走 SQL。</p>
 */
@Data
public class RunArtifactEntity {

    /** 主键 */
    private Long id;

    /** 归属运行 */
    private Long runId;

    /** 归属用户（下载鉴权用） */
    private Long userId;

    /** 文件名（含扩展名） */
    private String name;

    /** MIME 类型 */
    private String contentType;

    /** 字节数 */
    private Integer sizeBytes;

    /** 文件内容 */
    private byte[] data;

    /** 创建时间 */
    private LocalDateTime createdAt;

}
