package com.lemon.lemonagent.model.modelconn;

import com.lemon.lemonagent.model.enums.Operations;
import com.lemon.lemonagent.model.route.PlatformRoute;
import com.lemon.architecture.model.BaseReq;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 新建个人模型连接（PRD FR-13，用户自建 BYOK 连接）。
 */
@Getter
@Setter
public class CreateModelConnectionReq extends BaseReq {

    /** 归属用户（服务端由认证拦截器解析回填，客户端不可传） */
    private Long userId;

    /** 连接名称 */
    private String name;

    /** 提供方标识 */
    private String provider;

    /** OpenAI 兼容端点 */
    private String baseUrl;

    /** API 密钥（明文入参，服务端 AES-GCM 加密落库，不再回传） */
    private String apiKey;

    /** 可用模型名列表 */
    private List<String> models;

    /** 是否允许辅助功能使用 */
    private Boolean allowAuxiliary;

    /** 启用状态（缺省 = 启用；false 可创建暂不启用的连接） */
    private Boolean enabled;

    /**
     * 填充路由三元组。
     *
     * @param source 调用来源（X-Source 请求头）
     */
    public void fillRoute(String source) {
        PlatformRoute.fill(this, source, Operations.MODEL_CONN_CREATE);
    }

}
