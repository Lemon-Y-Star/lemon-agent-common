package com.lemon.lemonagent.model.enums;

/**
 * 平台管理侧的操作码常量（路由三元组的第三段）。
 *
 * <p>请求对象的 {@code fillRoute} 与处理器的 {@code @HandlerRoute.operation}
 * 共用同一份常量，避免"注册了一个路由、请求填了另一个"的隐性错配。</p>
 */
public final class Operations {

    /** 登录 */
    public static final String AUTH_LOGIN = "AUTH_LOGIN";

    /** 修改密码 */
    public static final String AUTH_PASSWORD_CHANGE = "AUTH_PASSWORD_CHANGE";

    /** 新建模型连接 */
    public static final String MODEL_CONN_CREATE = "MODEL_CONN_CREATE";

    /** 更新模型连接 */
    public static final String MODEL_CONN_UPDATE = "MODEL_CONN_UPDATE";

    /** 删除模型连接 */
    public static final String MODEL_CONN_DELETE = "MODEL_CONN_DELETE";

    /** 新建 MCP 连接 */
    public static final String MCP_CONN_CREATE = "MCP_CONN_CREATE";

    /** 更新 MCP 连接 */
    public static final String MCP_CONN_UPDATE = "MCP_CONN_UPDATE";

    /** 删除 MCP 连接 */
    public static final String MCP_CONN_DELETE = "MCP_CONN_DELETE";

    /** 创建对话会话 */
    public static final String SESSION_CREATE = "SESSION_CREATE";

    /** 重命名会话 */
    public static final String SESSION_RENAME = "SESSION_RENAME";

    /** 删除会话 */
    public static final String SESSION_DELETE = "SESSION_DELETE";

    /** 发起运行 */
    public static final String RUN_CREATE = "RUN_CREATE";

    /** 取消运行 */
    public static final String RUN_CANCEL = "RUN_CANCEL";

    /** 提交审批决定 */
    public static final String APPROVAL_SUBMIT = "APPROVAL_SUBMIT";

    /** 创建个人 Agent */
    public static final String AGENT_CREATE = "AGENT_CREATE";

    /** 更新个人 Agent */
    public static final String AGENT_UPDATE = "AGENT_UPDATE";

    /** 删除个人 Agent */
    public static final String AGENT_DELETE = "AGENT_DELETE";

    /** 保存执行图（v3.2.0） */
    public static final String GRAPH_SAVE = "GRAPH_SAVE";

    /** 发起评测批次（v3.5） */
    public static final String EVAL_CREATE = "EVAL_CREATE";

    /** 知识库文档新增 / 删除（v3.6） */
    public static final String KB_DOC_CREATE = "KB_DOC_CREATE";

    public static final String KB_DOC_DELETE = "KB_DOC_DELETE";

    /** 长期记忆新增 / 启停 / 删除（v3.6） */
    public static final String MEMORY_CREATE = "MEMORY_CREATE";

    public static final String MEMORY_TOGGLE = "MEMORY_TOGGLE";

    public static final String MEMORY_DELETE = "MEMORY_DELETE";

    /** 工作流创建 / 保存新版本（v3.7） */
    public static final String WORKFLOW_CREATE = "WORKFLOW_CREATE";

    public static final String WORKFLOW_VERSION_SAVE = "WORKFLOW_VERSION_SAVE";

    /** 运行反馈（产物与反馈，v3.8） */
    public static final String RUN_FEEDBACK = "RUN_FEEDBACK";

    /** API 分发密钥创建 / 吊销（v3.8） */
    public static final String APIKEY_CREATE = "APIKEY_CREATE";

    public static final String APIKEY_REVOKE = "APIKEY_REVOKE";

    private Operations() {
    }

}
