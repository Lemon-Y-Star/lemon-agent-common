package com.lemon.lemonagent.model.runtime;

import lombok.Data;

/**
 * 运行文件产物元数据（v4.4.4，不含文件内容——内容经下载端点流式取）。
 */
@Data
public class RunArtifactData {

    /** 产物 ID */
    private Long id;

    /** 文件名 */
    private String name;

    /** MIME 类型 */
    private String contentType;

    /** 字节数 */
    private Integer sizeBytes;

    /** 创建时间（ISO-8601） */
    private String createdAt;

}
