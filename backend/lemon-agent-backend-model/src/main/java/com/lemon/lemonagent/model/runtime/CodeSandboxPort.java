package com.lemon.lemonagent.model.runtime;

import java.util.List;

/**
 * 代码沙箱端口（v4.4，PRD FR-6.8）。
 *
 * <p>能力对标市面竞品（Dify sandbox / FastGPT sandbox / Coze 代码运行器）：
 * 用户/模型提交 Python 源码 → 隔离容器受限执行 → 返回 stdout/stderr。
 * 安全由沙箱侧保证（禁网 + rlimit + 超时 + 非 root），本端口只负责契约。</p>
 */
public interface CodeSandboxPort {

    /**
     * 执行一段 Python 代码。
     *
     * @param code           入参：Python 源码
     * @param upstream       入参：上游节点产出（执行图 CODE 节点用；沙箱注入为代码内 {@code upstream} 列表变量；
     *                       v4.5.3 输入变量绑定时传 {@code null}，改用 {@code vars} 字典）
     * @param vars           入参：绑定变量字典（v4.5.3，{别名: 产出}，沙箱注入为代码内 {@code vars} 字典变量）；
     *                       可空 = 不注入
     * @param timeoutSeconds 入参：超时秒数（沙箱侧再封顶 15s）
     * @return 出参：执行结果（stdout/stderr/退出码/耗时/超时与截断标记）
     * @throws Exception 网络/服务异常（调用方折算为业务错误，不透出堆栈）
     */
    ExecResult exec(String code, java.util.List<String> upstream,
                    java.util.Map<String, String> vars, int timeoutSeconds) throws Exception;

    /** 沙箱执行结果。 */
    record ExecResult(
            String stdout,
            String stderr,
            int exitCode,
            long durationMs,
            boolean timedOut,
            boolean truncated,
            List<FileRef> files) {

        /** 兼容旧 JSON（无 files 字段）反序列化。 */
        public ExecResult {
            if (files == null) {
                files = List.of();
            }
        }

        /** 沙箱回收的文件（png/csv/html）：contentB64 为 base64 内容。 */
        public record FileRef(String name, String contentType, String contentB64) {
        }

        /** 拼装为给模型/正文看的紧凑文本（stdout 为主，stderr 附后）。 */
        public String asText() {
            StringBuilder sb = new StringBuilder(stdout == null ? "" : stdout.strip());
            if (stderr != null && !stderr.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append('\n');
                }
                sb.append("[stderr] ").append(stderr.strip());
            }
            if (timedOut) {
                sb.append("\n[终止] 代码执行超时，被沙箱强制结束");
            }
            return sb.toString();
        }
    }
}
