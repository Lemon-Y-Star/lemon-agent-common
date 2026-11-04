package com.lemon.lemonagent.servicecore.runtime.agent;

import com.lemon.lemonagent.dal.entity.ModelConnectionEntity;
import com.lemon.lemonagent.dal.repository.ModelConnectionRepository;
import com.lemon.lemonagent.model.modelconn.ModelCompareItemData;
import com.lemon.lemonagent.model.runtime.ModelPort;
import com.lemon.lemonagent.util.crypto.CryptoUtil;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 多模型对比（v4.8，用户自建连接并排比较）：同一问题并发发给选中的启用连接，
 * 汇总回答正文 / 耗时 / token 用量 / 失败原因。
 *
 * <p>设计：每个连接一个虚拟线程独立攒批（复用 PromptOptimizeService 的同步调用口径），
 * 单个失败或超时不影响其他连接；结果按请求传入的连接顺序返回。</p>
 */
@Service
public class ModelCompareService {

    private static final Logger log = LoggerFactory.getLogger(ModelCompareService.class);

    /** 同时对比的连接数上限：回答都是长输出，太多既慢又费钱。 */
    private static final int MAX_CONNECTIONS = 4;

    /** 单个模型超时（秒）：对比问题的正常回答 10-40s，120s 覆盖慢速渠道。 */
    private static final int TIMEOUT_SECONDS = 120;

    /** 单项回答长度上限（与运行时截断口径一致）。 */
    private static final int MAX_CONTENT_LEN = 8000;

    /** 默认系统提示词（对比场景不带 Agent 人设，公平起见所有模型同一起跑线）。 */
    private static final String DEFAULT_SYSTEM_PROMPT = "你是 AI 助手，简洁准确地回答用户问题。";

    private final ModelConnectionRepository modelConnectionRepository;

    private final ModelPort modelPort;

    public ModelCompareService(ModelConnectionRepository modelConnectionRepository,
                               ObjectProvider<ModelPort> modelPortProvider) {
        this.modelConnectionRepository = modelConnectionRepository;
        this.modelPort = modelPortProvider.getIfAvailable();
    }

