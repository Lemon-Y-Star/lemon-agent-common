package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.lemonagent.dal.entity.ApiKeyEntity;
import com.lemon.lemonagent.dal.repository.ApiKeyRepository;
import com.lemon.lemonagent.model.runtime.ApiKeyCreatedData;
import com.lemon.lemonagent.model.runtime.ApiKeyData;
import com.lemon.lemonagent.model.runtime.ApiKeysData;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * API 分发密钥（v3.8，PRD FR-24 最小版）。
 *
 * <p>口径与平台密钥一致：库里只存 SHA-256 哈希，明文只在创建响应里出现一次；
 * 列表只给前缀（sk- + 前 8 位），吊销即失效。鉴权在开放端点（{@code /api/v1/open/**}）
 * 里做——哈希查库 + 状态启用，命中后以密钥归属用户的身份发起运行。</p>
 */
@Service
public class ApiKeyService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    /**
     * 创建密钥。
     *
     * @param userId 入参：用户
     * @param name   入参：名称
     * @return 出参：含一次性明文
     */
    public ApiKeyCreatedData create(Long userId, String name) {
        String title = name == null ? "" : name.trim();
        if (title.isEmpty()) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "密钥名称不能为空");
        }
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        String key = "sk-" + HexFormat.of().formatHex(bytes);
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setUserId(userId);
        entity.setName(title);
        entity.setKeyPrefix(key.substring(0, 11));
        entity.setKeyHash(sha256(key));
        apiKeyRepository.insert(entity);

        ApiKeyCreatedData data = new ApiKeyCreatedData();
        data.setId(entity.getId());
        data.setName(title);
        data.setKey(key);
        return data;
    }

    /**
     * 密钥列表（不含哈希/明文）。
     *
     * @param userId 入参：用户
     * @return 出参：列表
     */
    public ApiKeysData list(Long userId) {
        List<ApiKeyData> items = new ArrayList<>();
        for (ApiKeyEntity e : apiKeyRepository.findByUser(userId)) {
            ApiKeyData item = new ApiKeyData();
            item.setId(e.getId());
            item.setName(e.getName());
            item.setKeyPrefix(e.getKeyPrefix());
            item.setEnabled(e.getStatus() != null && e.getStatus() == 1);
            item.setLastUsedAt(e.getLastUsedAt() == null ? null : e.getLastUsedAt().atOffset(ZoneOffset.UTC).format(ISO));
            item.setCreatedAt(e.getCreatedAt() == null ? null : e.getCreatedAt().atOffset(ZoneOffset.UTC).format(ISO));
            items.add(item);
        }
        ApiKeysData data = new ApiKeysData();
        data.setKeys(items);
        return data;
    }

    /**
     * 吊销（本人密钥）。
     *
     * @param userId 入参：用户
     * @param keyId  入参：密钥 ID
     */
    public void revoke(Long userId, Long keyId) {
        int rows = apiKeyRepository.revoke(keyId, userId);
        if (rows == 0) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "密钥不存在");
        }
    }

    /**
     * 开放端点鉴权：明文 → 哈希查库 → 必须启用；命中返回密钥实体（取归属用户）。
     *
     * @param rawKey 入参：明文密钥（sk-…）
     * @return 出参：密钥实体
     * @throws BusinessException 缺失 / 无效 / 已吊销
     */
    public ApiKeyEntity authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new BusinessException(ResultCodes.UNAUTHORIZED, "缺少 API 密钥（Authorization: Bearer sk-… 或 X-Api-Key）");
        }
        ApiKeyEntity entity = apiKeyRepository.findByHash(sha256(rawKey.trim()));
        if (entity == null) {
            throw new BusinessException(ResultCodes.UNAUTHORIZED, "API 密钥无效");
        }
        if (entity.getStatus() == null || entity.getStatus() != 1) {
            throw new BusinessException(ResultCodes.FORBIDDEN, "API 密钥已吊销");
        }
        apiKeyRepository.touch(entity.getId());
        return entity;
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

}
