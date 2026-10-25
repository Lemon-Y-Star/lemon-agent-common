package com.lemon.lemonagent.model.runtime;

import lombok.Data;

import java.util.List;

/** 产物列表。 */
@Data
public class ArtifactsData {

    /** 产物 */
    private List<ArtifactData> items;

    /** 总数 */
    private long total;

}
