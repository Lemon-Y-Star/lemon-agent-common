package com.lemon.lemonagent.servicecore.runtime.agent;

import com.lemon.lemonagent.dal.entity.ModelConnectionEntity;
import com.lemon.lemonagent.dal.repository.ModelConnectionRepository;
import com.lemon.lemonagent.model.runtime.ModelPort;
import com.lemon.lemonagent.util.crypto.CryptoUtil;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 提示词 AI 优化器（v4.5，Dify Prompt Generator 同款能力）：
 * 把用户写的粗糙 instruction 交给模型改写为结构化提示词（角色/任务/约束/输出格式四段式）。
 *
 * <p>设计：复用运行时的模型防腐端口与连接解析口径（用户最新启用连接），
 * 一次同步调用（stream 攒批），失败直接抛业务异常——不做降级续写。</p>
 */
@Service
public class PromptOptimizeService {

    private static final Logger log = LoggerFactory.getLogger(PromptOptimizeService.class);

    /** 单次优化超时（秒）：提示词改写是小输出任务，60s 足够覆盖慢速渠道。 */
    private static final int OPTIMIZE_TIMEOUT_SECONDS = 60;

    /** 优化后的提示词长度上限（与 AgentConfigService 的 instruction 校验口径一致）。 */
    private static final int MAX_OPTIMIZED_LEN = 4000;

    /** 优化器系统提示词：只输出改写结果，不输出解释。 */
    private static final String SYSTEM_PROMPT = """
            你是一名资深提示词工程师。用户会给你一段为 AI Agent 编写的系统提示词（instruction），
            请把它改写为结构清晰、可执行性强的版本，遵循以下规则：
            1. 保留原意与全部业务要求，不新增虚构的能力或工具；
            2. 按「角色定位 / 任务说明 / 行为约束 / 输出要求」组织（可省略不需要的小节）；
            3. 明确、具体、无歧义：把模糊表述改为可判定的规则；
            4. 语言与原文一致（中文原文输出中文）；
            5. 只输出改写后的提示词正文，禁止任何解释、前后缀或 markdown 代码块围栏；
            6. 长度控制在 1000 字以内。
            """;

    private final ModelConnectionRepository modelConnectionRepository;

    private final ModelPort modelPort;

    public PromptOptimizeService(ModelConnectionRepository modelConnectionRepository,
                                 ObjectProvider<ModelPort> modelPortProvider) {
        this.modelConnectionRepository = modelConnectionRepository;
        this.modelPort = modelPortProvider.getIfAvailable();
    }

    /**
     * 优化提示词：用户最新启用连接发一次同步调用。
     *
     * @param userId      入参：归属用户
     * @param instruction 入参：原始提示词
     * @return 出参：优化后的提示词
     */
    public String optimize(Long userId, String instruction) {
        if (modelPort == null) {
            throw new BusinessException(ResultCodes.SYSTEM_ERROR, "模型端口未就绪");
        }
        if (instruction == null || instruction.isBlank()) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "提示词不能为空");
        }
        ModelConnectionEntity conn = modelConnectionRepository.findByUser(userId).stream()
                .filter(c -> c.getStatus() != null && c.getStatus() == 1)
                .reduce((first, second) -> second)
                .orElse(null);
        if (conn == null) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "没有可用的模型连接，请先在模型连接页启用一个");
        }
        String apiKey = CryptoUtil.decrypt(conn.getApiKeyCipher());
        ModelPort.ModelRequest request = new ModelPort.ModelRequest(
                conn.getBaseUrl(), apiKey, firstModel(conn),
                SYSTEM_PROMPT, List.of(), instruction.trim(), List.of());

        StringBuilder output = new StringBuilder();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        long start = System.currentTimeMillis();
        modelPort.stream(request, new ModelPort.Listener() {
            @Override
            public void onChunk(String delta) {
                if (output.length() < MAX_OPTIMIZED_LEN) {
                    output.append(delta);
                }
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
        try {
            if (!done.await(OPTIMIZE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new BusinessException(ResultCodes.SYSTEM_ERROR, "提示词优化超时，请稍后重试");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCodes.SYSTEM_ERROR, "提示词优化被中断");
        }
        if (error.get() != null) {
            log.warn("提示词优化失败：{}", error.get().getMessage());
            throw new BusinessException(ResultCodes.SYSTEM_ERROR, "提示词优化失败：" + error.get().getMessage());
        }
        String optimized = output.toString().trim();
        // 思考模型会带 <think> 块：剥掉再返回（闭合/未闭合两种形态都处理）
        int thinkStart = optimized.indexOf("<think>");
        if (thinkStart >= 0) {
            int thinkEnd = optimized.indexOf("</think>");
            optimized = (thinkEnd >= 0 ? optimized.substring(thinkEnd + "</think>".length())
                    : "").trim();
        }
        // 模型偶发包围栏：剥掉最外层 ``` 围栏行
        if (optimized.startsWith("```")) {
            optimized = optimized.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("```\\s*$", "").trim();
        }
        if (optimized.isEmpty()) {
            throw new BusinessException(ResultCodes.SYSTEM_ERROR, "模型没有返回优化结果，请换一个模型连接重试");
        }
        return optimized;
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
