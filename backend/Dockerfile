# 多阶段构建：Maven 打包 bootstrap 可执行 jar，再用 JRE 21 运行。
# 运行时配置全部通过环境变量注入（见 bootstrap/src/main/resources/application.yml），
# LEMON_DB_PASSWORD 必须在部署平台填写，镜像内不含任何密钥。
#
# 分层缓存（v4.5.3 优化）：①依赖层只 COPY 各模块 pom + 解析依赖——只有 pom 变化才会重新下载；
# ②源码层 COPY 全部源码编译。此前 COPY . . 一把梭导致每次改代码都全量重拉依赖，
# NAS 外网抖动时构建 10-50 分钟（"部署卡顿"的根源）；分层后命中缓存只需编译，约 2-5 分钟。
ARG BUILD_IMAGE=docker.1ms.run/library/maven:3.9-eclipse-temurin-21
ARG RUN_IMAGE=docker.1ms.run/library/eclipse-temurin:21-jre
# Debian 版 node（glibc），与 temurin 的 Ubuntu 运行时兼容——stdio 型 MCP 连接靠 npx 拉起子进程
FROM docker.1ms.run/library/node:24 AS node-provider

FROM ${BUILD_IMAGE} AS build
WORKDIR /app

# ① 依赖层
COPY pom.xml .
COPY deploy/mirror-settings.xml deploy/mirror-settings.xml
COPY lemon-agent-backend-util/pom.xml lemon-agent-backend-util/
COPY lemon-agent-backend-model/pom.xml lemon-agent-backend-model/
COPY lemon-agent-backend-dal/pom.xml lemon-agent-backend-dal/
COPY lemon-agent-backend-integration/pom.xml lemon-agent-backend-integration/
COPY lemon-agent-backend-service-core/pom.xml lemon-agent-backend-service-core/
COPY lemon-agent-backend-service/pom.xml lemon-agent-backend-service/
COPY lemon-agent-backend-web/pom.xml lemon-agent-backend-web/
COPY lemon-agent-backend-bootstrap/pom.xml lemon-agent-backend-bootstrap/
RUN mvn -B -q -DskipTests -s deploy/mirror-settings.xml dependency:go-offline

# ② 源码层（依赖层缓存命中时只编译）
COPY . .
RUN mvn -B -DskipTests -s deploy/mirror-settings.xml package

FROM ${RUN_IMAGE} AS runtime
WORKDIR /app
# stdio 型 MCP 连接需要 node/npx 运行时可用
COPY --from=node-provider /usr/local/bin/node /usr/local/bin/node
COPY --from=node-provider /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -sf /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && ln -sf /usr/local/lib/node_modules/npm/bin/npx-cli.js /usr/local/bin/npx
# 预装平台 stdio 连接的三个包：运行时 npx 直接命中全局安装，冷启动不再去外网拉包
RUN npm install -g --no-audit --no-fund howtocook-mcp math-mcp @cyanheads/openfoodfacts-mcp-server \
    && npm cache clean --force

COPY --from=build /app/lemon-agent-backend-bootstrap/target/lemon-agent-backend-bootstrap-1.0.0.jar /app/app.jar

ENV SERVER_PORT=8090 \
    TZ=Asia/Shanghai \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseG1GC -Dfile.encoding=UTF-8"
EXPOSE 8090
# temurin 基础镜像不带 curl/wget，用 bash 内建 /dev/tcp 探测端口。
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=5 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1:8090' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
