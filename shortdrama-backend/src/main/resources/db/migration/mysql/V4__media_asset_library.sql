CREATE TABLE media_asset (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '素材主键',
  type ENUM('COVER','VIDEO') NOT NULL COMMENT '素材类型',
  original_filename VARCHAR(255) NOT NULL COMMENT '上传时的原始文件名，仅供展示',
  storage_filename VARCHAR(255) NOT NULL COMMENT '服务端生成的存储文件名',
  content_type VARCHAR(100) NOT NULL COMMENT '服务端按文件签名识别的 MIME 类型',
  size_bytes BIGINT NOT NULL COMMENT '文件字节数',
  duration_seconds INT NULL COMMENT '视频真实时长向上取整秒数，封面为空',
  created_at DATETIME(6) NOT NULL COMMENT '上传时间',
  created_by_user_id BIGINT NULL COMMENT '上传管理员 ID，删除账号后保留素材',
  PRIMARY KEY (id),
  UNIQUE KEY uk_media_asset_storage_filename (storage_filename),
  KEY idx_media_asset_type_created (type, created_at),
  KEY idx_media_asset_creator (created_by_user_id),
  CONSTRAINT fk_media_asset_creator FOREIGN KEY (created_by_user_id)
    REFERENCES app_user (id) ON DELETE SET NULL,
  CONSTRAINT chk_media_asset_size CHECK (size_bytes > 0),
  CONSTRAINT chk_media_asset_duration CHECK (
    (type = 'COVER' AND duration_seconds IS NULL)
    OR (type = 'VIDEO' AND duration_seconds > 0)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='可复用封面与视频素材库';
