-- Refuse an automatic baseline unless the non-empty database is exactly the
-- known five-table, 29-column legacy schema. This callback never drops data.
SET @legacy_table_count = (
  SELECT COUNT(*) FROM information_schema.tables
  WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'
);
SET @legacy_column_count = (
  SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()
);
SET @known_column_count = (
  SELECT COUNT(*) FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND CONCAT(table_name, '.', column_name) IN (
      'drama.id', 'drama.title', 'drama.description', 'drama.genre',
      'drama.cover_url', 'drama.status',
      'episode.id', 'episode.drama_id', 'episode.episode_position',
      'episode.title', 'episode.video_url', 'episode.duration_seconds', 'episode.free',
      'seed_marker.id',
      'app_user.id', 'app_user.username', 'app_user.password_hash',
      'app_user.nickname', 'app_user.role',
      'interaction.id', 'interaction.type', 'interaction.text',
      'interaction.drama_id', 'interaction.episode_id', 'interaction.at_seconds',
      'interaction.lane', 'interaction.status', 'interaction.user_id',
      'interaction.created_at'
    )
);
SET @baseline_guard_sql = IF(
  @legacy_table_count = 5 AND @legacy_column_count = 29 AND @known_column_count = 29,
  'SELECT 1',
  'SELECT * FROM FLYWAY_BASELINE_REFUSED_EXPECTED_5_TABLES_29_COLUMNS'
);
PREPARE baseline_guard_statement FROM @baseline_guard_sql;
EXECUTE baseline_guard_statement;
DEALLOCATE PREPARE baseline_guard_statement;
