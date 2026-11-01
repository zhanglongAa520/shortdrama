CREATE TABLE payment_order (
  order_no VARCHAR(64) NOT NULL COMMENT '服务端订单号',
  user_id BIGINT NOT NULL COMMENT '下单用户 ID',
  product_code VARCHAR(32) NOT NULL COMMENT 'VIP_MONTHLY 或 EPISODE_UNLOCK',
  provider VARCHAR(16) NOT NULL COMMENT 'WECHAT 或 ALIPAY',
  status VARCHAR(16) NOT NULL COMMENT 'CREATED、PAID、CLOSED 或 FAILED',
  amount_fen BIGINT NOT NULL COMMENT '服务端订单金额，分',
  membership_days INT NULL COMMENT '会员产品增加天数',
  episode_id BIGINT NULL COMMENT '单集解锁目标分集',
  provider_trade_no VARCHAR(128) NULL COMMENT '支付渠道交易号',
  return_url VARCHAR(1000) NULL COMMENT '支付完成返回地址',
  created_at DATETIME(6) NOT NULL COMMENT '创建时间',
  paid_at DATETIME(6) NULL COMMENT '支付成功时间',
  PRIMARY KEY (order_no),
  UNIQUE KEY uk_payment_order_provider_trade (provider, provider_trade_no),
  KEY idx_payment_order_user_created (user_id, created_at),
  KEY idx_payment_order_status_created (status, created_at),
  KEY idx_payment_order_episode (episode_id),
  CONSTRAINT fk_payment_order_user FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_payment_order_episode FOREIGN KEY (episode_id)
    REFERENCES episode (id) ON DELETE SET NULL,
  CONSTRAINT chk_payment_order_amount CHECK (amount_fen > 0),
  CONSTRAINT chk_payment_membership_days CHECK (membership_days IS NULL OR membership_days > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='微信与支付宝支付订单表';
