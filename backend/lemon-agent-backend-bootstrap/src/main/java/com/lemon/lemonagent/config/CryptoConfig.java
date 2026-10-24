package com.lemon.lemonagent.config;

import com.lemon.lemonagent.util.crypto.CryptoUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 密钥加密工具的装配：把配置里的 AES 密钥（Base64 32 字节）注入 {@link CryptoUtil}。
 *
 * <p>密钥配置缺失时在启动期失败——落库加密是硬前提，不允许"忘了配就明文写库"。</p>
 */
@Configuration(proxyBeanMethods = false)
public class CryptoConfig {

    private final String base64Key;

    public CryptoConfig(@Value("${lemon.crypto.secret}") String base64Key) {
        this.base64Key = base64Key;
    }

    @PostConstruct
    public void init() {
        CryptoUtil.init(base64Key);
    }

}
