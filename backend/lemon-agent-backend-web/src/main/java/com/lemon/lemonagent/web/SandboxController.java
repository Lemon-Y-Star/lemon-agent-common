package com.lemon.lemonagent.web;

import com.lemon.architecture.model.BaseResult;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import com.lemon.lemonagent.model.runtime.CodeSandboxPort;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import com.lemon.lemonagent.servicecore.runtime.engine.RunExecutor;

/**
 * 代码沙箱（v4.4）：编辑器「试运行」直通端点。
 *
 * <p>执行图 CODE 节点保存前可先在此试跑看输出；生产执行链路
 * （run_python 工具 / CODE 节点）不经此端点，走 RunExecutor。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/sandbox")
public class SandboxController {

    /** 代码沙箱端口。 */
    @Resource
    private CodeSandboxPort codeSandbox;

    /**
     * 试运行一段 Python 代码（登录态即可，最长 10 秒）。
     *
     * @param userId 当前用户
     * @param req    入参：{code}
     * @return 出参：stdout/stderr/退出码/耗时
     */
    @PostMapping("/execute")
    public BaseResult<Map<String, Object>> execute(@RequestAttribute("lemon.userId") Long userId,
                                                   @RequestBody Map<String, String> req) {
        String code = req.getOrDefault("code", "");
        if (code.isBlank()) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "代码不能为空");
        }
        try {
            CodeSandboxPort.ExecResult r = codeSandbox.exec(code, List.of(), 10);
            List<String> images = (r.files() == null ? List.<CodeSandboxPort.ExecResult.FileRef>of() : r.files()).stream()
                    .filter(f -> f.contentType() != null && f.contentType().startsWith("image/"))
                    .map(f -> "data:" + f.contentType() + ";base64," + f.contentB64())
                    .toList();
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            out.put("stdout", r.stdout());
            out.put("stderr", r.stderr());
            out.put("exitCode", r.exitCode());
            out.put("durationMs", r.durationMs());
            out.put("timedOut", r.timedOut());
            out.put("truncated", r.truncated());
            out.put("images", images);
            return BaseResult.success(out);
        } catch (Exception e) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "沙箱不可用：" + e.getMessage());
        }
    }
}
