package com.lemon.lemonagent.servicecore.conn;

import com.lemon.lemonagent.model.modelconn.ModelConnectionData;
import com.lemon.lemonagent.model.modelconn.ModelConnectionsData;

import java.util.List;

/**
 * 个人模型连接领域服务（PRD FR-13）。
 */
public interface ModelConnectionService {

    /**
     * 查询连接列表页数据（含平台侧配置：主机白名单、辅助用途说明）。
     *
     * @param userId 入参：用户 ID
     * @return 出参：列表页数据
     */
    ModelConnectionsData list(Long userId);

    /**
     * 新建连接。
     *
     * @param userId   入参：用户 ID
     * @param name     入参：连接名称
     * @param provider 入参：提供方标识
     * @param baseUrl  入参：OpenAI 兼容端点
     * @param apiKey   入参：API 密钥（明文，加密落库）
     * @param models   入参：可用模型名列表
     * @param allowAuxiliary 入参：是否允许辅助功能使用
     * @param enabled        入参：是否启用（缺省启用；false 可创建暂不启用的连接）
     * @return 出参：创建后的连接数据
     */
    ModelConnectionData create(Long userId, String name, String provider, String baseUrl,
                               String apiKey, List<String> models, boolean allowAuxiliary, boolean enabled);

    /**
     * 更新连接；apiKey 为空表示保留原密钥。
     *
     * @param userId   入参：用户 ID
     * @param id       入参：连接 ID
     * @param name     入参：连接名称
     * @param provider 入参：提供方标识
     * @param baseUrl  入参：端点
     * @param apiKey   入参：新密钥（可空）
     * @param models   入参：模型名列表
     * @param allowAuxiliary 入参：是否允许辅助功能
     * @param enabled  入参：启用状态（可空 = 不变）
     * @return 出参：更新后的连接数据
     */
    ModelConnectionData update(Long userId, Long id, String name, String provider, String baseUrl,
                               String apiKey, List<String> models, Boolean allowAuxiliary, Boolean enabled);

    /**
     * 删除连接。
     *
     * @param userId 入参：用户 ID
     * @param id     入参：连接 ID
     */
    void delete(Long userId, Long id);

}
