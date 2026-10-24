package com.lemon.lemonagent.servicecore.conn.impl;

import com.lemon.lemonagent.dal.entity.McpConnectionEntity;
import com.lemon.lemonagent.dal.repository.McpConnectionRepository;
import com.lemon.lemonagent.model.enums.ConnectionStatus;
import com.lemon.lemonagent.model.enums.TransportType;
import com.lemon.lemonagent.model.mcpconn.McpConnectionData;
import com.lemon.lemonagent.servicecore.conn.McpConnectionService;
import com.lemon.lemonagent.util.crypto.CryptoUtil;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * MCP 连接领域服务实现。
 *
 * <p>平台公共连接（userId 为 NULL）对所有用户可见但不可改删——服务端的保护方式是
 * "自建连接一律按 (id, userId) 双键定位"，平台连接不属于任何用户，天然不在可操作集合里，
 * 不依赖"先查再判断 editable"的顺序（少一个可漏的分支）。</p>
 */
@Service
public class McpConnectionServiceImpl implements McpConnectionService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private final McpConnectionRepository repository;

    public McpConnectionServiceImpl(McpConnectionRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<McpConnectionData> list(Long userId) {
        List<McpConnectionData> result = new ArrayList<>();
        repository.findPlatform().forEach(e -> result.add(toData(e)));
        repository.findByUser(userId).forEach(e -> result.add(toData(e)));
        return result;
    }

    @Override
    public McpConnectionData create(Long userId, String name, String transportType,
                                    String url, String command, String secret) {
        TransportType type = validate(name, transportType, url, command);
        McpConnectionEntity entity = new McpConnectionEntity();
        entity.setUserId(userId);
        entity.setName(name.trim());
        entity.setTransportType(type.name());
        entity.setUrl(type.needsUrl() ? trimOrNull(url) : null);
        entity.setCommand(type.needsCommand() ? trimOrNull(command) : null);
        entity.setSecretCipher(isBlank(secret) ? null : CryptoUtil.encrypt(secret));
        entity.setStatus(ConnectionStatus.ENABLED.getCode());
        entity.setEditable(1);
        entity.setPlatformPublic(0);
        entity.setUnavailableReason(null);
        entity.setVersion(1);
        repository.insert(entity);
        return toData(entity);
    }

    @Override
    public McpConnectionData update(Long userId, Long id, String name, String transportType,
                                    String url, String command, String secret, Boolean enabled) {
        McpConnectionEntity entity = requireOwned(userId, id);
        TransportType type = validate(name, transportType, url, command);
        entity.setName(name.trim());
        entity.setTransportType(type.name());
        entity.setUrl(type.needsUrl() ? trimOrNull(url) : null);
        entity.setCommand(type.needsCommand() ? trimOrNull(command) : null);
        if (secret != null && !secret.isBlank()) {
            entity.setSecretCipher(CryptoUtil.encrypt(secret));
        }
        if (enabled != null) {
            entity.setStatus(enabled ? ConnectionStatus.ENABLED.getCode() : ConnectionStatus.DISABLED.getCode());
        }
        entity.setUnavailableReason(null);
        repository.update(entity);
        return toData(entity);
    }

    @Override
    public void delete(Long userId, Long id) {
        requireOwned(userId, id);
        repository.delete(id, userId);
    }

    private McpConnectionEntity requireOwned(Long userId, Long id) {
        McpConnectionEntity entity = repository.findByIdAndUser(id, userId);
        if (entity == null) {
            throw new BusinessException(ResultCodes.NOT_FOUND, "MCP 连接不存在（平台公共连接不可修改）");
        }
        return entity;
    }

    private TransportType validate(String name, String transportType, String url, String command) {
        if (isBlank(name)) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "连接名称不能为空");
        }
        TransportType type;
        try {
            type = TransportType.valueOf(transportType == null ? "" : transportType.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "传输类型必须是 SSE / STDIO / STREAMABLE 之一");
        }
        if (type.needsUrl() && isBlank(url)) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, type.name() + " 类型必须填写 HTTP 端点");
        }
        if (type.needsCommand() && isBlank(command)) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "STDIO 类型必须填写启动命令");
        }
        return type;
    }

    private McpConnectionData toData(McpConnectionEntity entity) {
        McpConnectionData data = new McpConnectionData();
        data.setId(entity.getId());
        data.setUserId(entity.getUserId());
        data.setName(entity.getName());
        data.setTransportType(entity.getTransportType());
        data.setUrl(entity.getUrl());
        data.setCommand(entity.getCommand());
        data.setHasSecret(entity.getSecretCipher() != null && !entity.getSecretCipher().isBlank());
        boolean enabled = entity.getStatus() != null && entity.getStatus() == 1;
        data.setEnabled(enabled);
        data.setEditable(entity.getEditable() != null && entity.getEditable() == 1);
        data.setPlatformPublic(entity.getPlatformPublic() != null && entity.getPlatformPublic() == 1);
        data.setUsable(enabled && entity.getUnavailableReason() == null);
        data.setUnavailableReason(entity.getUnavailableReason());
        data.setVersion(entity.getVersion());
        data.setCreatedAt(format(entity.getCreatedAt()));
        data.setUpdatedAt(format(entity.getUpdatedAt()));
        return data;
    }

    private static String trimOrNull(String s) {
        return isBlank(s) ? null : s.trim();
    }

    private static String format(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC).format(ISO);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

}
