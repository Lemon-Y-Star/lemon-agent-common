package com.lemon.lemonagent.model.runtime;

import java.util.List;

/**
 * 模型调用防腐端口（BUILD-SPEC §2.3）：纯领域接口，零框架依赖。
 *
 * <p>{@code org.springframework.ai.*} 的类型不允许出现在 service-core / model 里——
 * 换模型 SDK / 换版本时只改 integration 的实现类。流式回调由实现层保证在
 * 单线程内串行回调（调用方负责攒批与落库顺序）。</p>
 */
public interface ModelPort {

    /**
     * 发起一次流式对话补全。
     *
     * @param request  入参：模型端点、凭据与上下文
     * @param listener 入参：增量 / 用量 / 完成 / 异常四类回调（实现层保证各自只调一次，onChunk 可多次）
     */
    void stream(ModelRequest request, Listener listener);

    /**
     * 模型请求：OpenAI 兼容端点 + 会话上下文。
     *
     * @param baseUrl      兼容端点（如 https://api.deepseek.com/v1）
     * @param apiKey       解密后的 API Key（明文仅在内存中存在）
     * @param model        模型名
     * @param systemPrompt 系统提示词
     * @param history      历史消息（按时间升序，role: USER/ASSISTANT）
     * @param userInput    本轮用户输入
     */
    record ModelRequest(String baseUrl, String apiKey, String model,
                        String systemPrompt, List<Msg> history, String userInput) {
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
