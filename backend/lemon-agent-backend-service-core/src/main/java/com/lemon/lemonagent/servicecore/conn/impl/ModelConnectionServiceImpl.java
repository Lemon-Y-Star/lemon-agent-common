package com.lemon.lemonagent.servicecore.conn.impl;

import com.lemon.lemonagent.dal.entity.ModelConnectionEntity;
import com.lemon.lemonagent.dal.repository.ModelConnectionRepository;
import com.lemon.lemonagent.model.enums.ConnectionStatus;
import com.lemon.lemonagent.model.modelconn.ModelConnectionData;
import com.lemon.lemonagent.model.modelconn.ModelConnectionsData;
import com.lemon.lemonagent.servicecore.conn.ModelConnectionService;
import com.lemon.lemonagent.util.crypto.CryptoUtil;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * 模型连接领域服务实现。
 *
 * <p>两条关键规则：① baseUrl 的主机必须在白名单内（防止把用户连接指到内网地址做 SSRF 跳板，
 * 白名单即 PRD 实测返回里的 {@code allowedHosts}）；② apiKey 只在写入时加密、读取时解出
 * 尾 4 位做脱敏展示，任何接口都不回传明文或密文。</p>
 */
@Service
public class ModelConnectionServiceImpl implements ModelConnectionService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private final ModelConnectionRepository repository;

    private final List<String> allowedHosts;

    private final List<String> auxiliaryPurposes;

    public ModelConnectionServiceImpl(ModelConnectionRepository repository,
                                      @Value("${lemon.model-conn.allowed-hosts}") String allowedHosts,
                                      @Value("${lemon.model-conn.auxiliary-purposes}") String auxiliaryPurposes) {
        this.repository = repository;
        this.allowedHosts = Arrays.stream(allowedHosts.split(",")).map(String::trim).toList();
        this.auxiliaryPurposes = Arrays.stream(auxiliaryPurposes.split(",")).map(String::trim).toList();
    }

    @Override
    public ModelConnectionsData list(Long userId) {
        ModelConnectionsData data = new ModelConnectionsData();
        data.setSecretsAvailable(true);
        data.setAllowedHosts(allowedHosts);
        data.setAuxiliaryProviderSource("PLATFORM");
        data.setAuxiliaryPurposes(auxiliaryPurposes);
        data.setAllowPlatformAuxiliaries(true);
        data.setConnections(repository.findByUser(userId).stream().map(this::toData).toList());
        return data;
    }

    @Override
    public ModelConnectionData create(Long userId, String name, String provider, String baseUrl,
                                      String apiKey, List<String> models, boolean allowAuxiliary, boolean enabled) {
        validate(name, provider, baseUrl, apiKey);
        ModelConnectionEntity entity = new ModelConnectionEntity();
        entity.setUserId(userId);
        entity.setName(name.trim());
        entity.setProvider(provider.trim());
        entity.setBaseUrl(baseUrl.trim());
        entity.setApiKeyCipher(CryptoUtil.encrypt(apiKey));
        entity.setModelsJson(toJson(models));
        entity.setAllowAuxiliary(allowAuxiliary ? 1 : 0);
        entity.setStatus(enabled ? ConnectionStatus.ENABLED.getCode() : ConnectionStatus.DISABLED.getCode());
        repository.insert(entity);
        return toData(entity);
    }

    @Override
    public ModelConnectionData update(Long userId, Long id, String name, String provider, String baseUrl,
                                      String apiKey, List<String> models, Boolean allowAuxiliary, Boolean enabled) {
        validate(name, provider, baseUrl, apiKey);
        ModelConnectionEntity entity = requireOwned(userId, id);
        entity.setName(name.trim());
        entity.setProvider(provider.trim());
        entity.setBaseUrl(baseUrl.trim());
        if (apiKey != null && !apiKey.isBlank()) {
            // 密钥轮换：仅在传了新值时覆盖；空值表示保留原密钥（改名字/端点不强制重填密钥）
            entity.setApiKeyCipher(CryptoUtil.encrypt(apiKey));
        }
        entity.setModelsJson(toJson(models));
        if (allowAuxiliary != null) {
            entity.setAllowAuxiliary(allowAuxiliary ? 1 : 0);
        }
        if (enabled != null) {
            entity.setStatus(enabled ? ConnectionStatus.ENABLED.getCode() : ConnectionStatus.DISABLED.getCode());
        }
        repository.update(entity);
        return toData(entity);
    }

    @Override
    public void delete(Long userId, Long id) {
        requireOwned(userId, id);
        repository.delete(id, userId);
    }

    private ModelConnectionEntity requireOwned(Long userId, Long id) {
        ModelConnectionEntity entity = repository.findByIdAndUser(id, userId);
        if (entity == null) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "模型连接不存在或已被删除");
        }
        return entity;
    }

    private void validate(String name, String provider, String baseUrl, String apiKey) {
        if (isBlank(name) || isBlank(provider) || isBlank(baseUrl) || isBlank(apiKey)) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "名称、提供方、端点与 API 密钥均为必填");
        }
        String host = extractHost(baseUrl);
        if (host == null) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "端点地址不合法，请填写 http(s) 完整 URL");
        }
        if (!allowedHosts.contains(host)) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR,
                    "端点主机不在允许列表内：" + host + "，允许的主机：" + allowedHosts);
        }
    }

    private String extractHost(String baseUrl) {
        try {
            return new URI(baseUrl.trim()).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private ModelConnectionData toData(ModelConnectionEntity entity) {
        ModelConnectionData data = new ModelConnectionData();
        data.setId(entity.getId());
        data.setName(entity.getName());
        data.setProvider(entity.getProvider());
        data.setBaseUrl(entity.getBaseUrl());
        String cipher = entity.getApiKeyCipher();
        if (cipher != null && !cipher.isBlank()) {
            data.setHasSecret(true);
            String plain = CryptoUtil.decrypt(cipher);
            data.setSecretTail(plain.length() <= 4 ? "****"
                    : plain.substring(plain.length() - 4));
        }
        data.setModels(fromJson(entity.getModelsJson()));
        data.setEnabled(entity.getStatus() != null && entity.getStatus() == 1);
        data.setAllowAuxiliary(entity.getAllowAuxiliary() != null && entity.getAllowAuxiliary() == 1);
        data.setCreatedAt(format(entity.getCreatedAt()));
        data.setUpdatedAt(format(entity.getUpdatedAt()));
        return data;
    }

    private static String toJson(List<String> models) {
        if (models == null || models.isEmpty()) {
            return "[]";
        }
        // 模型名是受限字符集（字母数字 . - _ / :），逐个转义后手工拼数组足够安全，不必为此引入序列化器
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < models.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String m = models.get(i);
            if (m != null && !m.isBlank() && m.matches("[A-Za-z0-9._/:-]+")) {
                sb.append('"').append(m.trim()).append('"');
            }
        }
        if (sb.charAt(sb.length() - 1) == '[') {
            return "[]";
        }
        return sb.append(']').toString();
    }

    private static List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        String body = json.trim();
        if (body.startsWith("[")) {
            body = body.substring(1, body.length() - 1);
        }
        if (body.isBlank()) {
            return List.of();
        }
        return Arrays.stream(body.split(","))
                .map(s -> s.replace("\"", "").trim())
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private static String format(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC).format(ISO);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

}
