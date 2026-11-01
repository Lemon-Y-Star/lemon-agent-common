package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.dal.entity.KbDocumentEntity;
import com.lemon.lemonagent.dal.entity.KbImageEntity;
import com.lemon.lemonagent.dal.entity.KbMissEntity;
import com.lemon.lemonagent.dal.repository.KnowledgeRepository;
import com.lemon.lemonagent.model.runtime.KbDocumentData;
import com.lemon.lemonagent.model.runtime.KbDocsData;
import com.lemon.lemonagent.model.runtime.KbMissesData;
import com.lemon.lemonagent.model.runtime.KbSearchData;
import com.lemon.lemonagent.model.runtime.KbSearchHit;
import com.lemon.lemonagent.model.runtime.RetrievalPort;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 知识库服务（v3.6 → v4.1）：文档管理 + 检索 + 未命中队列 + 多模态图片。
 *
 * <p><b>检索三级降级</b>：整串命中（全文索引优先 + LIKE 兜底）→ 分词 OR 匹配 → 向量叠加；
 * 配了 rerank 再对合并候选精排（重排失败降级原序，不报错）——
 * 降级不报错、升级不换接口。</p>
 *
 * <p><b>运营闭环（v4.1）</b>：最终 0 命中的 query 沉淀进 kb_miss（去重计数），
 * 知识库页展示未命中 Top N——检索准确率的提升靠「知道用户问了什么而库答不了什么」。</p>
 */
