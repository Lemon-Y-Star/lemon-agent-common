package com.lemon.lemonagent.model.runtime;

import lombok.Data;

/**
 * 可授权工具项（v3.0 Agent 三要素）：工具注册表的对外投影。
 *
 * <p>前端「我的 Agent」表单据此渲染勾选框；{@code needsApproval} 让用户提前知道
 * 勾了这个工具之后运行会挂起等审批。</p>
 */
@Data
public class ToolInfoData {

    /** 工具名（授权清单里存的就是它） */
    private String name;

    /** 工具描述（下发给模型的那段，也是给用户看的说明） */
    private String description;

    /** 是否需要审批（审批名单内） */
    private boolean needsApproval;

    /** 当前是否可用（检索工具依赖 embedding 配置，未配置时不可选） */
    private boolean available;

}
