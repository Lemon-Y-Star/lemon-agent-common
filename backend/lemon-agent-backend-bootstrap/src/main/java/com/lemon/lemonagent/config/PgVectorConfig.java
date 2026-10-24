package com.lemon.lemonagent.config;

import com.lemon.lemonagent.integration.retrieval.PgVectorProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 启用检索切片配置属性（lemon.retrieval.*：embedding 凭据 + PgVector 连接）。
 *
 * <p>@ConfigurationProperties 绑定类在 integration 模块，@EnableConfigurationProperties
 * 必须由 scanBasePackages 覆盖到的 @Configuration 显式开启——放组合根而不是 integration
 * 的自动配置，是为了让「检索能力是否启用」成为一个显式的组合根决策。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PgVectorProperties.class)
public class PgVectorConfig {

}
