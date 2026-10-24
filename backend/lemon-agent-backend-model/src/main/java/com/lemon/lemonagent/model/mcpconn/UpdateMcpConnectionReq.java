package com.lemon.lemonagent.model.mcpconn;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新用户自建 MCP 连接（平台公共连接由服务端拒绝：editable=false）。
 *
 * <p>{@code secret} 传空表示保留原密钥。</p>
 */
@Getter
@Setter
public class UpdateMcpConnectionReq extends BaseReq {

    /** 归属用户（服务端由认证拦截器解析回填，客户端不可传） */
    private Long userId;

    /** 连接 ID */
    private Long id;

    /** 连接名称 */
    private String name;

    /** 传输类型 */
    private String transportType;

    /** HTTP 端点 */
    private String url;

    /** 本地子进程命令 */
    private String command;

    /** 密钥（空 = 保留原值） */
    private String secret;

    /** 启用状态 */
    private Boolean enabled;

    /**
     * 填充路由三元组。
     *
     * @param source 调用来源（X-Source 请求头）
     */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.MCP_CONN_UPDATE);
    }

}
