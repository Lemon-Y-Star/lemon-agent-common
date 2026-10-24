package com.lemon.lemonagent.model.route;

import com.lemon.architecture.model.BadRequestException;
import com.lemon.architecture.model.BaseReq;
import com.lemon.architecture.model.route.BusinessType;
import com.lemon.architecture.model.route.SourceType;

import java.util.Locale;

/**
 * 平台管理侧路由三元组（source / business / operation）的统一填充入口。
 *
 * <p>所有命令型请求的 {@code fillRoute} 都走这里：business 统一 {@link BusinessType#ANY}
 * （管理台操作不按业务域区分），来源解析失败转 400（客户端传错请求头不该报 500）。</p>
 */
public final class PlatformRoute {

    private PlatformRoute() {
    }

    /**
     * 填充路由三元组。
     *
     * @param request   入参：目标请求对象
     * @param source    入参：调用来源（X-Source 请求头，取 SourceType 枚举名，忽略大小写）
     * @param operation 入参：操作码，取自 Operations 常量
     */
    public static void fill(BaseReq request, String source, String operation) {
        request.setSourceType(resolveSource(source));
        request.setBusinessType(BusinessType.ANY);
        request.setOperationType(operation);
    }

    /**
     * 把请求头里的来源转成枚举。
     *
     * @param source 入参：调用来源，允许首尾空白
     * @return 出参：对应的 SourceType
     * @throws BadRequestException 来源为空或不是合法枚举名
     */
    public static SourceType resolveSource(String source) {
        if (source == null || source.isBlank()) {
            throw new BadRequestException("调用来源不能为空");
        }
        try {
            return SourceType.valueOf(source.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("不支持的调用来源：" + source);
        }
    }

}
