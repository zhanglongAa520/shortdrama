CREATE TABLE integration_config (
  provider VARCHAR(32) NOT NULL COMMENT 'WECHAT、ALIPAY、CSJ_ANDROID 或 CSJ_IOS',
  enabled BOOLEAN NOT NULL DEFAULT FALSE COMMENT '后台配置的渠道开关',
  encrypted_values TEXT NOT NULL COMMENT 'AES-256-GCM 加密后的 JSON 配置覆盖值',
  config_version BIGINT NOT NULL COMMENT '配置版本，用于运行时客户端缓存失效',
  updated_at DATETIME(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (provider),
  CONSTRAINT chk_integration_config_version CHECK (config_version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='第三方渠道动态加密配置表';
