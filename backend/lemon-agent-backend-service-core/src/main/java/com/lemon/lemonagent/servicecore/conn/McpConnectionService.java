package com.lemon.lemonagent.servicecore.conn;

import com.lemon.lemonagent.model.mcpconn.McpConnectionData;

import java.util.List;

/**
 * MCP 连接领域服务（PRD FR-12：平台公共列表只读，用户自建可增删改）。
 */
public interface McpConnectionService {

    /**
     * 查询连接列表：平台公共在前（按名称序），用户自建在后（按创建倒序）。
     *
     * @param userId 入参：用户 ID
     * @return 出参：全部可见连接
     */
    List<McpConnectionData> list(Long userId);

    /**
     * 新建自建连接。
     *
     * @param userId        入参：用户 ID
     * @param name          入参：名称
     * @param transportType 入参：SSE / STDIO / STREAMABLE
     * @param url           入参：HTTP 端点（SSE / STREAMABLE 必填）
     * @param command       入参：本地命令（STDIO 必填）
     * @param secret        入参：密钥（可空）
     * @return 出参：创建后的连接
     */
    McpConnectionData create(Long userId, String name, String transportType,
                             String url, String command, String secret);

    /**
     * 更新自建连接；secret 为空表示保留原密钥。
     *
     * @param userId        入参：用户 ID
     * @param id            入参：连接 ID
     * @param name          入参：名称
     * @param transportType 入参：传输类型
     * @param url           入参：端点
     * @param command       入参：本地命令
     * @param secret        入参：新密钥（可空）
     * @param enabled       入参：启用状态（可空 = 不变）
     * @return 出参：更新后的连接
     */
    McpConnectionData update(Long userId, Long id, String name, String transportType,
                             String url, String command, String secret, Boolean enabled);

    /**
     * 删除自建连接。
     *
     * @param userId 入参：用户 ID
     * @param id     入参：连接 ID
     */
    void delete(Long userId, Long id);

}
