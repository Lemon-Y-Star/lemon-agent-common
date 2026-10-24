package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.dal.entity.AgentRunEventEntity;
import com.lemon.lemonagent.dal.repository.AgentRunEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 事件发布器：seq 分配 → 落库 → 推送（BUILD-SPEC 铁律「先落库，再推送」）。
 *
 * <p>三件事：
 * ① {@link #append} 在 per-run 锁内分配连续 seq（内存计数 + DB UNIQUE(run_id,seq) 兜底），
 * 事件落库后推给该 run 的全部在线 emitter；
 * ② {@link #subscribe} 按契约 §4.7 的 8 步处理订阅：重放 → 终态即完 → 注册 → ★补一轮
 * （消除「重放完 → 注册前」的空档）；
 * ③ 心跳：15 秒给全部 emitter 发 {@code : ping} 注释帧（不落库、不占 seq）。</p>
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final AgentRunEventRepository eventRepository;

    private final com.lemon.lemonagent.dal.repository.AgentRunRepository runRepository;

    /** runId → 在线 emitter 列表（一个 run 允许多个浏览器标签页同时订阅）。 */
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** runId → 已分配的最大 seq（进程内计数；DB UNIQUE(run_id,seq) 是兜底闸）。 */
    private final Map<Long, AtomicLong> seqCounters = new ConcurrentHashMap<>();

    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public EventPublisher(AgentRunEventRepository eventRepository,
                          com.lemon.lemonagent.dal.repository.AgentRunRepository runRepository) {
        this.eventRepository = eventRepository;
        this.runRepository = runRepository;
        heartbeat.scheduleAtFixedRate(this::pingAll, 15, 15, TimeUnit.SECONDS);
    }

    /**
     * 追加事件：锁内分配 seq → 落库 → 推送在线订阅者。
     *
     * @param runId     入参：运行 ID
     * @param eventType 入参：事件类型（线格式名）
     * @param payload   入参：负载 JSON 字符串
     * @return 出参：分配的 seq
     */
    public long append(Long runId, String eventType, String payload) {
        AtomicLong counter = seqCounters.computeIfAbsent(runId, k -> new AtomicLong(0));
        long seq;
        synchronized (counter) {
            seq = counter.incrementAndGet();
            AgentRunEventEntity event = new AgentRunEventEntity();
            event.setRunId(runId);
            event.setSeq(seq);
            event.setEventType(eventType);
            event.setPayload(payload);
            // UNIQUE(run_id,seq) 冲突只可能发生在进程重启恢复边界（计数器晚于 DB），重试一次即可
            int rows = eventRepository.insert(event);
            if (rows == 0) {
                seq = counter.incrementAndGet();
                event.setSeq(seq);
                eventRepository.insert(event);
            }
            // last_seq 回写：快照 / 列表 / 刷新恢复都以它为续流起点（契约 4.4/4.6）
            runRepository.updateLastSeq(runId, seq);
        }
        publish(runId, seq, eventType, payload);
        return seq;
    }

    /**
     * 注册 run 的 seq 计数起点（run 创建时调用：run_started 占 seq=1）。
     *
     * @param runId   入参：运行 ID
     * @param startSeq 入参：初始 seq（创建即 1）
     */
    public void initCounter(Long runId, long startSeq) {
        seqCounters.put(runId, new AtomicLong(startSeq));
    }

    /**
     * 订阅运行事件流（契约 §4.7 的 8 步处理顺序）。
     *
     * @param runId       入参：运行 ID
     * @param cursor      入参：重放游标（afterEventId / Last-Event-ID 取大者）
     * @param isFinal     入参：run 是否已终态（终态重放完立即 complete）
     * @param onNextEvent 入参：订阅方拿到新事件的回调（本方法内部在锁外异步调用）
     * @return 出参：SSE emitter（已注册）
     */
    public SseEmitter subscribe(Long runId, long cursor, boolean isFinal, Consumer<AgentRunEventEntity> onNextEvent) {
        SseEmitter emitter = new SseEmitter(0L);
        long[] lastSent = {cursor};

        // 重放：seq > cursor 逐条写
        List<AgentRunEventEntity> replay = eventRepository.findByRunAfter(runId, cursor);
        List<AgentRunEventEntity> pending = new java.util.ArrayList<>(replay);
        if (isFinal) {
            // 终态：重放完立即 complete，不注册
            replay.forEach(e -> send(emitter, e));
            emitter.complete();
            return emitter;
        }

        Runnable registerAndDrain = () -> {
            List<SseEmitter> list = emitters.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>());
            list.add(emitter);
            // ★补一轮：再查 seq > lastSent 的事件，消除「重放完 → 注册前」的空档
            List<AgentRunEventEntity> missed = eventRepository.findByRunAfter(runId, lastSent[0]);
            pending.clear();
            pending.addAll(missed);
            for (AgentRunEventEntity e : pending) {
                if (e.getSeq() > lastSent[0]) {
                    send(emitter, e);
                    lastSent[0] = e.getSeq();
                    onNextEvent.accept(e);
                }
            }
        };
        registerAndDrain.run();

        Runnable cleanup = () -> {
            List<SseEmitter> list = emitters.get(runId);
            if (list != null) {
                list.remove(emitter);
            }
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        return emitter;
    }

    private void publish(Long runId, long seq, String eventType, String payload) {
        List<SseEmitter> list = emitters.get(runId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().id(String.valueOf(seq)).name(eventType).data(payload));
            } catch (Exception e) {
                log.debug("SSE 推送失败（客户端断开），等待清理回调移除: runId={}", runId);
            }
        }
    }

    private void send(SseEmitter emitter, AgentRunEventEntity e) {
        try {
            emitter.send(SseEmitter.event().id(String.valueOf(e.getSeq()))
                    .name(e.getEventType()).data(e.getPayload()));
        } catch (IOException ex) {
            log.debug("SSE 重放写帧失败: runId={} seq={}", e.getRunId(), e.getSeq());
        }
    }

    private void pingAll() {
        emitters.forEach((runId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    // 心跳失败不处理，等待容器侧超时/错误回调清理
                }
            }
        });
    }

}
