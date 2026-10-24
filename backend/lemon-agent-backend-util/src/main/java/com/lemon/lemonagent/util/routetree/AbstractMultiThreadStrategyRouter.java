package com.lemon.lemonagent.util.routetree;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 规则树（组合模式）多线程策略路由抽象类。
 *
 * <p>节点受理（apply）进入 doApply 处理具体业务，完成后通过 router() 流转：
 * 先执行 multiThread 扩展点（节点需要并行处理的逻辑），再由 get() 决定下一个节点。
 * get() 中写流转判断，把 if···else 从业务逻辑代码里拆分开。</p>
 *
 * @param <T> 请求参数类型
 * @param <D> 上下文类型
 * @param <R> 返回结果类型
 */
public abstract class AbstractMultiThreadStrategyRouter<T, D, R> implements StrategyHandler<T, D, R> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * 末端默认处理器：节点不需要继续流转时返回它，结果为 null。
     */
    protected final StrategyHandler<T, D, R> defaultStrategyHandler = new DefaultStrategyHandler<>();

    @Override
    public R apply(T requestParameter, D dynamicContext) throws Exception {
        return doApply(requestParameter, dynamicContext);
    }

    /**
     * 受理具体的业务逻辑；完成后调用 router() 流转到下一节点，不需要流转则直接 return。
     *
     * @param requestParameter 请求参数
     * @param dynamicContext   上下文
     * @return 处理结果
     * @throws Exception 处理过程中的异常
     */
    protected abstract R doApply(T requestParameter, D dynamicContext) throws Exception;

    /**
     * 流转到下一节点：先执行 multiThread 扩展点，再由 get() 决定的节点继续受理。
     *
     * @param requestParameter 请求参数
     * @param dynamicContext   上下文
     * @return 下一个节点的处理结果
     * @throws Exception 处理过程中的异常
     */
    protected R router(T requestParameter, D dynamicContext) throws Exception {
        multiThread(requestParameter, dynamicContext);
        return get(requestParameter, dynamicContext).apply(requestParameter, dynamicContext);
    }

    /**
     * 多线程扩展点：节点在流转前需要并行执行的逻辑写在这里（默认空实现）。
     *
     * @param requestParameter 请求参数
     * @param dynamicContext   上下文
     * @throws ExecutionException    执行异常
     * @throws InterruptedException  中断异常
     * @throws TimeoutException      超时异常
     */
    protected void multiThread(T requestParameter, D dynamicContext) throws ExecutionException, InterruptedException, TimeoutException {
    }

    /**
     * 获取下一个节点；需要分支判断的流程在这里返回对应节点。
     *
     * @param requestParameter 请求参数
     * @param dynamicContext   上下文
     * @return 下一个节点
     * @throws Exception 处理过程中的异常
     */
    public abstract StrategyHandler<T, D, R> get(T requestParameter, D dynamicContext) throws Exception;

}
