-- 遗留初始化脚本：只用于还原 Flyway 接管前的五表结构。
-- 新环境不要执行本文件；使用 mysql profile 启动后端，由 Flyway 自动执行 V1-V6。
-- 本脚本保留给迁移演练和已有安装核对，不覆盖已有业务数据或表结构。
-- 完整说明见 sql/README.md。
-- 数据库密码不写入 SQL；admin 账号由后端首次启动时按环境变量初始化。
-- 演示剧目由后端初始化，seed_marker 用于避免重启时重复创建。

CREATE DATABASE IF NOT EXISTS `yingke_drama`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE `yingke_drama`;

CREATE TABLE IF NOT EXISTS `drama` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '剧目主键，自增编号',
  `title` VARCHAR(255) NOT NULL COMMENT '短剧名称',
  `description` VARCHAR(4000) NULL COMMENT '短剧简介',
  `genre` VARCHAR(255) NOT NULL COMMENT '短剧题材或分类，如悬疑、爱情',
  `cover_url` VARCHAR(2000) NOT NULL COMMENT '封面图片地址，支持相对路径或完整 URL',
  `status` ENUM('DRAFT','PUBLISHED') NOT NULL COMMENT '发布状态：DRAFT=草稿，PUBLISHED=已发布',
  PRIMARY KEY (`id`),
  KEY `idx_drama_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='短剧信息表';

CREATE TABLE IF NOT EXISTS `episode` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '剧集主键，自增编号',
  `drama_id` BIGINT NOT NULL COMMENT '所属短剧 ID，关联 drama.id，删除短剧时级联删除剧集',
  `episode_position` INT NULL COMMENT '剧集在所属短剧中的排列顺序，从 0 开始，由 JPA 维护',
  `title` VARCHAR(255) NOT NULL COMMENT '剧集标题',
  `video_url` VARCHAR(2000) NOT NULL COMMENT '视频播放地址，内置视频使用需登录的媒体接口',
  `duration_seconds` INT NOT NULL COMMENT '剧集标注时长，单位：秒',
  `free` BIT(1) NOT NULL COMMENT '是否免费：0=非免费，1=免费；不代表可免登录观看',
  PRIMARY KEY (`id`),
  KEY `idx_episode_drama` (`drama_id`),
  CONSTRAINT `fk_episode_drama` FOREIGN KEY (`drama_id`) REFERENCES `drama` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='短剧分集表';

CREATE TABLE IF NOT EXISTS `seed_marker` (
  `id` VARCHAR(255) NOT NULL COMMENT '初始化任务唯一标识，用于避免重复写入演示数据',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='演示数据初始化标记表';

CREATE TABLE IF NOT EXISTS `app_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键，自增编号',
  `username` VARCHAR(64) NOT NULL COMMENT '登录用户名，唯一；接口限制为 3 至 32 位字母、数字或下划线',
  `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希，不存储明文密码',
  `nickname` VARCHAR(64) NOT NULL COMMENT '用户昵称，用于评论、弹幕和账号信息展示',
  `role` ENUM('USER','ADMIN') NOT NULL COMMENT '用户角色：USER=普通观众，ADMIN=后台管理员',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户与管理员账号表';

CREATE TABLE IF NOT EXISTS `interaction` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '互动内容主键，自增编号',
  `type` ENUM('COMMENT','DANMAKU') NOT NULL COMMENT '互动类型：COMMENT=评论，DANMAKU=弹幕',
  `text` VARCHAR(500) NOT NULL COMMENT '互动正文；接口限制评论最多 500 字，弹幕最多 30 字',
  `drama_id` BIGINT NOT NULL COMMENT '所属短剧 ID，关联 drama.id，删除短剧时级联删除互动',
  `episode_id` BIGINT NULL COMMENT '所属剧集 ID，关联 episode.id；弹幕必填，评论可为空；删除剧集时级联删除其互动',
  `at_seconds` DOUBLE NULL COMMENT '弹幕出现的播放时间点，单位：秒，必须大于等于 0；评论可为空',
  `lane` INT NULL COMMENT '弹幕轨道：0=上轨，1=中轨，2=下轨；评论可为空',
  `status` ENUM('VISIBLE','HIDDEN') NOT NULL COMMENT '显示状态：VISIBLE=观众可见，HIDDEN=后台隐藏；删除操作直接删除记录',
  `user_id` BIGINT NOT NULL COMMENT '发布者用户 ID，关联 app_user.id，由登录会话确定；删除用户时级联删除其互动',
  `created_at` DATETIME(6) NOT NULL COMMENT '创建时间，由服务端生成，精确到微秒',
  PRIMARY KEY (`id`),
  KEY `idx_interaction_public` (`drama_id`, `type`, `episode_id`, `status`, `created_at`),
  KEY `idx_interaction_user` (`user_id`),
  CONSTRAINT `fk_interaction_drama` FOREIGN KEY (`drama_id`) REFERENCES `drama` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_interaction_episode` FOREIGN KEY (`episode_id`) REFERENCES `episode` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_interaction_user` FOREIGN KEY (`user_id`) REFERENCES `app_user` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_interaction_lane` CHECK (`lane` IS NULL OR (`lane` BETWEEN 0 AND 2)),
  CONSTRAINT `chk_interaction_at` CHECK (`at_seconds` IS NULL OR `at_seconds` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='观众评论与弹幕表';
