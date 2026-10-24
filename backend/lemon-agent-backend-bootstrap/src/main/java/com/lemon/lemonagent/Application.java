package com.lemon.lemonagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

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

}
