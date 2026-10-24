package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/** 更新个人 Agent 请求。 */
@Getter
@Setter
public class UpdateAgentReq extends BaseReq {

    /** 归属用户（服务端回填） */
    private Long userId;

    /** Agent ID */
    private Long agentId;

    /** 名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 系统提示词 */
    private String instruction;

    /** 可用工具白名单（null = 全部工具；空数组 = 不装配工具） */
    private java.util.List<String> tools;

    /** 绑定模型连接（null = 保持不变；0 = 清除绑定，回落用户最新启用连接） */
    private Long modelConnectionId;

    /** 启用状态 */
    private Boolean enabled;

    /** 填充路由三元组。 */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.AGENT_UPDATE);
    }

}
