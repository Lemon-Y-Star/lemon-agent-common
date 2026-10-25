package com.lemon.lemonagent.web;

import com.lemon.lemonagent.model.runtime.CreateKbDocReq;
import com.lemon.lemonagent.model.runtime.CreateKbDocResp;
import com.lemon.lemonagent.model.runtime.DeleteKbDocReq;
import com.lemon.lemonagent.model.runtime.DeleteKbDocResp;
import com.lemon.lemonagent.model.runtime.KbDocsData;
import com.lemon.lemonagent.model.runtime.KbSearchHit;
import com.lemon.lemonagent.servicecore.runtime.KnowledgeService;
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
     * 文档列表 + 检索能力。
     *
     * @param userId 当前用户
     * @return 列表
     */
    @GetMapping
    public BaseResult<KbDocsData> list(@RequestAttribute("lemon.userId") Long userId) {
        return BaseResult.success(knowledgeService.list(userId));
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
    public BaseResult<List<KbSearchHit>> search(@RequestParam("q") String q,
                                                @RequestParam(value = "topK", defaultValue = "5") int topK,
                                                @RequestAttribute("lemon.userId") Long userId) {
        return BaseResult.success(knowledgeService.search(userId, q, topK));
    }

}
