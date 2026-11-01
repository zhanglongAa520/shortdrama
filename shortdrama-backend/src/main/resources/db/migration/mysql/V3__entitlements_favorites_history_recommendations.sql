ALTER TABLE drama
  ADD COLUMN featured BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否推荐到精选位',
  ADD COLUMN recommendation_rank INT NOT NULL DEFAULT 100 COMMENT '推荐排序，数值越小越靠前',
  ADD COLUMN view_count BIGINT NOT NULL DEFAULT 0 COMMENT '累计有效观看次数';

ALTER TABLE app_user
  ADD COLUMN membership_expires_at DATETIME(6) NULL COMMENT '会员到期时间，空表示非会员';

CREATE TABLE episode_unlock (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '解锁记录主键',
  user_id BIGINT NOT NULL COMMENT '用户 ID',
  episode_id BIGINT NOT NULL COMMENT '已解锁分集 ID',
  granted_at DATETIME(6) NOT NULL COMMENT '获得权益时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_episode_unlock_user_episode (user_id, episode_id),
  KEY idx_episode_unlock_episode (episode_id),
  CONSTRAINT fk_episode_unlock_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_episode_unlock_episode FOREIGN KEY (episode_id) REFERENCES episode (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户分集解锁权益表';

CREATE TABLE favorite (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '收藏记录主键',
  user_id BIGINT NOT NULL COMMENT '用户 ID',
  drama_id BIGINT NOT NULL COMMENT '短剧 ID',
  created_at DATETIME(6) NOT NULL COMMENT '收藏时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_favorite_user_drama (user_id, drama_id),
  KEY idx_favorite_drama (drama_id),
  CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_favorite_drama FOREIGN KEY (drama_id) REFERENCES drama (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户短剧收藏表';

CREATE TABLE watch_history (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '观看进度主键',
  user_id BIGINT NOT NULL COMMENT '用户 ID',
  drama_id BIGINT NOT NULL COMMENT '短剧 ID',
  episode_id BIGINT NOT NULL COMMENT '分集 ID',
  progress_seconds DOUBLE NOT NULL DEFAULT 0 COMMENT '播放进度，秒，保留亚秒精度',
  updated_at DATETIME(6) NOT NULL COMMENT '最后更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_watch_history_user_episode (user_id, episode_id),
  KEY idx_watch_history_user_updated (user_id, updated_at),
  KEY idx_watch_history_drama (drama_id),
  KEY idx_watch_history_episode (episode_id),
  CONSTRAINT fk_watch_history_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_watch_history_drama FOREIGN KEY (drama_id) REFERENCES drama (id) ON DELETE CASCADE,
  CONSTRAINT fk_watch_history_episode FOREIGN KEY (episode_id) REFERENCES episode (id) ON DELETE CASCADE,
  CONSTRAINT chk_watch_history_progress CHECK (progress_seconds >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户分集观看进度表';

CREATE TABLE drama_view (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '观看去重记录主键',
  user_id BIGINT NOT NULL COMMENT '用户 ID',
  drama_id BIGINT NOT NULL COMMENT '短剧 ID',
  first_viewed_at DATETIME(6) NOT NULL COMMENT '首次观看时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_drama_view_user_drama (user_id, drama_id),
  KEY idx_drama_view_drama (drama_id),
  CONSTRAINT fk_drama_view_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_drama_view_drama FOREIGN KEY (drama_id) REFERENCES drama (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户短剧观看去重表';
