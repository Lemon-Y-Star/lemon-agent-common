# lemon-agent-common

LemonAgent 平台 · 通用基座。基于 `architecture` 框架（`com.lemon:architecture-*`，NAS Gitea 私服）搭建，
产品口径见《LemonAgent 工作台 PRD》与《后端接口契约（Agent 运行时切片）》。

## 版本规划（一步一步来）

| 版本 | 范围 | 状态 |
|---|---|---|
| v1 | 平台基座：账号认证（登录 / 登出 / 身份 / 改密）+ 模型连接管理 + MCP 连接管理（平台公共 11 个预置 + 用户自建），React + TS 前端 | ✅ |
| v2.0 | 对话运行时·纯对话：会话 / 运行 / SSE 事件流（seq 连续 + 断线重连）/ 取消 / 运行历史；Spring AI 2.0 流式（BYOK 防腐层） | ✅ |
| v2.1 | 工具调用 + 审批挂起（demo_ticket 可见效果，BLOCKED 态 + approval 端点） | ✅ |
| v2.2 | 会话深链路由（/chat/:sessionId 刷新定位）+ 运行历史页（FR-18.5 左列表右详情） | ✅ |
| v2.3 | 知识检索切片：`search_docs` 接 PgVector 真检索（Agentic RAG；embedding 未配置时工具不注册） | ✅ |
| v2.4 | 会话管理全闭环：重命名 / 删除（run → event → message → session 级联）/ 深链复用 | ✅ |
| v2.5 | Agent 管理（FR-6 最小充分版：平台公共只读 + 自建增删改 + 对话页挂载）+ 用量与额度（FR-16）+ 网站统计（FR-22.1，admin）+ 收件箱派生通知（FR-21） | ✅ |
| v3.0 | Agent 三要素补齐：可用工具白名单（每 Agent 勾选，运行时只装配已授权工具）+ 模型来源绑定 + 启停开关；新增工具目录接口 | ✅ |
| v3.1 | 策略模板 plan-execute：先出计划（2..5 步，落库）再逐步执行，SSE 可视化计划进度，与工具审批打通 | ✅ |
| v3.2.0（本版） | 执行图后端：`agent_graph` 版本化 + Kahn 校验 + START/LLM/TOOL/APPROVAL/END 五类节点调度 + 图快照冻结 + `graph_started/graph_completed` 事件 | ✅ |
| v3.2.1（本版） | 执行图画布：拖拽定位（坐标随图持久化）+ 连线 + 检查器（节点指令/工具/入参/审批语/节点级白名单）+ 保存走服务端校验 | ✅ |

## 结构

```
lemon-agent-common/
├── backend/     # Java 21 + Spring Boot 4.1 + MySQL 8 + Flyway + Sa-Token，architecture 七模块（含 integration 模型防腐层）
│   ├── model/         # 请求/响应/枚举，继承框架 BaseReq / BaseResult
│   ├── util/          # AES-GCM 密钥加密、PBKDF2 口令哈希、密码策略（纯工具，带单测）
│   ├── dal/           # 实体 + 仓储接口 + JDBC 实现（JdbcTemplate）
│   ├── service-core/  # 领域服务：TokenStore、认证、模型连接、MCP 连接
│   ├── service/       # @HandlerRoute 业务处理器（命令走框架路由链）
│   ├── integration/   # Spring AI 2.0 流式模型适配（org.springframework.ai 只允许出现在此）
│   ├── web/           # Controller + 框架全局异常装配
│   └── bootstrap/     # 启动 + 认证拦截器 + Flyway 迁移 + 组合根配置
└── frontend/    # React 18 + TypeScript + Vite（登录 / 账户 / 模型连接 / MCP 连接）
```

命令型请求走框架标准链路：`req.fillRoute(source)` → `OptUtil.exec` → `RequestDispatcher` → `@HandlerRoute`
处理器（幂等、校验、api 日志、异常收口由框架统一完成）；查询类直调领域服务；
未认证请求由拦截器直接 HTTP 401。

## v2.0 对话运行时实现要点（契约 API契约_Agent运行时 §4/§5）

