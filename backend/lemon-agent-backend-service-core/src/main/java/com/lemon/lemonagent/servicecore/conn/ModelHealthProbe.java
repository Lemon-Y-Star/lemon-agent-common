package com.lemon.lemonagent.servicecore.conn;

import com.lemon.lemonagent.dal.entity.ModelConnectionEntity;
import com.lemon.lemonagent.dal.repository.ModelConnectionRepository;
import com.lemon.lemonagent.model.runtime.ModelHealthData;
import com.lemon.lemonagent.model.runtime.ModelHealthPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 模型连接健康探测编排（v4.1，对标 FastGPT 模型健康监控）：
 * 定时对全部启用连接探活（出站 HTTP 走 {@link ModelHealthPort} 防腐端口，本类只做编排与落库），
 * health_status / latency / 时间三列落库。
 *
 * <p>探测是「尽力而为」的可用性信号，不是配额校验：404/401 都算 fail——
 * 用户看到红点就该去检查连接，具体报因在对话运行时的 MODEL_ERROR 里有更细的口径。</p>
 */
@org.springframework.stereotype.Component
public class ModelHealthProbe {

    private static final Logger log = LoggerFactory.getLogger(ModelHealthProbe.class);

    private final ModelConnectionRepository repository;

    private final ModelHealthPort healthPort;

    public ModelHealthProbe(ModelConnectionRepository repository, ModelHealthPort healthPort) {
        this.repository = repository;
        this.healthPort = healthPort;
    }

    /** 定时探测（默认 5 分钟一轮，启动 30s 后首跑）。 */
    @Scheduled(fixedDelayString = "${lemon.health.probe-interval-ms:300000}",
            initialDelayString = "${lemon.health.initial-delay-ms:30000}")
    public void probeAll() {
        int ok = 0;
        int fail = 0;
        for (ModelConnectionEntity conn : repository.findAllEnabled()) {
            try {
                if (probe(conn)) {
                    ok++;
                } else {
                    fail++;
                }
            } catch (Exception e) {
                log.debug("健康探测异常 conn={}: {}", conn.getId(), e.getMessage());
                fail++;
            }
        }
        if (ok + fail > 0) {
            log.info("模型连接健康探测完成：ok={} fail={}", ok, fail);
        }
    }

    /**
     * 立即探测某用户的全部启用连接（前端「立即检测」按钮 / MODEL_HEALTH_CHECK 命令用）。
     *
     * @param userId 入参：用户
     * @return 出参：逐条结果
     */
    public List<ModelHealthData> probeUser(Long userId) {
        List<ModelHealthData> out = new ArrayList<>();
        for (ModelConnectionEntity conn : repository.findByUser(userId)) {
            if (conn.getStatus() == null || conn.getStatus() != 1) {
                continue;
            }
            boolean ok = probe(conn);
            ModelHealthData item = new ModelHealthData();
            item.setConnectionId(conn.getId());
            item.setName(conn.getName());
            item.setHealthStatus(ok ? "ok" : "fail");
            item.setHealthLatencyMs(conn.getHealthLatencyMs());
            out.add(item);
        }
        return out;
    }

    /** 单连接探测：ok 落时延，fail 落状态。 */
    private boolean probe(ModelConnectionEntity conn) {
        if (conn.getApiKeyCipher() == null || conn.getBaseUrl() == null || conn.getBaseUrl().isBlank()) {
            repository.saveHealth(conn.getId(), "fail", null);
            return false;
        }
        ModelHealthPort.ProbeResult result = healthPort.probe(conn.getBaseUrl(),
                com.lemon.lemonagent.util.crypto.CryptoUtil.decrypt(conn.getApiKeyCipher()));
        repository.saveHealth(conn.getId(), result.ok() ? "ok" : "fail", result.latencyMs());
        return result.ok();
    }

}
