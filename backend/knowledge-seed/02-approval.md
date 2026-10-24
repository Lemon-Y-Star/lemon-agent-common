# 工具审批机制

LemonAgent 的工具调用按「是否改变外部状态」划分为两类，而不是按「是否调用外部服务」划分。只读工具（如 search_docs 知识检索、current_time）直接执行；写操作工具（如 create_ticket 创建工单）在审批名单内，必须经用户确认后才会执行。

审批的完整流程：模型决定调用高风险工具时，运行状态从 RUNNING 迁移到 BLOCKED，审批快照（工具名、参数、风险等级、过期时间）原子写入 agent_run 表；SSE 流推送 approval_required 事件后断开，前端弹出审批卡片。用户批准后状态回到 RUNNING，执行器从数据库重建上下文并继续执行；拒绝则终态为 FAILED，错误归因 TOOL_REJECTED。

审批有 300 秒超时（可配置），超时扫描器每 30 秒轮询一次 BLOCKED 状态的运行，超时自动拒绝，错误归因 APPROVAL_TIMEOUT。审批决策本身是幂等的：同一个审批 ID 重复提交返回当前状态，不报错。审批跨 HTTP 请求存活的实现方式是把挂起快照持久化到数据库而不是内存，因此服务重启后待审批项依然有效。

审批名单不写死在代码里，通过 application.yml 的 agent.tools.approval-required 配置，显式白名单加默认拒绝——按工具名判定而不是读写自动判定，因为自动判定不可靠，显式名单才可审计。
