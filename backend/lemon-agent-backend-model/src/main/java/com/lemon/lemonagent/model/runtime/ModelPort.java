package com.lemon.lemonagent.model.runtime;

import java.util.List;

/**
 * 模型调用防腐端口（BUILD-SPEC §2.3）：纯领域接口，零框架依赖。
 *
 * <p>{@code org.springframework.ai.*} 的类型不允许出现在 service-core / model 里——
 * 换模型 SDK / 换版本时只改 integration 的实现类。流式回调由实现层保证在
 * 单线程内串行回调（调用方负责攒批与落库顺序）。</p>
 *
 * <p>工具调用语义：请求携带 {@link ToolSpec} 时，若模型返回工具调用而非文本，
 * 实现层调用 {@link Listener#onToolCalls}（替代 onComplete），由调用方决定
 * 审批挂起还是直接执行——Spring AI 的内部工具执行循环在实现层被禁用。</p>
 */
public interface ModelPort {

    /**
     * 发起一次流式对话补全。
     *
     * @param request  入参：模型端点、凭据、上下文与工具清单
     * @param listener 入参：增量 / 工具调用 / 用量 / 完成 / 异常五类回调
     */
    void stream(ModelRequest request, Listener listener);

    /**
     * 模型请求：OpenAI 兼容端点 + 会话上下文 + 工具清单。
     *
     * @param baseUrl      兼容端点（如 https://api.deepseek.com/v1）
     * @param apiKey       解密后的 API Key（明文仅在内存中存在）
     * @param model        模型名
     * @param systemPrompt 系统提示词
     * @param history      历史消息（按时间升序，role: USER/ASSISTANT）
     * @param userInput    本轮用户输入
     * @param tools        可用工具清单（可空 = 纯对话）
     */
    record ModelRequest(String baseUrl, String apiKey, String model,
                        String systemPrompt, List<Msg> history, String userInput,
                        List<ToolSpec> tools) {
    }

    /**
     * 历史消息。
     *
     * @param role    USER / ASSISTANT
     * @param content 正文
     */
    record Msg(String role, String content) {
    }

    /**
     * 工具定义（透传给模型的 name / description / JSON Schema）。
     *
     * @param name        工具名
     * @param description 描述（必须写明是否改变外部状态——模型靠它判断该不该调用）
     * @param inputSchema 参数 JSON Schema
     */
    record ToolSpec(String name, String description, String inputSchema) {
    }

    /**
     * 模型请求的工具调用。
     *
     * @param id        调用 ID（模型生成）
     * @param name      工具名
     * @param arguments 参数 JSON 字符串
     */
    record ModelToolCall(String id, String name, String arguments) {
    }

    /**
     * 流式监听器。
     */
    interface Listener {

        /**
         * 模型增量输出（单线程串行回调）。
         *
         * @param delta 入参：增量文本
         */
        void onChunk(String delta);

        /**
         * 模型请求调用工具（替代 onComplete；本轮无文本输出时不会先有 onChunk）。
         *
         * @param calls 入参：工具调用列表
         */
        default void onToolCalls(List<ModelToolCall> calls) {
        }

        /**
         * 用量上报（结束时回调一次；拿不到时以 0 上报）。
         *
         * @param inputTokens  入参：输入 tokens
         * @param outputTokens 入参：输出 tokens
         */
        default void onUsage(long inputTokens, long outputTokens) {
        }

        /**
         * 正常完成（在最后一个 onChunk 之后回调一次）。
         */
        void onComplete();

        /**
         * 异常终止（onError 后不再有其他回调）。
         *
         * @param error 入参：异常
         */
        void onError(Throwable error);
    }

}
