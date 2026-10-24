package com.lemon.lemonagent.util.routetree;

/**
 * 规则树（组合模式）策略路由接口：树上每个节点都是同构的策略处理器。
 *
 * @param <T> 请求参数类型
 * @param <D> 上下文类型
 * @param <R> 返回结果类型
 */
public interface StrategyHandler<T, D, R> {

    /**
     * 受理请求并返回结果。
     *
     * @param requestParameter 请求参数
     * @param dynamicContext   上下文
     * @return 处理结果
     * @throws Exception 处理过程中的异常
     */
    R apply(T requestParameter, D dynamicContext) throws Exception;

}
