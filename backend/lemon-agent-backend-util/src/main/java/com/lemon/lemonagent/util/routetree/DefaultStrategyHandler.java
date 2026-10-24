package com.lemon.lemonagent.util.routetree;

/**
 * 规则树末端默认处理器：节点不再流转时落到这里，直接返回 null 表示流程结束。
 *
 * @param <T> 请求参数类型
 * @param <D> 上下文类型
 * @param <R> 返回结果类型
 */
public class DefaultStrategyHandler<T, D, R> implements StrategyHandler<T, D, R> {

    @Override
    public R apply(T requestParameter, D dynamicContext) {
        return null;
    }

}
