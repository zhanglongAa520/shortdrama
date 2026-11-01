CREATE TABLE reward_attempt (
  id VARCHAR(36) NOT NULL COMMENT '奖励尝试 UUID',
  user_id BIGINT NOT NULL COMMENT '发起用户 ID',
  episode_id BIGINT NOT NULL COMMENT '目标分集 ID',
  platform VARCHAR(16) NOT NULL COMMENT '客户端平台',
  nonce VARCHAR(64) NOT NULL COMMENT '单次奖励随机串',
  status VARCHAR(16) NOT NULL COMMENT '奖励状态',
  expected_reward_name VARCHAR(64) NOT NULL COMMENT '服务端期望奖励名称',
  expected_reward_amount INT NOT NULL COMMENT '服务端期望奖励数量',
  provider_transaction_id VARCHAR(128) NULL COMMENT '广告平台回传交易 ID',
  created_at DATETIME(6) NOT NULL COMMENT '创建时间',
  expires_at DATETIME(6) NOT NULL COMMENT '验证过期时间',
  granted_at DATETIME(6) NULL COMMENT '实际发放时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_reward_attempt_nonce (nonce),
  UNIQUE KEY uk_reward_attempt_provider_transaction (provider_transaction_id),
  KEY idx_reward_attempt_user_created (user_id, created_at),
  KEY idx_reward_attempt_status_expires (status, expires_at),
  KEY idx_reward_attempt_episode (episode_id),
  CONSTRAINT fk_reward_attempt_user FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_reward_attempt_episode FOREIGN KEY (episode_id)
    REFERENCES episode (id) ON DELETE CASCADE,
  CONSTRAINT chk_reward_attempt_amount CHECK (expected_reward_amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='激励广告服务端验证与发权尝试表';
