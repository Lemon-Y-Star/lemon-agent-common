package com.lemon.lemonagent.model.enums;

/**
 * 连接启用状态：连接类资源（模型连接 / MCP 连接）通用的启停语义。
 */
public enum ConnectionStatus {

    /** 停用：节点引用时不可见，但配置保留，可随时启用 */
    DISABLED(0),

    /** 启用 */
    ENABLED(1);

    private final int code;

    ConnectionStatus(int code) {
        this.code = code;
    }

    /**
     * 数据库存储值。
     *
     * @return 出参：1 启用 / 0 停用
     */
    public int getCode() {
        return code;
    }

    /**
     * 从存储值还原枚举；非法值按停用处理（保守默认）。
     *
     * @param code 入参：存储值
     * @return 出参：对应枚举
     */
    public static ConnectionStatus from(int code) {
        return code == 1 ? ENABLED : DISABLED;
    }

}
