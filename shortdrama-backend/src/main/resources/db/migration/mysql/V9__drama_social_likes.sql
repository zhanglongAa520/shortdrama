CREATE TABLE drama_like (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '点赞记录主键',
  user_id BIGINT NOT NULL COMMENT '点赞账号 ID',
  drama_id BIGINT NOT NULL COMMENT '短剧 ID',
  created_at DATETIME(6) NOT NULL COMMENT '点赞时间（UTC）',
  PRIMARY KEY (id),
  UNIQUE KEY uk_drama_like_user_drama (user_id, drama_id),
  KEY idx_drama_like_drama (drama_id),
  CONSTRAINT fk_drama_like_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT fk_drama_like_drama FOREIGN KEY (drama_id) REFERENCES drama (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='账号短剧点赞关系表';