- **事件流三条铁律**：seq 在 run 内从 1 连续递增；先落库（UNIQUE(run_id,seq) 兜底）再推送；
  终态事件必是最后一个 seq。`EventPublisher` 持 per-run 锁分配 seq，订阅按契约 §4.7 的
  8 步处理（重放 → 注册 → ★补一轮消除空档），15s 心跳注释帧不占 seq。
- **token 攒批**：40 字符 / 200ms 双阈值合并（BUILD-SPEC §5.5），写入从 300+ 次小事务降到 ~8 次；
  重放粒度变粗但 `run_completed.outputText` 是权威正文、气泡以它覆盖。
- **幂等与会话互斥**：`UNIQUE(user_id, client_request_id)` 命中返回原 run（duplicated=true）；
  同会话并发 run → 409 SESSION_BUSY（data.activeRunId 带出）。
- **模型防腐**：`ModelPort` 纯接口在 model，Spring AI 2.0 流式实现在 integration
  （`org.springframework.ai.*` 与 Reactor 不出该模块）；模型按用户的 BYOK 连接构造。
- **进程重启收口**：启动期把遗留 RUNNING/BLOCKED run 标 FAILED(INTERRUPTED)（契约 §6.2）。

## v2.5 Agent 管理与观测实现要点（FR-6 / FR-16 / FR-21 / FR-22.1 最小充分版）

- **一表分治**：`agent_config` 用 `user_id IS NULL` 表示平台公共 Agent（所有人可见、只读），
  自建 Agent 按 (id, user_id) 双键收口——越权与不存在同样返回 `NOT_FOUND`（不泄漏存在性），
  改/删平台 Agent 返回 `FORBIDDEN`。
- **挂载语义（PRD S2「跟随已启用版本」）**：`agent_run.agent_id` 落库为事实，执行期按 `run.agent_id`
  **回查**配置——系统提示词由 Agent 的 instruction 决定，模型连接优先级 = Agent 绑定 > 用户最新启用。
  执行器不持有 Agent 内存态：审批挂起跨 HTTP 恢复、进程重启后语义一致（与 `pending_approval_json` 同口径）。
- **平台提示词不下发**：列表接口对平台 Agent 返回 `instruction = null`，避免照抄平台提示词自建同名 Agent
  绕过版本管理；只有自建 Agent 回传明文。
- **版本号归库**：`UPDATE ... SET version = version + 1` 后**回读实体**再返回，响应体 version / updatedAt
  与库一致（否则前端看到版本不动、updatedAt 不刷新）。
- **派生通知不建表**：收件箱 = 待审批（BLOCKED）+ 近 7 天失败运行；已读语义由前端 localStorage 的最后查看时间承载。
- **管理端**：`/api/v1/admin/stats` 先查 `user_account.role` 再放行（仅 `admin`），聚合走 MySQL 直查，不做缓存。
- **已知环境限制（非代码问题）**：`api.shuaiapi.com` 中转的 `deepseek-v4.1-flash-0910` 在请求**带 `tools`** 时会忽略
  system 消息——裸 HTTP 复现：同一 system prompt 不带 tools 时模型严格照办，带上 tools 即恢复自由发挥。
  表现为「Agent 人格不生效」，换官方或其它渠道即恢复；排查时先看 `RunExecutor` 的挂载日志（`run N 挂载 agentId=… -> Agent[名] vX`）。

## v3.0 Agent 三要素实现要点（FR-6 完整版）

- **三要素落点**：`instruction`（人格）/ `tools_json`（可用工具）/ `model_connection_id`（模型来源），
  运行期由 `run.agent_id` 回查后**一次装配**——人格决定 system prompt、白名单决定下发的 tools、绑定决定用哪个连接。
- **白名单语义**：`NULL` = 跟随注册表（**将来新增的工具自动可用**，兼容 v2.5 及更早数据）；
  `[]` = 不装配任何工具；显式列表 = **精确授权**（新增工具不自动放开）。
  刻意**不**把「显式全选」归一成 NULL——两者将来语义不同，归一化等于偷偷扩大授权范围。
- **双闸**：装配层按白名单过滤下发给模型的 `tools`；执行层再兜底一次——模型若报出未授权工具名，
  不执行、回注「未授权，请改用已授权工具或直接回答」，让模型自行改写答复（防幻觉越权，比只靠过滤更稳）。
