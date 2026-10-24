package com.lemon.lemonagent.web.config;

import com.lemon.architecture.web.advice.GlobalExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 注册框架的全局异常处理器，让所有异常都返回统一的 {@code BaseResult} 结构。
 *
 * <p>框架 starter 的自动装配清单里没有 {@code GlobalExceptionHandler}（它靠
 * {@code @RestControllerAdvice} 生效，而本工程 {@code scanBasePackages} 扫不到
 * {@code com.lemon.architecture.web.advice} 包），需要显式 {@code @Import}。
 * 只导异常处理器、不整包扫描 {@code com.lemon.architecture.web}，避免把已由 WebTrace
 * 自动装配注册过的 {@code TraceFilter} 注册第二遍。</p>
 */
@Configuration(proxyBeanMethods = false)
@Import(GlobalExceptionHandler.class)
public class WebAdviceConfig {
}
