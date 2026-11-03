package com.lemon.lemonagent.integration.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemon.lemonagent.model.runtime.CodeSandboxPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 沙箱 HTTP 客户端（integration 层唯一实现）。
 *
 * <p>与 {@code McpClientToolPort} 同款姿势：HttpClient 锁 HTTP/1.1（对纯 HTTP
 * 服务先发 h2c 升级会被直接断连）；地址由 {@code LEMON_SANDBOX_URL} 注入，
 * 未配置时保持注册但调用报明确错误——主流程（对话/图执行）不因沙箱缺席而中断。</p>
 */
@Component
public class HttpCodeSandboxClient implements CodeSandboxPort {

    private static final Logger log = LoggerFactory.getLogger(HttpCodeSandboxClient.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 沙箱基地址（compose 内网名 / 本地开发 127.0.0.1:17770）。 */
    private final String baseUrl;

    /** 沙箱访问令牌（与沙箱容器 LEMON_SANDBOX_TOKEN 一致；空 = 沙箱侧未启用校验）。 */
    private final String token;

    public HttpCodeSandboxClient(
            @Value("${lemon.sandbox.url:http://127.0.0.1:17770}") String baseUrl,
            @Value("${lemon.sandbox.token:}") String token) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://127.0.0.1:17770" : baseUrl;
        this.token = token == null ? "" : token.trim();
        log.info("代码沙箱客户端就绪：{}（令牌校验：{}）", this.baseUrl, this.token.isEmpty() ? "未启用" : "已启用");
    }

    @Override
    public ExecResult exec(String code, List<String> upstream, java.util.Map<String, String> vars,
                           int timeoutSeconds) throws Exception {
        // 调用等待 = 执行超时 + 连接与序列化余量
        Duration callTimeout = Duration.ofSeconds(Math.max(timeoutSeconds, 1) + 10L);
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("code", code == null ? "" : code);
        payload.put("timeoutSeconds", Math.min(Math.max(timeoutSeconds, 1), 15));
        // v4.5.3 输入变量绑定：绑定时 upstream 传 null（沙箱端默认空列表），vars 传绑定字典
        payload.put("upstream", upstream == null ? List.of() : upstream);
        if (vars != null && !vars.isEmpty()) {
            payload.put("vars", vars);
        }
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/execute"))
                .timeout(callTimeout)
                .header("Content-Type", "application/json");
        if (!token.isEmpty()) {
            rb.header("X-Sandbox-Token", token);
        }
        HttpRequest request = rb.POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(payload)))
                .build();
        HttpResponse<byte[]> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("沙箱返回 HTTP " + resp.statusCode());
        }
        return objectMapper.readValue(resp.body(), ExecResult.class);
    }
}
