package com.lemon.lemonagent.dal.repository;

import com.lemon.lemonagent.dal.entity.AgentMemoryEntity;
import com.lemon.lemonagent.dal.entity.KbImageEntity;
import com.lemon.lemonagent.dal.entity.KbMissEntity;
import com.lemon.lemonagent.dal.entity.KbDocumentEntity;

import java.util.List;

/** 知识库文档 + 长期记忆的仓储端口（两张小表合成一个端口，避免碎片化）。 */
public interface KnowledgeRepository {

    /** 文档列表（倒序）。 */
    List<KbDocumentEntity> listDocs(Long userId);

    /** 文档分页列表（v4.5.6，倒序）：列表页不再一次拉全量。 */
    List<KbDocumentEntity> listDocsPage(Long userId, int offset, int limit);

    /** 文档总数（v4.5.6 分页配套）。 */
    int countDocs(Long userId);

    /** 插入文档。 */
    int insertDoc(KbDocumentEntity entity);

    /** 删除本人文档。 */
    int deleteDoc(Long id, Long userId);

    /** 关键词检索（全文索引优先 + LIKE 兜底）。 */
    List<KbDocumentEntity> searchDocs(Long userId, String keyword, int limit);

    /** 未命中记录（v4.1）：0 命中 query 按 (user_id, query) 去重计数。 */
    void recordMiss(Long userId, String query);

    /** 未命中队列 Top N（按次数降序）。 */
    List<KbMissEntity> listMisses(Long userId, int limit);

    /** 未命中队列分页（v4.5.6，按次数降序）。 */
    List<KbMissEntity> listMissesPage(Long userId, int offset, int limit);

    /** 未命中总数。 */
    int countMisses(Long userId);

    /** 重置文档图片（先删后插，随文档创建写入）。 */
    void replaceImages(Long userId, Long docId, List<KbImageEntity> images);

    /** 按文档 ID 批量取图片。 */
    List<KbImageEntity> findImagesByDocs(List<Long> docIds);

    /** 按标题找文档 ID（向量召回只回传标题，图片经标题关联回带）。 */
    List<KbDocumentEntity> findByTitles(Long userId, List<String> titles);

    /** 按 ID 查文档（v4.5.2 知识库绑定归属校验用）；不存在返回 null。 */
    KbDocumentEntity findById(Long docId);

    /** 记忆列表（倒序）。 */
    List<AgentMemoryEntity> listMemories(Long userId);

    /** 生效记忆（供运行时注入）。 */
    List<AgentMemoryEntity> listEnabledMemories(Long userId, int limit);

    /** 插入记忆。 */
    int insertMemory(AgentMemoryEntity entity);

    /** 启用/停用记忆。 */
    int toggleMemory(Long id, Long userId, boolean enabled);

    /** 删除记忆。 */
    int deleteMemory(Long id, Long userId);

}
