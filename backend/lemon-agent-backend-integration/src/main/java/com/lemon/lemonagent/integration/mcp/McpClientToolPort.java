package com.lemon.lemonagent.integration.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lemon.lemonagent.model.mcp.McpToolPort;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP 工具端口实现：STREAMABLE HTTP（JSON-RPC over HTTP POST）+ STDIO（子进程 stdin/stdout）。
 *
 * <p>设计要点：</p>
 * <ul>
 *   <li>HttpClient 强制 HTTP/1.1——明文 HTTP 端点对 h2c 升级请求会直接断连（本机踩坑）；</li>
 *   <li>HTTP 无独立会话态：每次调用先 initialize 换 Mcp-Session-Id（多数远端网关按会话头隔离），
 *       已知会话缓存在 {@code sessions}，404/失效时重建；</li>
 *   <li>STDIO 每次调用拉起一个子进程（initialize → 调用 → 销毁），进程随调用生命周期，
 *       不做常驻进程池——单机平台场景下每次 1~2s 的冷启动换零状态管理；</li>
 *   <li>响应既兼容 application/json，也兼容 text/event-stream（data: 行里取匹配 id 的那条）。</li>
 * </ul>
 */
@Component
public class McpClientToolPort implements McpToolPort {

    private static final String PROTOCOL_VERSION = "2025-03-26";
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(30);
    private static final long IO_DEADLINE_MILLIS = 45_000L;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger rpcIds = new AtomicInteger(1);

    /** 连接 id → Mcp-Session-Id（STREAMABLE 复用会话，失败重建）。 */
    private final Map<Long, String> sessions = new java.util.concurrent.ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    @Override
    public List<ToolDef> listTools(ConnSpec conn) throws Exception {
        JsonNode result = rpcWithSession(conn, "tools/list", Map.of());
        List<ToolDef> tools = new ArrayList<>();
        for (JsonNode t : result.path("tools")) {
            String schema = t.has("inputSchema") ? objectMapper.writeValueAsString(t.get("inputSchema")) : "{}";
            tools.add(new ToolDef(
                    t.path("name").asText(),
                    t.path("description").asText(""),
                    schema));
        }
        return tools;
    }

