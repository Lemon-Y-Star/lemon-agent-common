package com.lemon.lemonagent.web;

import com.lemon.lemonagent.model.runtime.CreateKbDocReq;
import com.lemon.lemonagent.model.runtime.CreateKbDocResp;
import com.lemon.lemonagent.model.runtime.DeleteKbDocReq;
import com.lemon.lemonagent.model.runtime.DeleteKbDocResp;
import com.lemon.lemonagent.model.runtime.KbDocumentData;
import com.lemon.lemonagent.model.runtime.KbDocsData;
import com.lemon.lemonagent.model.runtime.KbMissesData;
import com.lemon.lemonagent.model.runtime.KbSearchData;
import com.lemon.lemonagent.servicecore.runtime.knowledge.KnowledgeService;
import com.lemon.architecture.model.BaseResult;
import com.lemon.architecture.service.util.opt.OptUtil;
import com.lemon.architecture.util.validation.AssertUtils;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 我的知识库（v3.6）：文档 CRUD + 检索测试。
 *
 * <p>检索在页面里可直接试：没配 embedding 时走关键词降级，命中会标 `keyword` / `vector` 来源。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/knowledge")
public class KnowledgeController {

    @Resource
    private OptUtil optUtil;

    @Resource
    private KnowledgeService knowledgeService;

    /**
     * 文档列表（v4.5.6 分页）。
     *
     * @param userId 当前用户
     * @param page   页码（1 起）
     * @param size   页大小（默认 20）
     * @return 当前页 + 总数
     */
    @GetMapping
    public BaseResult<KbDocsData> list(@RequestAttribute("lemon.userId") Long userId,
                                       @RequestParam(value = "page", defaultValue = "1") int page,
                                       @RequestParam(value = "size", defaultValue = "20") int size) {
        return BaseResult.success(knowledgeService.list(userId, page, size));
    }

    /**
     * 文档详情（v4.5.6）：弹窗看全文（Markdown 渲染，多模态图链随文展示）。
     *
     * @param docId  文档 ID
     * @param userId 当前用户
     * @return 文档全文
     */
    @GetMapping("/{docId}")
    public BaseResult<KbDocumentData> detail(@PathVariable Long docId,
                                             @RequestAttribute("lemon.userId") Long userId) {
        return BaseResult.success(knowledgeService.detail(userId, docId));
    }

    /**
     * 新增文档。
     *
     * @param request 文档
     * @param userId  当前用户
     * @param source  调用来源
     * @return 新文档
     */
    @PostMapping
    public CreateKbDocResp create(@RequestBody CreateKbDocReq request,
                                  @RequestAttribute("lemon.userId") Long userId,
                                  @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new CreateKbDocResp(), req -> {
            AssertUtils.notBlank(req.getTitle(), "标题不能为空");
            AssertUtils.notBlank(req.getContent(), "正文不能为空");
        });
    }

    /**
     * 删除文档。
     *
     * @param docId  文档 ID
     * @param userId 当前用户
     * @param source 调用来源
     * @return 空
     */
    @DeleteMapping("/{docId}")
    public DeleteKbDocResp delete(@PathVariable Long docId,
                                  @RequestAttribute("lemon.userId") Long userId,
                                  @RequestHeader(value = "X-Source", defaultValue = "web") String source) {
        DeleteKbDocReq request = new DeleteKbDocReq();
        request.setDocId(docId);
        request.setUserId(userId);
        request.fillRoute(source);
        return optUtil.exec(request, new DeleteKbDocResp(), req -> AssertUtils.notNull(req.getDocId(), "文档 ID 不能为空"));
    }

    /**
     * 检索测试（关键词优先，向量可用时叠加）。
     *
     * @param q      查询
     * @param topK   条数
     * @param userId 当前用户
     * @return 命中
     */
    @GetMapping("/search")
    public BaseResult<KbSearchData> search(@RequestParam("q") String q,
                                           @RequestParam(value = "topK", defaultValue = "5") int topK,
                                           @RequestAttribute("lemon.userId") Long userId) {
        return BaseResult.success(knowledgeService.searchDetailed(userId, q, topK));
    }

    /**
     * 检索未命中队列（v4.1 运营闭环；v4.5.6 分页）：0 命中 query 按频次降序，指导补文档。
     *
     * @param userId 当前用户
     * @param page   页码（1 起）
     * @param size   页大小（默认 10）
     * @return 队列 + 总数
     */
    @GetMapping("/misses")
    public BaseResult<KbMissesData> misses(@RequestAttribute("lemon.userId") Long userId,
                                           @RequestParam(value = "page", defaultValue = "1") int page,
                                           @RequestParam(value = "size", defaultValue = "10") int size) {
        return BaseResult.success(knowledgeService.misses(userId, page, size));
    }

    /**
     * AI 补文档草稿（v4.6.0 标注闭环延伸）：kb_miss/标注查询 → LLM 生成候选答案草稿，
     * 前端展示可编辑，确认后走 POST /workspace/knowledge 入库。
     *
     * @param body   入参：{"query": "未命中查询/标注问题"}
     * @param userId 归属用户
     * @return 出参：{"draft": "Markdown 草稿"}
     */
    @PostMapping("/ai-draft")
    public BaseResult<Map<String, String>> aiDraft(@RequestBody Map<String, String> body,
                                                   @RequestAttribute("lemon.userId") Long userId) {
        String draft = knowledgeService.aiDraft(userId, body.get("query"));
        return BaseResult.success(Map.of("draft", draft));
    }

}
