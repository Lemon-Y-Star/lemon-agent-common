package com.lemon.lemonagent;

import com.lemon.lemonagent.servicecore.runtime.AgentRuntimeService;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;

/**
 * LemonAgent 平台后端启动入口。
 *
 * <p>框架通过 starter 自动装配生效，无需扫描 com.lemon.architecture 包；
 * scanBasePackages 覆盖全部应用层（model / dal / service-core / service / web），
 * 保证各模块里的 Controller 与 Handler 被 Spring 发现。</p>
 */
@SpringBootApplication(scanBasePackages = "com.lemon.lemonagent")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    /**
     * 进程重启的孤儿运行收口：上次进程遗留的 RUNNING/BLOCKED run 标为 FAILED(INTERRUPTED)，
     * 否则前端会对着一个永远不会推进的运行无限续流（契约 §6.2 的 INTERRUPTED 归因）。
     */
    @Bean
    public ApplicationListener<ApplicationReadyEvent> orphanRunCleaner(AgentRuntimeService agentRuntimeService) {
        return event -> agentRuntimeService.failOrphanRunsOnStartup();
    }

}
