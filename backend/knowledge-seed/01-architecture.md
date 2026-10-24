# LemonAgent 平台架构说明

LemonAgent 是一个基于 lemon architecture 框架搭建的智能体平台，采用前后端分离架构。后端为 Java 21 + Spring Boot 4.1 + MySQL 8 + Flyway + Sa-Token 的多模块工程，共七个 Maven 模块：model（请求/响应模型与领域端口）、util（加密与口令工具）、dal（实体与 JDBC 仓储）、service-core（领域服务与运行时）、integration（Spring AI 模型适配防腐层）、service（Handler 业务处理器）、web（Controller）、bootstrap（启动与组合根）。前端为 React 18 + TypeScript + Vite。

架构的核心约定是防腐层设计：org.springframework.ai 的类型只允许出现在 integration 模块，service-core 通过纯接口 ModelPort 调用模型。这样换模型 SDK 或升级 Spring AI 版本时只改一个实现类，领域编排代码不动。模型调用按用户的 BYOK 连接构造，用户在「模型连接」页面配置 OpenAI 兼容端点与密钥，密钥经 AES-256-GCM 加密落库，接口永不回传明文。

对话运行时的核心设计是"事件先落库、再从库里推送"。每次运行（run）的事件流（token 增量、工具调用、审批请求等）先写入 agent_run_event 表（UNIQUE(run_id, seq) 保证 run 内序号连续），再推送给在线的 SSE 订阅者。这带来两个性质：断线重连用 afterEventId 重放即可补齐；服务重启后历史可完整回放。运行状态机有六态：RUNNING、BLOCKED、COMPLETED、FAILED、CANCELLED、TIMEOUT。