    /**
     * 并发对比：选中的每个启用连接各发一次同步调用，全部结束后统一返回。
     *
     * @param userId        入参：归属用户
     * @param query         入参：对比问题
     * @param connectionIds 入参：连接 ID 列表（结果按此顺序）
     * @param systemPrompt  入参：可选系统提示词
     * @return 出参：逐连接结果
     */
    public List<ModelCompareItemData> compare(Long userId, String query,
                                              List<Long> connectionIds, String systemPrompt) {
        if (modelPort == null) {
            throw new BusinessException(ResultCodes.SYSTEM_ERROR, "模型端口未就绪");
        }
        if (query == null || query.isBlank()) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "对比问题不能为空");
        }
        if (connectionIds == null || connectionIds.isEmpty()) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "请至少选择一个模型连接");
        }
        if (connectionIds.size() > MAX_CONNECTIONS) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "最多同时对比 " + MAX_CONNECTIONS + " 个模型");
        }
        // 只允许自己的启用连接（归属校验不通过按参数错误提示，避免被用来探测他人连接）
        Map<Long, ModelConnectionEntity> enabled = modelConnectionRepository.findByUser(userId).stream()
                .filter(c -> c.getStatus() != null && c.getStatus() == 1)
                .collect(Collectors.toMap(ModelConnectionEntity::getId, Function.identity()));
        List<ModelConnectionEntity> selected = new ArrayList<>();
        for (Long id : connectionIds) {
            ModelConnectionEntity conn = enabled.get(id);
            if (conn == null) {
                throw new BusinessException(ResultCodes.VALIDATION_ERROR,
                        "连接 #" + id + " 不存在或未启用，请重新选择");
            }
            selected.add(conn);
        }
        // 去重保序（同一连接选两次只会比出两份一样的回答，纯浪费）
        if (selected.size() != connectionIds.stream().distinct().count()) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "连接选择有重复，请检查");
        }

        String sysPrompt = (systemPrompt == null || systemPrompt.isBlank())
                ? DEFAULT_SYSTEM_PROMPT : systemPrompt.trim();
        long start = System.currentTimeMillis();
        List<ModelCompareItemData> items = new ArrayList<>();
        // 虚拟线程并发：单连接失败/超时只体现在自己的 error 字段，不拖垮整批
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<ModelCompareItemData>> futures = selected.stream()
                    .map(conn -> pool.submit(() -> callOnce(conn, sysPrompt, query.trim())))
                    .toList();
            for (Future<ModelCompareItemData> f : futures) {
                items.add(f.get()); // callOnce 内部有超时兜底，这里不会无限阻塞
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCodes.SYSTEM_ERROR, "对比被中断");
        } catch (Exception e) {
            log.warn("模型对比汇聚失败：{}", e.getMessage());
            throw new BusinessException(ResultCodes.SYSTEM_ERROR, "模型对比执行失败：" + e.getMessage());
        }
        log.info("模型对比完成：{} 个连接，总耗时 {}ms", items.size(), System.currentTimeMillis() - start);
        return items;
    }

    /** 单连接同步调用（stream 攒批 + 超时兜底 + think/围栏剥除），失败写 error 不抛出。 */
    private ModelCompareItemData callOnce(ModelConnectionEntity conn, String systemPrompt, String query) {
        ModelCompareItemData item = new ModelCompareItemData();
        item.setConnectionId(conn.getId());
        item.setName(conn.getName());
        String model = firstModel(conn);
        item.setModel(model);
        long start = System.currentTimeMillis();
        try {
            String apiKey = CryptoUtil.decrypt(conn.getApiKeyCipher());
            ModelPort.ModelRequest request = new ModelPort.ModelRequest(
                    conn.getBaseUrl(), apiKey, model, systemPrompt, List.of(), query, List.of());

            StringBuilder output = new StringBuilder();
            AtomicReference<Throwable> error = new AtomicReference<>();
            AtomicLong inputTokens = new AtomicLong();
            AtomicLong outputTokens = new AtomicLong();
            CountDownLatch done = new CountDownLatch(1);
            modelPort.stream(request, new ModelPort.Listener() {
                @Override
                public void onChunk(String delta) {
                    if (output.length() < MAX_CONTENT_LEN) {
                        output.append(delta);
                    }
                }

                @Override
                public void onUsage(long in, long out) {
                    inputTokens.set(in);
                    outputTokens.set(out);
                }

                @Override
                public void onError(Throwable t) {
                    error.set(t);
                    done.countDown();
                }

                @Override
                public void onComplete() {
                    done.countDown();
                }
            });
            if (!done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                item.setError("超时（" + TIMEOUT_SECONDS + "s 未返回）");
                item.setLatencyMs(System.currentTimeMillis() - start);
                return item;
            }
            if (error.get() != null) {
                item.setError("调用失败：" + (error.get().getMessage() == null
                        ? error.get().toString() : error.get().getMessage()));
                item.setLatencyMs(System.currentTimeMillis() - start);
                return item;
            }
            String content = stripThinkAndFence(output.toString().trim());
            item.setContent(content.isEmpty() ? null : content);
            if (content.isEmpty()) {
                item.setError("模型没有返回内容");
            }
            item.setLatencyMs(System.currentTimeMillis() - start);
            item.setInputTokens(inputTokens.get());
            item.setOutputTokens(outputTokens.get());
        } catch (Exception e) {
            item.setError("调用失败：" + (e.getMessage() == null ? e.toString() : e.getMessage()));
            item.setLatencyMs(System.currentTimeMillis() - start);
        }
        return item;
    }

    /** 思考模型会带 <think> 块、偶发包围栏：剥掉再入对比（口径同 PromptOptimizeService）。 */
    private static String stripThinkAndFence(String text) {
        int thinkStart = text.indexOf("<think>");
        if (thinkStart >= 0) {
            int thinkEnd = text.indexOf("</think>");
            text = (thinkEnd >= 0 ? text.substring(thinkEnd + "</think>".length()) : "").trim();
        }
        if (text.startsWith("```")) {
            text = text.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("```\\s*$", "").trim();
        }
        return text;
    }

    /** 连接的首选模型名：modelsJson 数组第一个（与 RunExecutor 口径一致）。 */
    private String firstModel(ModelConnectionEntity conn) {
        try {
            List<?> models = new tools.jackson.databind.ObjectMapper()
                    .readValue(conn.getModelsJson(), List.class);
            if (models != null && !models.isEmpty()) {
                return String.valueOf(models.get(0));
            }
        } catch (Exception ignored) {
            // modelsJson 为空/非法时回退 provider
        }
        return conn.getProvider();
    }
}
