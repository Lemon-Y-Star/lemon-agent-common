package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/** 启用/停用长期记忆。 */
@Getter
@Setter
public class ToggleMemoryReq extends BaseReq {

    private Long userId;

    private Long memoryId;

    private Boolean enabled;

    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.MEMORY_TOGGLE);
    }
}
