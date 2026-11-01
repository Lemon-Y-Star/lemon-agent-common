package com.lemon.lemonagent.integration.model;

import com.lemon.lemonagent.model.runtime.ModelHealthPort;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * {@link ModelHealthPort} 的 HTTP 实现（防腐边界内）：GET {base}/models 探活。
 *
 * <p>与流式模型调用（SpringAiModelPort）同一出站通道归口：base 不以 /v1 结尾时补 /v1，
 * 5s 连接与响应超时，任何异常都折算为 fail——健康探测是可用性信号，不允许把异常抛回编排层。</p>
 */
@Component
public class ModelHealthHttpPort implements ModelHealthPort {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public ProbeResult probe(String baseUrl, String apiKey) {
        long start = System.currentTimeMillis();
        try {
            String base = baseUrl.replaceAll("/+$", "");
            String url = base.endsWith("/v1") ? base + "/models" : base + "/v1/models";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            int latency = (int) (System.currentTimeMillis() - start);
            return resp.statusCode() == 200 ? new ProbeResult(true, latency) : new ProbeResult(false, null);
        } catch (Exception e) {
            return new ProbeResult(false, null);
        }
    }

}
