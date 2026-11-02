-- 运行文件产物（v4.4.4）：沙箱生成的图表/数据/报告落库（BLOB 直存，单机部署无需文件卷）
CREATE TABLE run_artifact (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id       BIGINT       NOT NULL,
    user_id      BIGINT       NOT NULL,
    name         VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   INT          NOT NULL,
    data         LONGBLOB     NOT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_run (run_id),
    KEY idx_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='运行文件产物：沙箱 png/csv/html 自动回收';
