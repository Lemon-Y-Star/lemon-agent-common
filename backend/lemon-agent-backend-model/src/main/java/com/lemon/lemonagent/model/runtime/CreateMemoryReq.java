package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/** 新增长期记忆。 */
@Getter
@Setter
public class CreateMemoryReq extends BaseReq {

    private Long userId;

    private String content;

    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.MEMORY_CREATE);
    }
}
