package com.lemon.lemonagent.integration.model;

import com.lemon.lemonagent.model.runtime.ModelPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link ModelPort} 的 Spring AI 2.0 实现（OpenAI 兼容流式）。
 *
 * <p>防腐边界：{@code org.springframework.ai.*} 与 Reactor 的 {@code Flux} 只存在于本模块——
 * 出口是 {@link Listener} 的阻塞式回调（实现内部 {@code blockingForEach} 消费流），
 * service-core 的编排代码里不出现任何响应式类型（BUILD-SPEC §2.1 ①）。</p>
 *
 * <p>Spring AI 2.0 的变化：模型按请求构造（每次运行用用户自己的 BYOK 连接 + 模型名，
 * builder 的 {@code options(...)} 里直接给 baseUrl / apiKey，无需预建全局 Bean）。
 * 每次运行 new 一个 ChatModel 的成本可忽略（底层 HTTP 客户端轻量），换来的是连接级隔离。</p>
 */
@Component
public class SpringAiModelPort implements ModelPort {

    private static final Logger log = LoggerFactory.getLogger(SpringAiModelPort.class);

    @Override
    public void stream(ModelRequest request, Listener listener) {
        try {
            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .options(OpenAiChatOptions.builder()
                            .baseUrl(request.baseUrl())
                            .apiKey(request.apiKey())
                            .model(request.model())
                            .build())
                    .build();

            List<Message> messages = new ArrayList<>();
            if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
                messages.add(new org.springframework.ai.chat.messages.SystemMessage(request.systemPrompt()));
            }
            for (Msg msg : request.history()) {
                if ("ASSISTANT".equals(msg.role())) {
                    messages.add(new AssistantMessage(msg.content()));
                } else {
                    messages.add(new UserMessage(msg.content()));
                }
            }
            messages.add(new UserMessage(request.userInput()));

            Flux<ChatResponse> flux = chatModel.stream(new Prompt(messages));
            // 阻塞式消费：把 Reactor 边界锁死在本模块内（BUILD-SPEC §2.3）
            flux.doOnNext(resp -> {
                if (resp.getResult() != null && resp.getResult().getOutput() != null
                        && resp.getResult().getOutput().getText() != null) {
                    listener.onChunk(resp.getResult().getOutput().getText());
                }
                if (resp.getMetadata() != null && resp.getMetadata().getUsage() != null) {
                    var usage = resp.getMetadata().getUsage();
                    listener.onUsage(usage.getPromptTokens(), usage.getCompletionTokens());
                }
            }).doOnComplete(listener::onComplete)
              .doOnError(listener::onError)
              .blockLast();
            // blockLast 正常返回时 onComplete 已由 doOnComplete 触发；异常时 doOnError 触发并上抛
        } catch (Exception e) {
            // 流中途异常（网络断 / 鉴权失败 / 模型 4xx-5xx）统一走 onError，不再向外抛
            log.warn("模型流式调用失败: {}", e.getMessage());
            listener.onError(e);
        }
    }

}
