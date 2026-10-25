package com.lemon.lemonagent.dal.repository;

import com.lemon.lemonagent.dal.entity.AgentMemoryEntity;
import com.lemon.lemonagent.dal.entity.KbDocumentEntity;

import java.util.List;

/** 知识库文档 + 长期记忆的仓储端口（两张小表合成一个端口，避免碎片化）。 */
public interface KnowledgeRepository {

    /** 文档列表（倒序）。 */
    List<KbDocumentEntity> listDocs(Long userId);

    /** 插入文档。 */
    int insertDoc(KbDocumentEntity entity);

    /** 删除本人文档。 */
    int deleteDoc(Long id, Long userId);

    /** 关键词检索（标题或正文包含）。 */
    List<KbDocumentEntity> searchDocs(Long userId, String keyword, int limit);

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
