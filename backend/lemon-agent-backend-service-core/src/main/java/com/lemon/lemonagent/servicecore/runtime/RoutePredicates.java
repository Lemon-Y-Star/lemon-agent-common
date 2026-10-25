package com.lemon.lemonagent.servicecore.runtime;

import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;

import java.util.Locale;

/**
 * 条件路由谓词（v3.3，PRD FR-7.1 §④）：**预注册、可枚举、绝不求值任意表达式**。
 *
 * <p>为什么不用 SpEL/Aviator/JS：把"配置"变成"代码注入"——SpEL 的 {@code T()} 能打穿 JVM。
 * 这里只认四种形态，全部是纯函数（给定上游产出必得同一结果），因此：
 * <ul>
 *   <li>可单测、可审计（日志里就是一行 route=xxx → true/false）；</li>
 *   <li>边不需要持久化"走没走"——恢复时按上游产出重算即可，天然幂等。</li>
 * </ul></p>
 *
 * <p>形态：{@code always}（恒真，默认）· {@code nonEmpty}（上游产出非空）· {@code empty}（上游产出为空）·
 * {@code startsWith:关键词}（产出以该词开头，**推荐做分支标签**）· {@code contains:关键词}（包含匹配，关键词 ≤20 字）。</p>
 *
 * <p><b>为什么要有 startsWith</b>：contains 会被否定句误伤——上游写「常规：…无紧急迹象」时
 * {@code contains:紧急} 也命中，两条分支会同时跑。按标签前缀判定（「紧急：…」/「常规：…」）才是确定性的分支语义。</p>
 */
final class RoutePredicates {

    /** 恒真名。 */
    static final String ALWAYS = "always";

    /** 非空名。 */
    static final String NON_EMPTY = "nonEmpty";

    /** 空名。 */
    static final String EMPTY = "empty";

    /** 前缀匹配关键词。 */
    private static final String STARTS_WITH_PREFIX = "startsWith:";

    /** 包含匹配关键词。 */
    private static final String CONTAINS_PREFIX = "contains:";

    /** 关键词长度上限（防止把整段提示词塞进边里）。 */
    private static final int MAX_KEYWORD = 20;

    private RoutePredicates() {
    }

    /**
     * 归一：空/空白视为 {@link #ALWAYS}。
     *
     * @param route 入参：边上的 route
     * @return 出参：归一后的谓词名
     */
    static String normalize(String route) {
        return route == null || route.isBlank() ? ALWAYS : route.trim();
    }

    /**
     * 校验谓词是否合法（保存图时调用，非法直接 400）。
     *
     * @param route 入参：边上的 route（可空）
     * @return 出参：归一后的谓词名
     */
    static String validate(String route) {
        String name = normalize(route);
        if (ALWAYS.equals(name) || NON_EMPTY.equals(name) || EMPTY.equals(name)) {
            return name;
        }
        if (name.startsWith(STARTS_WITH_PREFIX) || name.startsWith(CONTAINS_PREFIX)) {
            String prefix = name.startsWith(STARTS_WITH_PREFIX) ? STARTS_WITH_PREFIX : CONTAINS_PREFIX;
            String keyword = name.substring(prefix.length()).trim();
            if (keyword.isEmpty() || keyword.length() > MAX_KEYWORD) {
                throw new BusinessException(ResultCodes.VALIDATION_ERROR,
                        "条件路由 " + name + " 的关键词必须为 1.." + MAX_KEYWORD + " 个字符");
            }
            return prefix + keyword;
        }
        throw new BusinessException(ResultCodes.VALIDATION_ERROR,
                "未知条件路由：" + name + "（支持 always / nonEmpty / empty / startsWith:关键词 / contains:关键词）");
    }

    /**
     * 求值：纯函数，给定上游产出必得同一结果（恢复时重算即可，无需持久化判定结果）。
     *
     * @param route          入参：已校验的谓词名
     * @param upstreamOutput 入参：上游节点产出（可空）
     * @return 出参：是否走这条边
     */
    static boolean evaluate(String route, String upstreamOutput) {
        String name = normalize(route);
        String output = upstreamOutput == null ? "" : upstreamOutput;
        if (ALWAYS.equals(name)) {
            return true;
        }
        if (NON_EMPTY.equals(name)) {
            return !output.isBlank();
        }
        if (EMPTY.equals(name)) {
            return output.isBlank();
        }
        if (name.startsWith(STARTS_WITH_PREFIX)) {
            String keyword = name.substring(STARTS_WITH_PREFIX.length()).trim().toLowerCase(Locale.ROOT);
            return output.trim().toLowerCase(Locale.ROOT).startsWith(keyword);
        }
        if (name.startsWith(CONTAINS_PREFIX)) {
            String keyword = name.substring(CONTAINS_PREFIX.length()).trim().toLowerCase(Locale.ROOT);
            return output.toLowerCase(Locale.ROOT).contains(keyword);
        }
        // 校验过的图不会走到这里；万一走到，按"不走"处理并让日志可见
        return false;
    }

}
