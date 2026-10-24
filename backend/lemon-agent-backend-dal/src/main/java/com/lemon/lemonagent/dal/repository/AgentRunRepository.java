package com.lemon.lemonagent.dal.repository;

import com.lemon.lemonagent.dal.entity.AgentRunEntity;

import java.util.List;

/** 运行仓储端口（状态机迁移全部走本端口的原子 UPDATE）。 */
public interface AgentRunRepository {

    /**
     * 插入运行（创建即 RUNNING），回填自增主键。
     *
     * @param entity 入参：待插入实体
     * @return 出参：影响行数
     */
    int insert(AgentRunEntity entity);

    /**
     * 幂等查询：同用户同 clientRequestId 的原 run。
     *
     * @param userId          入参：用户
     * @param clientRequestId 入参：幂等键
     * @return 出参：原运行；不存在返回 null
     */
    AgentRunEntity findByClientRequestId(Long userId, String clientRequestId);

    /**
     * 按主键查用户的运行。
     *
     * @param id     入参：运行 ID
     * @param userId 入参：归属用户
     * @return 出参：运行实体；不存在或不属于该用户返回 null
     */
    AgentRunEntity findByIdAndUser(Long id, Long userId);

    /**
     * 会话下是否存在活跃 run（RUNNING/BLOCKED）——会话互斥检查。
     *
     * @param sessionId 入参：会话 ID
     * @return 出参：活跃运行；无返回 null
     */
    AgentRunEntity findActiveBySession(Long sessionId);

    /**
     * 状态机原子迁移：仅当当前状态与期望一致时迁移。
     *
     * @param id       入参：运行 ID
     * @param from     入参：期望的当前状态
     * @param to       入参：目标状态
     * @param errorCode 入参：失败归因（可空）
     * @param errorMsg  入参：用户可读错误（可空）
     * @return 出参：影响行数（0 = 状态已变化，迁移被拒绝）
     */
    int transition(Long id, String from, String to, String errorCode, String errorMsg);

    /**
     * 结束运行：写终态、正文、用量与结束时间。
     *
     * @param id           入参：运行 ID
     * @param to           入参：终态
     * @param outputText   入参：最终正文（可空）
     * @param errorCode    入参：失败归因（可空）
     * @param errorMsg     入参：用户可读错误（可空）
     * @param inputTokens  入参：输入 tokens
     * @param outputTokens 入参：输出 tokens
     * @return 出参：影响行数
     */
    int finish(Long id, String to, String outputText, String errorCode, String errorMsg,
               Integer inputTokens, Integer outputTokens);

    /**
     * 会话下的全部运行（创建时间升序，契约 4.4 的 runs 快照）。
     *
     * @param sessionId 入参：会话 ID
     * @param userId    入参：归属用户
     * @return 出参：运行列表
     */
    List<AgentRunEntity> findBySession(Long sessionId, Long userId);

    /**
     * 用户运行列表（分页 + 可选状态过滤，创建时间倒序）。
     *
     * @param userId   入参：归属用户
     * @param status   入参：状态过滤（可空）
     * @param sessionId 入参：会话过滤（可空）
     * @param offset   入参：分页偏移
     * @param limit    入参：页大小
     * @return 出参：运行列表
     */
    List<AgentRunEntity> findByUser(Long userId, String status, Long sessionId, int offset, int limit);

    /**
     * 用户运行总数。
     *
     * @param userId   入参：归属用户
     * @param status   入参：状态过滤（可空）
     * @param sessionId 入参：会话过滤（可空）
     * @return 出参：总数
     */
    long countByUser(Long userId, String status, Long sessionId);

    /**
     * 回写事件序号游标（append 事务内调用；快照 / 续流起点以此为准）。
     *
     * @param id  入参：运行 ID
     * @param seq 入参：最新 seq
     * @return 出参：影响行数
     */
    int updateLastSeq(Long id, long seq);

    /**
     * 挂起（原子）：RUNNING→BLOCKED + 写审批快照，同一条 UPDATE 完成状态与快照的原子变更。
     *
     * @param id         入参：运行 ID
     * @param approvalId 入参：审批 ID
     * @param pendingJson 入参：审批快照 JSON
     * @return 出参：影响行数（0 = 状态已变化，放弃挂起）
     */
    int suspend(Long id, String approvalId, String pendingJson);

    /**
     * 清空审批快照（审批通过后调用）。
     *
     * @param id 入参：运行 ID
     * @return 出参：影响行数
     */
    int clearPendingApproval(Long id);

    /**
     * 保存计划快照与步骤游标（plan 策略）：计划生成后写一次，每步完成后覆盖一次。
     *
     * @param id       入参：运行 ID
     * @param planJson 入参：计划快照 JSON
     * @param planStep 入参：当前步骤下标（0 起）
     * @return 出参：影响行数
     */
    int savePlan(Long id, String planJson, int planStep);

    /**
     * 按状态查运行（审批超时扫描用）。
     *
     * @param status 入参：状态
     * @param limit  入参：上限
     * @return 出参：运行列表
     */
    List<AgentRunEntity> findByStatus(String status, int limit);

    /**
     * 进程重启的孤儿收口：把遗留的 RUNNING/BLOCKED run 标为 FAILED(INTERRUPTED)。
     *
     * @return 出参：受影响的运行数
     */
    int markOrphansAsInterrupted();

}
