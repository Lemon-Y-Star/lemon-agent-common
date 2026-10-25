package com.lemon.lemonagent.web;

import com.lemon.lemonagent.dal.entity.UserAccountEntity;
import com.lemon.lemonagent.dal.repository.McpConnectionRepository;
import com.lemon.lemonagent.dal.repository.UserAccountRepository;
import com.lemon.architecture.model.BaseResult;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import jakarta.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端 MCP 管理（v3.9，FR-22 切片）：admin 编辑平台公共连接的名称 / 端点 / 启停，
 * 以及新增平台公共连接。
 *
 * <p>安全口径：secret_cipher 不读不改不下发（密文不出库）；管理端直改平台行
 * （{@code user_id IS NULL}），用户自建连接仍走用户侧接口。</p>
 */
@RestController
@RequestMapping("/api/v1/admin/mcps")
public class AdminMcpController {

    /** 用户仓储（角色校验）。 */
    @Resource
    private UserAccountRepository userAccountRepository;

    /** MCP 仓储（平台连接列表复用）。 */
    @Resource
    private McpConnectionRepository mcpConnectionRepository;

    /** MySQL 直查（管理端平台行直改，主数据源）。 */
    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 新增 / 编辑请求体。 */
    public record AdminMcpReq(String name, String transportType, String url, String command, Integer status) {
    }

    /**
     * 平台公共连接列表（不含密文）。
     *
     * @param userId 当前用户（校验 admin 角色）
     * @return 列表
     */
    @GetMapping
    public BaseResult<List<Map<String, Object>>> list(@RequestAttribute("lemon.userId") Long userId) {
        requireAdmin(userId);
        List<Map<String, Object>> items = mcpConnectionRepository.findPlatform().stream()
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.getId());
                    m.put("name", e.getName());
                    m.put("transportType", e.getTransportType());
                    m.put("url", e.getUrl());
                    m.put("command", e.getCommand());
                    m.put("status", e.getStatus());
                    m.put("version", e.getVersion());
                    return m;
                })
                .toList();
        return BaseResult.success(items);
    }

    /**
     * 编辑平台公共连接（name / url / command / status；密文不动）。
     *
     * @param id     路径参数：连接 ID
     * @param request 编辑内容
     * @param userId 当前用户（校验 admin 角色）
     * @return 空
     */
    @PutMapping("/{id}")
    public BaseResult<Void> update(@PathVariable Long id,
                                   @RequestBody AdminMcpReq request,
                                   @RequestAttribute("lemon.userId") Long userId) {
        requireAdmin(userId);
        if (request.name() == null || request.name().isBlank()) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "名称不能为空");
        }
        int rows = jdbcTemplate.update(
                "UPDATE mcp_connection SET name = ?, url = ?, command = ?, status = ?, version = version + 1 "
                        + "WHERE id = ? AND user_id IS NULL",
                request.name().trim(), request.url(), request.command(),
                request.status() == null ? 1 : request.status(), id);
        if (rows == 0) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "平台公共连接不存在");
        }
        return BaseResult.success(null);
    }

    /**
     * 新增平台公共连接（不带密钥；需要鉴权的端点请预置密文或走迁移）。
     *
     * @param request 连接内容
     * @param userId  当前用户（校验 admin 角色）
     * @return 空
     */
    @PostMapping
    public BaseResult<Void> create(@RequestBody AdminMcpReq request,
                                   @RequestAttribute("lemon.userId") Long userId) {
        requireAdmin(userId);
        if (request.name() == null || request.name().isBlank() || request.url() == null || request.url().isBlank()) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "名称与端点不能为空");
        }
        String transport = request.transportType() == null || request.transportType().isBlank()
                ? "http" : request.transportType().trim();
        jdbcTemplate.update(
                "INSERT INTO mcp_connection (user_id, name, transport_type, url, command, secret_cipher, "
                        + "status, editable, platform_public, unavailable_reason, version, created_at, updated_at) "
                        + "VALUES (NULL, ?, ?, ?, ?, NULL, ?, 0, 1, NULL, 1, NOW(6), NOW(6))",
                request.name().trim(), transport, request.url().trim(), request.command(),
                request.status() == null ? 1 : request.status());
        return BaseResult.success(null);
    }

    /**
     * 删除平台公共连接（admin；物理删除，仅平台行）。
     *
     * @param id     路径参数：连接 ID
     * @param userId 当前用户（校验 admin 角色）
     * @return 空
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}")
    public BaseResult<Void> delete(@PathVariable Long id,
                                   @RequestAttribute("lemon.userId") Long userId) {
        requireAdmin(userId);
        int rows = jdbcTemplate.update(
                "DELETE FROM mcp_connection WHERE id = ? AND user_id IS NULL", id);
        if (rows == 0) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "平台公共连接不存在");
        }
        return BaseResult.success(null);
    }

    private void requireAdmin(Long userId) {
        UserAccountEntity user = userAccountRepository.findById(userId);
        if (user == null || !"admin".equals(user.getRole())) {
            throw new BusinessException(ResultCodes.FORBIDDEN, "仅管理员可访问");
        }
    }

}
