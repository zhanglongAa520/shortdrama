CREATE TABLE drama (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(255) NOT NULL,
  description VARCHAR(4000) NULL,
  genre VARCHAR(255) NOT NULL,
  cover_url VARCHAR(2000) NOT NULL,
  status ENUM('DRAFT','PUBLISHED') NOT NULL,
  PRIMARY KEY (id),
  KEY idx_drama_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE episode (
  id BIGINT NOT NULL AUTO_INCREMENT,
  drama_id BIGINT NOT NULL,
  episode_position INT NULL,
  title VARCHAR(255) NOT NULL,
  video_url VARCHAR(2000) NOT NULL,
  duration_seconds INT NOT NULL,
  free BIT(1) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_episode_drama (drama_id),
  CONSTRAINT fk_episode_drama FOREIGN KEY (drama_id) REFERENCES drama (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE seed_marker (
  id VARCHAR(255) NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE app_user (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  nickname VARCHAR(64) NOT NULL,
  role ENUM('USER','ADMIN') NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_app_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE interaction (
  id BIGINT NOT NULL AUTO_INCREMENT,
  type ENUM('COMMENT','DANMAKU') NOT NULL,
  text VARCHAR(500) NOT NULL,
  drama_id BIGINT NOT NULL,
  episode_id BIGINT NULL,
  at_seconds DOUBLE NULL,
  lane INT NULL,
  status ENUM('VISIBLE','HIDDEN') NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_interaction_public (drama_id, type, episode_id, status, created_at),
  KEY idx_interaction_user (user_id),
  CONSTRAINT fk_interaction_drama FOREIGN KEY (drama_id) REFERENCES drama (id) ON DELETE CASCADE,
  CONSTRAINT fk_interaction_episode FOREIGN KEY (episode_id) REFERENCES episode (id) ON DELETE CASCADE,
  CONSTRAINT fk_interaction_user FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE,
  CONSTRAINT chk_interaction_lane CHECK (lane IS NULL OR (lane BETWEEN 0 AND 2)),
  CONSTRAINT chk_interaction_at CHECK (at_seconds IS NULL OR at_seconds >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
