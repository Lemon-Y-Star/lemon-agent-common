-- v3.9.2 敏感词过滤：平台级词库（admin 维护），输入拦截 + 输出脱敏共用一份词表。

CREATE TABLE sensitive_word (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    word       VARCHAR(64)  NOT NULL COMMENT '敏感词（精确匹配，输出替换为【已屏蔽】）',
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_word (word)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '敏感词库';

INSERT INTO sensitive_word (word) VALUES
('赌博'), ('诈骗'), ('办证'), ('代开发票'), ('洗钱'), ('枪支'), ('毒品');