- **模型绑定**：创建时校验连接属于本人且已启用（越权 → NOT_FOUND，停用 → VALIDATION_ERROR）；
  更新时 `modelConnectionId` 传 `null` = 保持不变、传 `0` = 清除绑定（回落用户最新启用连接）。
- **工具目录**：`GET /api/v1/workspace/tools` 返回 `name / description / needsApproval / available`，
  前端据此渲染勾选框；`search_docs` 在未配置 embedding 时 `available=false` 并置灰（工具本身不注册进装配）。
- **停用的语义**：停用只是让对话页选不到（`usable=false`），**不删数据、不影响历史运行**；
  已在跑的运行按创建时的 `agent_id` 继续。

## v3.1 策略模板 plan-execute 实现要点（FR-7.1 的前置切片）

- **两段式**：规划（一次模型调用，只读文本、**不给工具**）→ 逐步执行（每步一次模型调用，带工具）。
  不给工具既是成本考虑，也绕开了"某些渠道带 tools 时会丢 system 消息"的差异，让规划质量稳定。
- **计划落库，执行器无内存态**：`agent_run.plan_json`（步骤 + 状态 + 产出 + 用量）、`plan_step`（游标）。
  每步完成后覆盖写一次；工具审批挂起、进程重启后都从当前步骤续跑，**不会重做前面的步骤**。
- **三个新事件**：`plan_created`（步骤清单）/ `step_started` / `step_completed`；`token` 事件新增 `stepIndex`，
  前端据此渲染计划卡并高亮当前步骤（刷新后可从运行快照的 `plan` 字段复原）。
- **解析宁松不严（实测必需）**：剥代码块 → 取首个 `{...}` → 读 `steps`；JSON 不成立时按行解析
  （优先编号行，其次项目符号；过滤条件句/冒号结尾的小标题/重复项；**不足 2 步不用**，退化成"单步：完成用户请求"）。
  实测渠道上的模型常把计划写成「执行计划：1. … 2. …」而不是 JSON，只靠 JSON 会一路退化；
  行解析兜底后同一请求从「6 个碎句假步骤、4679 tokens」降到「2 个真步骤、1058 tokens」。步骤上限 6。
- **上下文边界**：每步上下文 = 会话历史 + 已完成步骤的产出（合成 ASSISTANT 消息，**不落消息表**，
  避免计划中间产物污染后续对话）；`run_completed.outputText` 是按步骤拼装的权威正文。
- **与工具审批的组合**：步骤内触发高风险工具 → 照常 BLOCKED → 批准后 `resumeApproved` 重新进入
  `planLoop`，从**同一个 step** 重跑（工具结果作为合成上下文注入），这是"计划可恢复"的关键。
- **成本口径**：N+1 次模型调用（N ≤ 6），run 的 token 用量 = 各步累加（含规划那次不计入步骤）。

## v3.2.0 执行图后端实现要点（FR-7.1 最小落地）

- **图是版本化实体**：`agent_graph(agent_id, version, graph_json, status)`——保存即 `version+1`，旧版本置 `status=0`；
  Agent 被删除时级联清理图（不留孤儿行）。
- **运行冻结快照**：创建 `custom` 运行时把图**快照**写进 `agent_run.graph_json`——之后再改图，**在跑的运行不受影响**、可复现。
- **校验双跑**：保存时校验一次，创建运行时再校验一次（图可能被历史版本带进来）。规则：节点 ≤20、
  ID 唯一、类型白名单、唯一 START、至少一个 END、边两端存在、START 无入边、非 START 必有入边、
  TOOL 引用已注册工具、无环（Kahn）。失败直接 400，**不分配事件 seq**——运行时永远看不到半张图。
- **五类节点**：START/END（占位）· LLM（一次模型调用，system = Agent 人格 + 节点指令，输入带上游产出）·
  TOOL（执行作者显式配置的工具，**作者配置 = 已授权，不再走审批**）· APPROVAL（run 与节点同时 BLOCKED，
  `kind=NODE`，批准后 `resumeNodeApproved` 标记节点完成再续跑）。
