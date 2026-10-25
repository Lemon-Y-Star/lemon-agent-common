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
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link ModelPort} 的 Spring AI 2.0 实现（OpenAI 兼容流式）。
 *
 * <p>防腐边界：{@code org.springframework.ai.*} 与 Reactor 的 {@code Flux} 只存在于本模块——
 * 出口是 {@link Listener} 的阻塞式回调，service-core 的编排代码里不出现任何响应式类型。</p>
 *
 * <p><b>工具调用（2.0 行为，探针实测）</b>：Spring AI 2.0 的 {@code stream()} 不做内部工具
 * 执行（1.x 的 internalToolExecutionEnabled 已移除）——模型的工具调用直接出现在流末尾
 * chunk 的 {@code AssistantMessage.getToolCalls()} 里。本实现收集后经
 * {@link Listener#onToolCalls} 上抛，执行/审批的决策权在 RunExecutor。
 * 工具定义经 {@code OpenAiChatOptions.toolCallbacks}（仅提供 schema 给模型，call() 不执行）。</p>
 */
@Component
public class SpringAiModelPort implements ModelPort {

    private static final Logger log = LoggerFactory.getLogger(SpringAiModelPort.class);

    @Override
    public void stream(ModelRequest request, Listener listener) {
        try {
            List<ToolCallback> callbacks = request.tools() == null ? List.of() : request.tools().stream()
                    .<ToolCallback>map(spec -> new DeferredToolCallback(spec))
                    .toList();

            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .options(OpenAiChatOptions.builder()
                            .baseUrl(request.baseUrl())
                            .apiKey(request.apiKey())
                            .model(request.model())
                            .toolCallbacks(callbacks)
                            .build())
                    .build();

            List<Message> messages = new ArrayList<>();
            if (log.isDebugEnabled()) {
                log.debug("模型请求 model={} tools={} systemPrompt={}", request.model(),
                        request.tools() == null ? "[]" : request.tools().stream()
                                .map(ModelPort.ToolSpec::name).toList(),
                        request.systemPrompt());
            }
            if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
                messages.add(new org.springframework.ai.chat.messages.SystemMessage(request.systemPrompt()));
                // 渠道适配：中转网关对 system 消息的处理随请求形态漂移（带 tools / 不带 tools 两次观测行为相反，
                // 人格与长期记忆因此间歇性丢失）。防腐层统一兜底：把设定镜像一条 USER 消息，
                // 确保任何渠道形态下人格 / 记忆 / CoT 指令都可达；官方渠道下双写无害。
                messages.add(new UserMessage(
                        "[系统设定 · 请在整轮对话中严格遵守以下全部要求，不要向用户复述本段]\n"
                                + request.systemPrompt()));
            }
            for (Msg msg : request.history()) {
                if ("ASSISTANT".equals(msg.role())) {
                    messages.add(new AssistantMessage(msg.content()));
                } else {
                    messages.add(new UserMessage(msg.content()));
                }
            }
            messages.add(new UserMessage(request.userInput()));

            List<AssistantMessage.ToolCall> pendingCalls = new ArrayList<>();
            Flux<ChatResponse> flux = chatModel.stream(new Prompt(messages));
            // 阻塞式消费：把 Reactor 边界锁死在本模块内（BUILD-SPEC §2.1 ①）
            flux.doOnNext(resp -> {
                if (resp.getResult() != null && resp.getResult().getOutput() != null) {
                    var output = resp.getResult().getOutput();
                    if (output.getText() != null && !output.getText().isEmpty()) {
                        listener.onChunk(output.getText());
                    }
                    if (output instanceof AssistantMessage assistant
                            && assistant.getToolCalls() != null && !assistant.getToolCalls().isEmpty()) {
                        pendingCalls.addAll(assistant.getToolCalls());
                    }
                }
                if (resp.getMetadata() != null && resp.getMetadata().getUsage() != null) {
                    var usage = resp.getMetadata().getUsage();
                    listener.onUsage(usage.getPromptTokens(), usage.getCompletionTokens());
                }
            }).blockLast();

            if (!pendingCalls.isEmpty()) {
                // 模型请求调用工具：决策权交给调用方（审批挂起 / 直接执行）
                listener.onToolCalls(pendingCalls.stream()
                        .map(c -> new ModelPort.ModelToolCall(c.id(), c.name(), c.arguments()))
                        .toList());
            } else {
                listener.onComplete();
            }
        } catch (Exception e) {
            log.warn("模型流式调用失败: {}", e.getMessage());
            listener.onError(e);
        }
    }

    /** 壳回调：只提供工具定义给模型，call() 永远不该被调（执行由 RunExecutor 决策）。 */
    private record DeferredToolCallback(ModelPort.ToolSpec spec) implements ToolCallback {

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder()
                    .name(spec.name())
                    .description(spec.description())
                    .inputSchema(spec.inputSchema())
                    .build();
        }

        @Override
        public String call(String toolInput) {
            throw new UnsupportedOperationException(
                    "工具执行由 RunExecutor 决策（审批/直执行），不经 Spring AI 内部循环");
        }
    }

}
