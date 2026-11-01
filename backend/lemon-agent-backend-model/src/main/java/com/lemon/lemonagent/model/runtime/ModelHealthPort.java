package com.lemon.lemonagent.model.runtime;

/**
 * 模型连接健康探活端口（v4.1 防腐）：出站 HTTP 对模型 API 的适配归口 integration，
 * service-core 的定时探测只做编排（何时探、探完落库）。
 */
public interface ModelHealthPort {

    /**
     * 探活一次：GET {base}/models（OpenAI 兼容），5s 超时。
     *
     * @param baseUrl 入参：连接 baseUrl（OpenAI 兼容根，通常以 /v1 结尾）
     * @param apiKey  入参：解密后的 API Key
     * @return 出参：探活结果（ok + 时延；失败时 latency 为 null）
     */
    ProbeResult probe(String baseUrl, String apiKey);

    /**
     * 探活结果。
     *
     * @param ok       是否可达（HTTP 200）
     * @param latencyMs 时延 ms（失败为 null）
     */
    record ProbeResult(boolean ok, Integer latencyMs) {
    }

}
