package com.lemon.lemonagent.servicecore.runtime.artifact;

import com.lemon.lemonagent.dal.entity.RunArtifactEntity;
import com.lemon.lemonagent.dal.repository.RunArtifactRepository;
import com.lemon.lemonagent.model.runtime.RunArtifactData;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 运行文件产物（v4.4.4）：沙箱生成的文件落库、列表与下载。
 *
 * <p>与 {@link ArtifactService}（已完成运行列表）互补：那个是「运行级产物」，
 * 这个是「运行内的文件」。内容直存 BLOB，归属校验内聚在仓储层。</p>
 */
@Service
public class RunArtifactService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private final RunArtifactRepository repository;

    public RunArtifactService(RunArtifactRepository repository) {
        this.repository = repository;
    }

    /**
     * 保存沙箱产物文件（base64 解码后落库）。
     *
     * @param userId      入参：归属用户
     * @param runId       入参：归属运行
     * @param name        入参：文件名
     * @param contentType 入参：MIME 类型
     * @param contentB64  入参：base64 内容
     * @return 出参：产物 ID
     */
    public long save(Long userId, Long runId, String name, String contentType, String contentB64) {
        byte[] data = Base64.getDecoder().decode(contentB64);
        RunArtifactEntity e = new RunArtifactEntity();
        e.setRunId(runId);
        e.setUserId(userId);
        e.setName(name);
        e.setContentType(contentType);
        e.setSizeBytes(data.length);
        e.setData(data);
        return repository.insert(e);
    }

    /**
     * 运行的产物元数据列表。
     *
     * @param userId 入参：归属用户
     * @param runId  入参：运行 ID
     * @return 出参：产物列表
     */
    public List<RunArtifactData> list(Long userId, Long runId) {
        List<RunArtifactData> items = new ArrayList<>();
        for (RunArtifactEntity e : repository.findByRun(runId, userId)) {
            RunArtifactData d = new RunArtifactData();
            d.setId(e.getId());
            d.setName(e.getName());
            d.setContentType(e.getContentType());
            d.setSizeBytes(e.getSizeBytes());
            d.setCreatedAt(e.getCreatedAt() == null ? null
                    : e.getCreatedAt().atOffset(ZoneOffset.UTC).format(ISO));
            items.add(d);
        }
        return items;
    }

    /**
     * 加载产物内容（下载用；无权限返回 null）。
     *
     * @param userId 入参：归属用户
     * @param id     入参：产物 ID
     * @return 出参：产物实体（含内容）
     */
    public RunArtifactEntity load(Long userId, Long id) {
        return repository.findByIdAndUser(id, userId);
    }

}
