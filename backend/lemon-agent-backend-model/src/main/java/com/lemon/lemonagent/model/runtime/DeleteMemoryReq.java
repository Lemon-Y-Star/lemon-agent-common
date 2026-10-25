package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/** 删除长期记忆。 */
@Getter
@Setter
public class DeleteMemoryReq extends BaseReq {

    private Long userId;

    private Long memoryId;

    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.MEMORY_DELETE);
    }
}