@Service
public class KnowledgeService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private static final int MAX_TITLE = 200;

    private static final int MAX_CONTENT = 20000;

    /** markdown 图片语法：![alt](url) */
    private static final Pattern MD_IMAGE = Pattern.compile("!\\[([^\\]]*)]\\(([^)\\s]+)\\)");

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
     * 新增文档；v4.1 顺带提取 markdown 图链入 kb_image（多模态随文带出）。
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
        repository.replaceImages(userId, e.getId(), extractImages(userId, content));
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
        repository.replaceImages(userId, docId, List.of());
    }

    /**
     * 检索（search_docs 工具用）：只要最终命中。
     *
     * @param userId 入参：用户
     * @param query  入参：查询
     * @param topK   入参：条数
     * @return 出参：命中
     */
    public List<KbSearchHit> search(Long userId, String query, int topK) {
        return searchDetailed(userId, query, topK).hits();
    }

    /**
     * 检索详细结果（检索测试页用）：三级降级 + 重排前后对比 + 图片随文带出 + 未命中沉淀。
     *
     * <p>合并策略：关键词命中（title ×2 / content ×1 打分）与向量召回按 title 去重合并；
     * rerank 可用时对合并候选精排取 topK（source=rerank），否则按原序截断。</p>
     *
     * @param userId 入参：用户
     * @param query  入参：查询
     * @param topK   入参：条数
     * @return 出参：hits + beforeRerank + reranked
     */
    public KbSearchData searchDetailed(Long userId, String query, int topK) {
        if (query == null || query.isBlank()) {
            return new KbSearchData(List.of(), null, false);
        }
        int limit = Math.min(Math.max(topK, 1), 10);
        List<String> terms = splitTerms(query);

        // ① 关键词路：整串命中（全文索引优先 + LIKE 兜底），再分词降级
        Map<String, KbDocumentEntity> merged = new java.util.LinkedHashMap<>();
        for (KbDocumentEntity e : repository.searchDocs(userId, query.trim(), limit)) {
            merged.put(e.getTitle(), e);
        }
        if (merged.isEmpty()) {
            for (KbDocumentEntity e : termSearch(userId, terms)) {
                merged.putIfAbsent(e.getTitle(), e);
            }
        }
        List<KbSearchHit> candidates = new ArrayList<>(attachImages(userId, merged,
                merged.values().stream().map(KbDocumentEntity::getTitle).toList(), terms));

        // ② 向量路叠加（可用时多召回一些作为重排候选池；不可用时 keyword+分词结果即全部）
        if (retrievalPort.available()) {
            int pool = retrievalPort.rerankAvailable() ? Math.min(limit * 3, 10) : limit;
            try {
                for (RetrievalPort.Hit hit : retrievalPort.search(query, pool)) {
                    if (!merged.containsKey(hit.title())) {
                        candidates.add(new KbSearchHit(hit.title(), hit.snippet(), "vector"));
                    }
                }
            } catch (Exception ignored) {
                // 向量路失败不拖垮关键词结果（降级不报错）
            }
        }

        // ③ 重排精排（可用且候选 > 1）：beforeRerank 留档对比；失败降级原序截断
        List<KbSearchHit> beforeRerank = null;
        boolean reranked = false;
        List<KbSearchHit> hits;
        if (retrievalPort.rerankAvailable() && candidates.size() > 1) {
            beforeRerank = candidates.stream().limit(limit).toList();
            try {
                List<RetrievalPort.Hit> raw = candidates.stream()
                        .map(h -> new RetrievalPort.Hit(h.title(), h.snippet(), 0)).toList();
                hits = retrievalPort.rerank(query, raw, limit).stream()
                        .map(h -> new KbSearchHit(h.title(), h.snippet(), "rerank",
                                imagesOf(userId, merged, h.title()))).toList();
                reranked = true;
            } catch (Exception e) {
                hits = candidates.stream().limit(limit).toList();
            }
        } else {
            hits = candidates.stream().limit(limit).toList();
        }

        // ④ 未命中沉淀（运营闭环）：三级降级全空才记
        if (hits.isEmpty()) {
            try {
                repository.recordMiss(userId, query.trim());
            } catch (Exception ignored) {
                // 沉淀失败不影响检索本身
            }
        }
        return new KbSearchData(hits, beforeRerank, reranked);
    }

    /**
     * 未命中队列 Top N（v4.1）。
     *
     * @param userId 入参：用户
     * @param limit  入参：条数
     * @return 出参：队列 + 总数
     */
    public KbMissesData misses(Long userId, int limit) {
        List<KbMissesData.MissItem> items = new ArrayList<>();
        for (KbMissEntity e : repository.listMisses(userId, Math.min(Math.max(limit, 1), 50))) {
            items.add(new KbMissesData.MissItem(e.getQuery(),
                    e.getHits() == null ? 0 : e.getHits(),
                    e.getLastSeenAt() == null ? null : e.getLastSeenAt().atOffset(ZoneOffset.UTC).format(ISO)));
        }
        return new KbMissesData(items, repository.countMisses(userId));
    }

    /** 分词检索：文档表是用户私有小集合（列表页 LIMIT 200），直接内存打分。 */
    private List<KbDocumentEntity> termSearch(Long userId, List<String> terms) {
        if (terms.isEmpty()) {
            return List.of();
        }
        record Scored(KbDocumentEntity doc, int score) {
        }
        List<Scored> scored = new ArrayList<>();
        for (KbDocumentEntity d : repository.listDocs(userId)) {
            String title = d.getTitle() == null ? "" : d.getTitle();
            String content = d.getContent() == null ? "" : d.getContent();
            int s = 0;
            for (String t : terms) {
                if (title.contains(t)) {
                    s += 2;
                }
                if (content.contains(t)) {
                    s += 1;
                }
            }
            if (s > 0) {
                scored.add(new Scored(d, s));
            }
        }
        scored.sort((a, b) -> a.score() != b.score()
                ? b.score() - a.score()
                : Long.compare(b.doc().getId(), a.doc().getId()));
        List<KbDocumentEntity> out = new ArrayList<>();
        for (Scored s : scored) {
            out.add(s.doc());
        }
        return out;
    }

    /** 分词：按空白拆、过滤过短（<2 字）与过长（>20 字）词、去重，最多 6 个。 */
    private static List<String> splitTerms(String query) {
        List<String> terms = new ArrayList<>();
        for (String raw : query.trim().split("\\s+")) {
            String t = raw.trim();
            if (t.length() >= 2 && t.length() <= 20 && !terms.contains(t)) {
                terms.add(t);
            }
            if (terms.size() == 6) {
                break;
            }
        }
        return terms;
    }

    /** 命中窗口 snippet：优先取首个命中词附近约 200 字的窗口，没有命中取正文开头。 */
    private static String snippet(String content, List<String> terms) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        if (content.length() <= 200) {
            return content;
        }
        int at = -1;
        for (String t : terms) {
            int i = content.indexOf(t);
            if (i >= 0 && (at == -1 || i < at)) {
                at = i;
            }
        }
        int from = Math.max(0, (at == -1 ? 0 : at) - 40);
        return content.substring(from, Math.min(content.length(), from + 200)) + "…";
    }

    /** 关键词路命中转 KbSearchHit（带窗口 snippet）。 */
    private List<KbSearchHit> attachImages(Long userId, Map<String, KbDocumentEntity> merged,
                                           List<String> titles, List<String> terms) {
        Map<String, List<String>> imageMap = imagesByTitle(userId, titles);
        List<KbSearchHit> out = new ArrayList<>();
        for (String title : titles) {
            KbDocumentEntity e = merged.get(title);
            if (e == null) {
                continue;
            }
            out.add(new KbSearchHit(e.getTitle(), snippet(e.getContent(), terms), "keyword",
                    imageMap.get(title)));
        }
        return out;
    }

    private List<String> imagesOf(Long userId, Map<String, KbDocumentEntity> merged, String title) {
        return imagesByTitle(userId, List.of(title)).getOrDefault(title, null);
    }

    /** 批量取图片，按文档标题分组（向量路只回传标题，图片经标题关联回带）。 */
    private Map<String, List<String>> imagesByTitle(Long userId, List<String> titles) {
        if (titles.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> titleToId = repository.findByTitles(userId, titles).stream()
                .collect(Collectors.toMap(KbDocumentEntity::getTitle, KbDocumentEntity::getId, (a, b) -> a));
        if (titleToId.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<String>> byDoc = repository.findImagesByDocs(new ArrayList<>(titleToId.values())).stream()
                .collect(Collectors.groupingBy(KbImageEntity::getDocId,
                        Collectors.mapping(KbImageEntity::getUrl, Collectors.toList())));
        Map<String, List<String>> out = new java.util.LinkedHashMap<>();
        titleToId.forEach((title, docId) -> {
            List<String> urls = byDoc.get(docId);
            if (urls != null && !urls.isEmpty()) {
                out.put(title, urls);
            }
        });
        return out;
    }

    /** 提取正文里的 markdown 图链（去重保序，最多 20 张）。 */
    private List<KbImageEntity> extractImages(Long userId, String content) {
        List<KbImageEntity> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        Matcher m = MD_IMAGE.matcher(content == null ? "" : content);
        while (m.find() && out.size() < 20) {
            String url = m.group(2);
            if (url.startsWith("http") && seen.add(url)) {
                KbImageEntity img = new KbImageEntity();
                img.setUserId(userId);
                img.setUrl(url);
                img.setAlt(m.group(1) == null || m.group(1).isBlank() ? null : m.group(1));
                out.add(img);
            }
        }
        return out;
    }

}
