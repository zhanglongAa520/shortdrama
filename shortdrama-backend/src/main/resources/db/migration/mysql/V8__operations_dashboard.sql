ALTER TABLE app_user
  ADD COLUMN created_at DATETIME(6) NULL COMMENT '账号创建时间；迁移前旧账号保持空值，不推断历史',
  ADD KEY idx_app_user_role_created (role, created_at);

CREATE TABLE playback_event (
  id VARCHAR(36) NOT NULL COMMENT '客户端生成的幂等 UUID',
  user_id BIGINT NOT NULL COMMENT '实际播放用户 ID',
  drama_id BIGINT NOT NULL COMMENT '短剧 ID',
  episode_id BIGINT NOT NULL COMMENT '分集 ID',
  started_at DATETIME(6) NOT NULL COMMENT '服务端确认播放开始时间',
  PRIMARY KEY (id),
  KEY idx_playback_event_started (started_at),
  KEY idx_playback_event_user_started (user_id, started_at),
  KEY idx_playback_event_drama_started (drama_id, started_at),
  KEY idx_playback_event_episode_started (episode_id, started_at),
  CONSTRAINT fk_playback_event_user FOREIGN KEY (user_id)
    REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_playback_event_drama FOREIGN KEY (drama_id)
    REFERENCES drama (id) ON DELETE CASCADE,
  CONSTRAINT fk_playback_event_episode FOREIGN KEY (episode_id)
    REFERENCES episode (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='真实播放开始事件表';
