ALTER TABLE media_asset
  ADD COLUMN storage_backend ENUM('LOCAL','OSS') NOT NULL DEFAULT 'LOCAL'
    COMMENT '素材存储后端；历史数据为 LOCAL' AFTER storage_filename,
  ADD COLUMN object_key VARCHAR(512) NULL
    COMMENT 'OSS 私有对象 key；LOCAL 素材为空' AFTER storage_backend,
  ADD UNIQUE KEY uk_media_asset_object_key (object_key),
  ADD CONSTRAINT chk_media_asset_storage_location CHECK (
    (storage_backend = 'LOCAL' AND object_key IS NULL)
    OR (storage_backend = 'OSS' AND object_key LIKE 'ai-short-drama/assets/%')
  );
