package com.lemon.lemonagent.web;

import com.lemon.lemonagent.dal.entity.SensitiveWordEntity;
import com.lemon.lemonagent.dal.entity.UserAccountEntity;
import com.lemon.lemonagent.dal.repository.SensitiveWordRepository;
import com.lemon.lemonagent.dal.repository.UserAccountRepository;
import com.lemon.lemonagent.servicecore.safety.SensitiveWordService;
import com.lemon.architecture.model.BaseResult;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 敏感词管理（v3.9.2，FR-22 切片）：admin 维护平台级词库，输入拦截与输出脱敏共用。
 */
@RestController
@RequestMapping("/api/v1/admin/sensitive-words")
public class AdminSensitiveWordController {

    /** 用户仓储（角色校验）。 */
    @Resource
    private UserAccountRepository userAccountRepository;

    /** 敏感词领域服务。 */
    @Resource
    private SensitiveWordService sensitiveWordService;

    /** 敏感词仓储（列表）。 */
    @Resource
    private SensitiveWordRepository sensitiveWordRepository;

    /** 新增 / 删除请求体。 */
    public record SensitiveWordReq(String word) {
    }

    /**
     * 词库列表。
     *
     * @param userId 当前用户（校验 admin 角色）
     * @return 列表
     */
    @GetMapping
    public BaseResult<List<Map<String, Object>>> list(@RequestAttribute("lemon.userId") Long userId) {
        requireAdmin(userId);
        List<Map<String, Object>> items = sensitiveWordService.list().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.getId());
                    m.put("word", e.getWord());
                    return m;
                })
                .toList();
        return BaseResult.success(items);
    }

    /**
     * 添加敏感词。
     *
     * @param request 词
     * @param userId  当前用户（校验 admin 角色）
     * @return 词实体
     */
    @PostMapping
    public BaseResult<SensitiveWordEntity> add(@RequestBody SensitiveWordReq request,
                                               @RequestAttribute("lemon.userId") Long userId) {
        requireAdmin(userId);
        if (request.word() == null || request.word().isBlank()) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "敏感词不能为空");
        }
        return BaseResult.success(sensitiveWordService.add(request.word()));
    }

    /**
     * 删除敏感词。
     *
     * @param id     路径参数：词 ID
     * @param userId 当前用户（校验 admin 角色）
     * @return 空
     */
    @DeleteMapping("/{id}")
    public BaseResult<Void> delete(@PathVariable Long id,
                                   @RequestAttribute("lemon.userId") Long userId) {
        requireAdmin(userId);
        sensitiveWordService.remove(id);
        return BaseResult.success(null);
    }

    private void requireAdmin(Long userId) {
        UserAccountEntity user = userAccountRepository.findById(userId);
        if (user == null || !"admin".equals(user.getRole())) {
            throw new BusinessException(ResultCodes.FORBIDDEN, "仅管理员可访问");
        }
    }

}