- **授权仍然双闸**：节点可声明 `tools`（PRD FR-6.5 §⑪ 三态），未声明继承 Agent；模型自主发起的工具调用
  照旧走装配闸 + 执行闸 + 工具审批。
- **事件协议**：只用 `graph_started` / `graph_completed` 两个新事件，节点级复用 `step_started/step_completed`
  （额外带 `nodeId` / `nodeType`），`token` 带 `stepIndex` —— 前端改造成本≈0。
- **串行调度**：v3.2.0 按 Kahn 拓扑序**串行**执行（进度落 `graph_state_json`：done/outputs/current/用量），
  并行与条件路由留后续切片；节点完成即覆盖写一次，审批挂起/进程重启都能续跑。
- **迁移注意**：MySQL DDL 非事务，V007 首跑失败会留下半截列；处理方式是先清理残留（表/列/flyway 记录）再重跑。

## v3.2.1 执行图画布实现要点

- **入口**：Agent 列表每行「执行图」→ `/agents/:id/graph`；平台公共 Agent 只读（`editable=false` 由后端给）。
- **拖拽定位**：坐标存在节点字段 `x/y`（调度不读，纯排版），随图一起落库——**刷新后位置不丢**；
  没坐标的历史图由前端做一次网格自动布局，保存时写回。
- **连线**：画布内点节点「连线」→ 点目标节点「连到这里」（有向，重复边自动忽略）；
  检查器同时列出出边/入边并可删除。拖拽连线手感好但容错差，先选确定性更高的"点选连线"。
- **检查器按类型给字段**：LLM（节点指令 + 节点级工具白名单三态：继承/不装配/自定义）、
  TOOL（工具下拉 + 固定入参 JSON）、APPROVAL（审批提示语）、通用（标题/出边/删除节点）。
- **校验只做一套**：前端只在入参 JSON 语法上即时报错，**拓扑与工具校验全部交给后端**——
  「能存下就一定跑得起来」这条保证只由服务端负责，避免两套规则打架。

## 启动

前置：MySQL 8（建库 `lemon_agent`，Flyway 首次启动自动建表并写入种子数据）、
本机 `~/.m2/settings.xml` 已配置 gitea-lemon 私服（见框架 `docs/new-project-sop.md`）。

```bash
# 后端（默认 18090；种子账号 admin / lemon123456，首次登录后请改密）
cd backend && mvn spring-boot:run -pl lemon-agent-backend-bootstrap

# 前端（默认 5173，/api 代理到 18090）
cd frontend && npm install && npm run dev
```

## 第一版实现要点

- **会话**：登录会话由 **Sa-Token**（Boot 4 适配线 1.46.0）管理——`token-name=Authorization` +
  `token-prefix=Bearer` 与前端约定对齐，`TokenStore` 端口的 Sa-Token 实现收口在组合根，
  多实例部署时换 Redis 存储源即可，认证链路其余代码不动。
- **口令**：PBKDF2WithHmacSHA256（21 万轮、随机盐）；新密码策略与 PRD FR-2.4 校验链同口径
  （≥10 位、UTF-8 ≤72 字节——汉字按字节计），前后端双重校验，策略单测覆盖边界。
- **连接密钥**：AES-256-GCM 加密落库，启动期注入密钥（`lemon.crypto.secret`，缺失即启动失败）；
  接口只回传 `hasSecret` 与末 4 位，任何接口不回传明文/密文。
- **模型连接**：baseUrl 主机白名单校验（`allowedHosts`，防内网跳板）；apiKey 更新传空 = 保留原密钥。
- **MCP 连接**：平台公共 11 个（`user_id IS NULL`、`editable=0`）与用户自建同表分治；
  自建连接按 (id, user_id) 双键收口，越权即 NOT_FOUND。
- **归属与越权**：所有数据按 userId 收口；命令请求的 userId 由认证拦截器解析回填，客户端不可传。

## 测试

```bash
cd backend && mvn test -pl lemon-agent-backend-util -am   # 14 个单测：密码策略边界 / 哈希往返 / AES 往返
cd frontend && npm run build                              # tsc 严格模式 + vite 构建
```
