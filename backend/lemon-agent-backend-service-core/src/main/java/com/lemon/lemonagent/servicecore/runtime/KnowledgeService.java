package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.dal.entity.KbDocumentEntity;
import com.lemon.lemonagent.dal.repository.KnowledgeRepository;
import com.lemon.lemonagent.model.runtime.KbDocumentData;
import com.lemon.lemonagent.model.runtime.KbDocsData;
import com.lemon.lemonagent.model.runtime.KbSearchHit;
import com.lemon.lemonagent.model.runtime.RetrievalPort;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库服务（v3.6）：文档管理 + 检索。
 *
 * <p><b>双路检索降级</b>：没配 embedding 时用 MySQL 关键词（LIKE）保证「搜得到、答得出」，
 * 配了 embedding 再叠加 PgVector 向量召回并去重——降级不报错、升级不换接口。</p>
 */
@Service
public class KnowledgeService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private static final int MAX_TITLE = 200;

    private static final int MAX_CONTENT = 20000;

    private final KnowledgeRepository repository;

    private final RetrievalPort retrievalPort;

    public KnowledgeService(KnowledgeRepository repository, RetrievalPort retrievalPort) {
        this.repository = repository;
        this.retrievalPort = retrievalPort;
    }

    /**
     * 文档列表。
     *
     * @param userId 入参：用户
     * @return 出参：列表 + 是否启用向量检索
     */
    public KbDocsData list(Long userId) {
        List<KbDocumentData> docs = new ArrayList<>();
        for (KbDocumentEntity e : repository.listDocs(userId)) {
            KbDocumentData d = new KbDocumentData();
            d.setId(e.getId());
            d.setTitle(e.getTitle());
            d.setContent(e.getContent());
            d.setCreatedAt(e.getCreatedAt() == null ? null : e.getCreatedAt().atOffset(ZoneOffset.UTC).format(ISO));
            docs.add(d);
        }
        KbDocsData data = new KbDocsData();
        data.setDocs(docs);
        data.setVectorEnabled(retrievalPort.available());
        return data;
    }

    /**
     * 新增文档。
     *
     * @param userId  入参：用户
     * @param title   入参：标题
     * @param content 入参：正文
     * @return 出参：新文档
     */
    public KbDocumentData create(Long userId, String title, String content) {
        if (title == null || title.isBlank() || title.length() > MAX_TITLE) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "标题不能为空且不超过 200 字");
        }
        if (content == null || content.isBlank() || content.length() > MAX_CONTENT) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "正文不能为空且不超过 20000 字");
        }
        KbDocumentEntity e = new KbDocumentEntity();
        e.setUserId(userId);
        e.setTitle(title.trim());
        e.setContent(content);
        repository.insertDoc(e);
        return list(userId).getDocs().stream()
                .filter(d -> d.getId().equals(e.getId())).findFirst().orElse(null);
    }

    /**
     * 删除文档。
     *
     * @param userId 入参：用户
     * @param docId  入参：文档
     */
    public void delete(Long userId, Long docId) {
        if (repository.deleteDoc(docId, userId) == 0) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "文档不存在");
        }
    }

    /**
     * 检索：关键词优先（永远可用），向量可用时叠加并去重。
     *
     * @param userId 入参：用户
     * @param query  入参：查询
     * @param topK   入参：条数
     * @return 出参：命中
     */
    public List<KbSearchHit> search(Long userId, String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int limit = Math.min(Math.max(topK, 1), 10);
        List<KbSearchHit> hits = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        for (KbDocumentEntity e : repository.searchDocs(userId, query.trim(), limit)) {
            String snippet = e.getContent() == null ? "" : e.getContent();
            hits.add(new KbSearchHit(e.getTitle(), snippet.length() > 200 ? snippet.substring(0, 200) + "…" : snippet,
                    "keyword"));
            seen.add(e.getTitle());
        }
        if (retrievalPort.available() && hits.size() < limit) {
            for (RetrievalPort.Hit hit : retrievalPort.search(query, limit)) {
                if (seen.add(hit.title())) {
                    hits.add(new KbSearchHit(hit.title(), hit.snippet(), "vector"));
                }
            }
        }
        return hits;
    }

}
