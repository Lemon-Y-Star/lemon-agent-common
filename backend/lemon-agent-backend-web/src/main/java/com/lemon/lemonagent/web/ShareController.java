package com.lemon.lemonagent.web;

import com.lemon.lemonagent.dal.entity.AgentShareEntity;
import com.lemon.lemonagent.model.runtime.ShareInfoData;
import com.lemon.lemonagent.servicecore.runtime.share.ShareService;
import com.lemon.architecture.model.BaseResult;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Agent 分享（v4.6.0）：免登录公开对话页。
 *
 * <p>两组端点：{@code /workspace/agents/{id}/share}（登录，owner 管理开关/口令）；
 * {@code /share/{code}/**}（免登录，WebConfig 放行——校验走 share_code + 可选口令 + 频控）。</p>
 */
@RestController
public class ShareController {

    @Resource
    private ShareService shareService;

    // ---------- 登录态：owner 管理分享 ----------

    @PostMapping("/api/v1/workspace/agents/{agentId}/share")
    public BaseResult<Map<String, Object>> setShare(@PathVariable Long agentId,
                                                    @RequestAttribute("lemon.userId") Long userId,
                                                    @RequestBody Map<String, Object> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("enabled")));
        String accessCode = body.get("accessCode") == null ? null : String.valueOf(body.get("accessCode"));
        if (accessCode != null && accessCode.length() > 12) {
            accessCode = accessCode.substring(0, 12);
        }
        AgentShareEntity share = shareService.setShare(userId, agentId, enabled, accessCode);
        return BaseResult.success(Map.of(
                "enabled", share.getEnabled() != null && share.getEnabled() == 1,
                "shareCode", share.getShareCode() == null ? "" : share.getShareCode(),
                "accessCode", share.getAccessCode() == null ? "" : share.getAccessCode()));
    }

    @GetMapping("/api/v1/workspace/agents/{agentId}/share")
    public BaseResult<Map<String, Object>> getShare(@PathVariable Long agentId,
                                                    @RequestAttribute("lemon.userId") Long userId) {
        AgentShareEntity share = shareService.getShare(userId, agentId);
        if (share == null) {
            return BaseResult.success(Map.of("enabled", false, "shareCode", "", "accessCode", ""));
        }
        return BaseResult.success(Map.of(
                "enabled", share.getEnabled() != null && share.getEnabled() == 1,
                "shareCode", share.getShareCode(),
                "accessCode", share.getAccessCode() == null ? "" : share.getAccessCode()));
    }

    // ---------- 免登录：公开对话页（AuthInterceptor 对 /api/v1/share/** 放行） ----------

    /** 分享页信息（Agent 门面：名称/描述/开场白/建议问题）。 */
    @GetMapping("/api/v1/share/{shareCode}/info")
    public BaseResult<ShareInfoData> info(@PathVariable String shareCode,
                                          @RequestParam(value = "accessCode", required = false) String accessCode) {
        return BaseResult.success(shareService.publicInfo(shareCode, accessCode));
    }

    /** 公开发起运行：频控 + 口令校验后以主人身份建分享会话与运行。 */
    @PostMapping("/api/v1/share/{shareCode}/runs")
    public BaseResult<Map<String, Object>> run(@PathVariable String shareCode,
                                               @RequestBody Map<String, Object> body,
                                               HttpServletRequest request) {
        String message = String.valueOf(body.getOrDefault("message", ""));
        String accessCode = body.get("accessCode") == null ? null : String.valueOf(body.get("accessCode"));
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) {
            ip = request.getRemoteAddr();
        }
        return BaseResult.success(shareService.publicRun(shareCode, accessCode, message, ip));
    }

    /** 公开 SSE 事件流：run 必须属于该分享的会话（share_id 关系校验）。 */
    @GetMapping(value = "/api/v1/share/{shareCode}/runs/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable String shareCode,
                             @PathVariable Long runId,
                             @RequestParam(value = "afterEventId", required = false) Long afterEventId,
                             @RequestParam(value = "accessCode", required = false) String accessCode,
                             @RequestParam(value = "shareId", required = false) Long shareIdHint) {
        return shareService.publicSubscribe(shareCode, runId, afterEventId, null);
    }
}
