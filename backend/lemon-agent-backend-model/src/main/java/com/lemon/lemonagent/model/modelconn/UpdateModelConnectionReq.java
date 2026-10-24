package com.lemon.lemonagent.model.modelconn;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 更新个人模型连接。
 *
 * <p>{@code apiKey} 传空表示<b>保留原密钥</b>（改名称/端点/模型清单不强制重填密钥）；
 * 传新值即轮换密钥。</p>
 */
@Getter
@Setter
public class UpdateModelConnectionReq extends BaseReq {

    /** 归属用户（服务端由认证拦截器解析回填，客户端不可传） */
    private Long userId;

    /** 连接 ID */
    private Long id;

    /** 连接名称 */
    private String name;

    /** 提供方标识 */
    private String provider;

    /** OpenAI 兼容端点 */
    private String baseUrl;

    /** API 密钥（空 = 保留原值） */
    private String apiKey;

    /** 可用模型名列表 */
    private List<String> models;

    /** 是否允许辅助功能使用 */
    private Boolean allowAuxiliary;

    /** 启用状态 */
    private Boolean enabled;

    /**
     * 填充路由三元组。
     *
     * @param source 调用来源（X-Source 请求头）
     */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.MODEL_CONN_UPDATE);
    }

}
