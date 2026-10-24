# lemon-agent-common

LemonAgent 平台 · 通用基座。基于 `architecture` 框架（`com.lemon:architecture-*`，NAS Gitea 私服）搭建，
产品口径见《LemonAgent 工作台 PRD》与《后端接口契约（Agent 运行时切片）》。

## 版本规划（一步一步来）

| 版本 | 范围 | 状态 |
|---|---|---|
| v1（本版） | 平台基座：账号认证（登录 / 登出 / 身份 / 改密）+ 模型连接管理 + MCP 连接管理（平台公共 11 个预置 + 用户自建），React + TS 前端 | ✅ |
| v2+ | 对话运行时（会话 / 运行 / SSE 事件流 / 审批 / 断线重连） | 规划中 |
| v3+ | Agent 搭建（策略模板 / 节点编辑器 / 执行图） | 规划中 |

## 结构

```
lemon-agent-common/
├── backend/     # Java 21 + Spring Boot 4.1 + MySQL 8 + Flyway，architecture 六模块 + dal
│   ├── model/         # 请求/响应/枚举，继承框架 BaseReq / BaseResult
│   ├── util/          # AES-GCM 密钥加密、PBKDF2 口令哈希、密码策略（纯工具，带单测）
│   ├── dal/           # 实体 + 仓储接口 + JDBC 实现（JdbcTemplate）
│   ├── service-core/  # 领域服务：TokenStore、认证、模型连接、MCP 连接
│   ├── service/       # @HandlerRoute 业务处理器（命令走框架路由链）
│   ├── web/           # Controller + 框架全局异常装配
│   └── bootstrap/     # 启动 + 认证拦截器 + Flyway 迁移 + 组合根配置
└── frontend/    # React 18 + TypeScript + Vite（登录 / 账户 / 模型连接 / MCP 连接）
```

命令型请求走框架标准链路：`req.fillRoute(source)` → `OptUtil.exec` → `RequestDispatcher` → `@HandlerRoute`
处理器（幂等、校验、api 日志、异常收口由框架统一完成）；查询类直调领域服务；
未认证请求由拦截器直接 HTTP 401。

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
