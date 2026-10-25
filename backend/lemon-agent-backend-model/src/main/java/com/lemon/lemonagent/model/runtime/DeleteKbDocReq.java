package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/** 删除知识库文档。 */
@Getter
@Setter
public class DeleteKbDocReq extends BaseReq {

    private Long userId;

    private Long docId;

    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.KB_DOC_DELETE);
    }
}
