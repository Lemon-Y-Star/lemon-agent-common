package com.lemon.lemonagent.model.modelconn;

import lombok.Data;

import java.util.List;

/**
 * 模型连接列表响应数据（形状对齐 PRD FR-13 的实测返回，便于前端按原型渲染）。
 */
@Data
public class ModelConnectionsData {

    /** 平台密钥存储是否可用（v1 恒为 true：AES-GCM 密钥由组合根装配） */
    private boolean secretsAvailable;

    /** 允许的 baseUrl 主机白名单（越界连接在保存时被拒） */
    private List<String> allowedHosts;

    /** 辅助功能的模型来源：v1 固定 PLATFORM（平台模型） */
    private String auxiliaryProviderSource;

    /** 辅助用途说明（PRD：路由规划摘要 / 知识索引向量 / 重排） */
    private List<String> auxiliaryPurposes;

    /** 当前用户的个人连接列表 */
    private List<ModelConnectionData> connections;

    /** 是否允许使用平台辅助模型 */
    private boolean allowPlatformAuxiliaries;

}
