package com.lemon.lemonagent.model.runtime;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/** 创建个人 Agent 请求。 */
@Getter
@Setter
public class CreateAgentReq extends BaseReq {

    /** 归属用户（服务端由认证拦截器回填） */
    private Long userId;

    /** 名称 */
    private String name;

    /** 描述 */
    private String description;

    /** 系统提示词 */
    private String instruction;

    /** 可用工具白名单（null = 全部工具；空数组 = 不装配工具） */
    private java.util.List<String> tools;

    /** 绑定模型连接（可空） */
    private Long modelConnectionId;

    /** 挂载工作流（可空） */
    private Long workflowId;

    /** 挂载的工作流版本（可空 = 跟随工作流当前生效版本） */
    private Integer workflowVersion;

    /** 填充路由三元组。 */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.AGENT_CREATE);
    }

}
