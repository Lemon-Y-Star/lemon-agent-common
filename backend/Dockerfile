# 多阶段构建：Maven 打包 bootstrap 可执行 jar，再用 JRE 21 运行。
# 运行时配置全部通过环境变量注入（见 bootstrap/src/main/resources/application.yml），
# LEMON_DB_PASSWORD 必须在部署平台填写，镜像内不含任何密钥。
ARG BUILD_IMAGE=docker.1ms.run/library/maven:3.9-eclipse-temurin-21
ARG RUN_IMAGE=docker.1ms.run/library/eclipse-temurin:21-jre

FROM ${BUILD_IMAGE} AS build
WORKDIR /app

COPY . .
RUN mvn -B -DskipTests -s deploy/mirror-settings.xml package

FROM ${RUN_IMAGE} AS runtime
WORKDIR /app
COPY --from=build /app/lemon-agent-backend-bootstrap/target/lemon-agent-backend-bootstrap-1.0.0.jar /app/app.jar

ENV SERVER_PORT=8090 \
    TZ=Asia/Shanghai \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseG1GC -Dfile.encoding=UTF-8"
EXPOSE 8090
# temurin 基础镜像不带 curl/wget，用 bash 内建 /dev/tcp 探测端口。
HEALTHCHECK --interval=30s --timeout=5s --start-period=120s --retries=5 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8090' || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