    @Override
    public Map<String, Object> callTool(ConnSpec conn, String toolName, Map<String, Object> args) throws Exception {
        JsonNode result = rpcWithSession(conn, "tools/call",
                Map.of("name", toolName, "arguments", args == null ? Map.of() : args));
        boolean isError = result.path("isError").asBoolean(false);
        JsonNode structured = result.get("structuredContent");
        if (!isError && structured != null && structured.isObject()) {
            return objectMapper.convertValue(structured, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() { });
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode c : result.path("content")) {
            if ("text".equals(c.path("type").asText())) {
                if (text.length() > 0) {
                    text.append('\n');
                }
                text.append(c.path("text").asText());
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        if (isError) {
            out.put("error", text.toString());
        } else {
            out.put("result", text.toString());
        }
        return out;
    }

    // ==================================================================
    // JSON-RPC 会话层
    // ==================================================================

    /** 带会话的 RPC：initialize（或复用缓存会话）→ notifications/initialized → 业务方法。 */
    private JsonNode rpcWithSession(ConnSpec conn, String method, Map<String, Object> params) throws Exception {
        String transport = conn.transportType() == null ? "" : conn.transportType().toUpperCase();
        if ("SSE".equals(transport)) {
            throw new UnsupportedOperationException("SSE 旧协议暂不支持，请将连接改为 STREAMABLE 类型");
        }
        if ("STDIO".equals(transport)) {
            return stdioRpc(conn, method, params);
        }
        return httpRpc(conn, method, params);
    }

    // ==================================================================
    // STREAMABLE HTTP
    // ==================================================================

    private JsonNode httpRpc(ConnSpec conn, String method, Map<String, Object> params) throws Exception {
        String sessionId = sessions.get(conn.id());
        try {
            return httpRoundTrip(conn, method, params, sessionId);
        } catch (McpSessionExpired e) {
            // 会话失效：强制重新握手一次（不让远端会话过期拖垮工具调用）
            sessions.remove(conn.id());
            return httpRoundTrip(conn, method, params, null);
        }
    }

    private JsonNode httpRoundTrip(ConnSpec conn, String method, Map<String, Object> params, String sessionId) throws Exception {
        boolean needSession = sessionId == null;
        if (needSession) {
            JsonNode initResult = httpPost(conn, "initialize",
                    Map.of("protocolVersion", PROTOCOL_VERSION,
                            "capabilities", Map.of(),
                            "clientInfo", Map.of("name", "lemon-agent", "version", "1.0")),
                    null);
            sessionId = lastSessionHeader;
            if (sessionId == null || sessionId.isBlank()) {
                // 无会话头的实现（无状态网关）：握手即完成
                sessionId = "";
            }
            sessions.put(conn.id(), sessionId);
            try {
                httpPost(conn, "notifications/initialized", Map.of(), sessionId.isEmpty() ? null : sessionId);
            } catch (Exception ignored) {
                // 通知失败不致命
            }
        }
        try {
            return httpPost(conn, method, params, sessionId.isEmpty() ? null : sessionId);
        } catch (McpSessionExpired e) {
            if (needSession) {
                throw e;
            }
            throw new McpSessionExpired();
        }
    }

    /** 上一次响应捕获的 Mcp-Session-Id（HttpSession 无状态，用字段传递）。 */
    private String lastSessionHeader;

    private JsonNode httpPost(ConnSpec conn, String method, Map<String, Object> params, String sessionId) throws Exception {
        int id = params.isEmpty() && method.startsWith("notifications") ? -1 : rpcIds.getAndIncrement();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        if (id >= 0) {
            payload.put("id", id);
        }
        payload.put("method", method);
        if (!params.isEmpty()) {
            payload.put("params", params);
        }
        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create(conn.url()))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", PROTOCOL_VERSION);
        if (conn.secret() != null && !conn.secret().isBlank()) {
            rb.header("Authorization", "Bearer " + conn.secret());
        }
        if (sessionId != null) {
            rb.header("Mcp-Session-Id", sessionId);
        }
        HttpResponse<byte[]> resp = httpClient.send(rb
                .POST(HttpRequest.BodyPublishers.ofByteArray(objectMapper.writeValueAsBytes(payload)))
                .build(), HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() == 404 || resp.statusCode() == 400 && sessionId != null) {
            throw new McpSessionExpired();
        }
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + resp.statusCode() + " from " + conn.url());
        }
        resp.headers().firstValue("Mcp-Session-Id").ifPresent(v -> lastSessionHeader = v);
        String contentType = resp.headers().firstValue("Content-Type").orElse("");
        String body = new String(resp.body(), StandardCharsets.UTF_8);
        if (contentType.contains("text/event-stream")) {
            return fromSse(body, id);
        }
        if (body.isBlank()) {
            // 通知类请求：无响应体属正常
            return objectMapper.createObjectNode();
        }
        JsonNode node = objectMapper.readTree(body);
        if (node.has("error")) {
            throw new IllegalStateException("MCP error: " + node.get("error").toString());
        }
        return node.path("result");
    }

    /** 从 SSE 帧里找匹配 id 的 data 行并解析。 */
    private JsonNode fromSse(String body, int expectId) throws Exception {
        for (String block : body.split("\n\n")) {
            String data = null;
            for (String line : block.split("\n")) {
                if (line.startsWith("data:")) {
                    data = line.substring(5).trim();
                }
            }
            if (data == null || data.isBlank()) {
                continue;
            }
            JsonNode node = objectMapper.readTree(data);
            if (node.path("id").asInt(-1) == expectId) {
                if (node.has("error")) {
                    throw new IllegalStateException("MCP error: " + node.get("error").toString());
                }
                return node.path("result");
            }
        }
        throw new IllegalStateException("SSE 响应中未找到 id=" + expectId + " 的结果");
    }

    private static final class McpSessionExpired extends RuntimeException {
    }

    // ==================================================================
    // STDIO
    // ==================================================================

    /**
     * STDIO 一次调用 = 一个子进程：initialize → initialized 通知 → 业务方法 → 销毁。
     * 读线程 + 有界队列实现带超时的行读取（避免服务端挂起时永久阻塞）。
     */
    private JsonNode stdioRpc(ConnSpec conn, String method, Map<String, Object> params) throws Exception {
        List<String> argv = new ArrayList<>(List.of(conn.command().trim().split("\\s+")));
        // Windows 下 npx/uvx 是 .cmd 批处理，ProcessBuilder 不能直接 CreateProcess，须借 cmd /c
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            argv.add(0, "/c");
            argv.add(0, "cmd");
        }
        Process p = new ProcessBuilder(argv).redirectErrorStream(false).start();
        LinkedBlockingQueue<String> lines = new LinkedBlockingQueue<>(2048);
        Thread reader = Thread.ofVirtual().unstarted(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!lines.offer(line, 1, TimeUnit.SECONDS)) {
                        break;
                    }
                }
            } catch (Exception ignored) {
                // 进程被销毁时流关闭属正常
            }
        });
        reader.setDaemon(true);
        reader.start();
        long deadline = System.currentTimeMillis() + IO_DEADLINE_MILLIS;
        try {
            int initId = rpcIds.getAndIncrement();
            write(p, Map.of("jsonrpc", "2.0", "id", initId, "method", "initialize",
                    "params", Map.of("protocolVersion", PROTOCOL_VERSION, "capabilities", Map.of(),
                            "clientInfo", Map.of("name", "lemon-agent", "version", "1.0"))));
            JsonNode initResp = readResponse(lines, initId, deadline);
            write(p, Map.of("jsonrpc", "2.0", "method", "notifications/initialized"));
            if ("initialize".equals(method)) {
                return initResp;
            }
            int callId = rpcIds.getAndIncrement();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("jsonrpc", "2.0");
            payload.put("id", callId);
            payload.put("method", method);
            if (!params.isEmpty()) {
                payload.put("params", params);
            }
            write(p, payload);
            return readResponse(lines, callId, deadline);
        } finally {
            p.destroyForcibly();
        }
    }

    private void write(Process p, Object payload) throws Exception {
        byte[] bytes = (objectMapper.writeValueAsBytes(payload));
        p.getOutputStream().write(bytes);
        p.getOutputStream().write('\n');
        p.getOutputStream().flush();
    }

    /** 从行队列里取匹配 id 的 JSON-RPC 响应（跳过通知/无关帧），超时抛异常。 */
    private JsonNode readResponse(LinkedBlockingQueue<String> lines, int expectId, long deadline) throws Exception {
        while (System.currentTimeMillis() < deadline) {
            String line = lines.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (line == null) {
                break;
            }
            if (!line.startsWith("{")) {
                continue;
            }
            JsonNode node;
            try {
                node = objectMapper.readTree(line);
            } catch (Exception e) {
                continue;
            }
            if (node.path("id").asInt(-2) != expectId) {
                continue;
            }
            if (node.has("error")) {
                throw new IllegalStateException("MCP error: " + node.get("error").toString());
            }
            return node.path("result");
        }
        throw new IllegalStateException("STDIO MCP 响应超时（" + IO_DEADLINE_MILLIS + "ms）");
    }

}
