package com.lemon.lemonagent.model.runtime;

import lombok.Getter;
import lombok.Setter;

/**
 * 模型连接健康数据（v4.1 立即检测的逐条结果）。
 */
@Getter
@Setter
public class ModelHealthData {

    private Long connectionId;

    private String name;

    /** ok / fail */
    private String healthStatus;

    private Integer healthLatencyMs;

}
