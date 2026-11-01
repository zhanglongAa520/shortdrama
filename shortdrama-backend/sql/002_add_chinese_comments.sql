-- 遗留辅助脚本：为 001_init_mysql.sql 创建的五张表补充中文注释。
-- 新环境及已由 Flyway 管理的环境不要手工执行本文件。
-- 适用范围：现有表结构与 001 初始化脚本一致的 yingke_drama 数据库。
-- 仅更新表和字段的 COMMENT；保留字段类型、可空性、自增属性、索引及外键。
-- 在 Navicat 中连接本机 MySQL 后执行；可重复执行，不修改业务数据。
USE `yingke_drama`;

ALTER TABLE `drama`
  COMMENT = '短剧信息表',
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '剧目主键，自增编号',
  MODIFY COLUMN `title` VARCHAR(255) NOT NULL COMMENT '短剧名称',
  MODIFY COLUMN `description` VARCHAR(4000) NULL COMMENT '短剧简介',
  MODIFY COLUMN `genre` VARCHAR(255) NOT NULL COMMENT '短剧题材或分类，如悬疑、爱情',
  MODIFY COLUMN `cover_url` VARCHAR(2000) NOT NULL COMMENT '封面图片地址，支持相对路径或完整 URL',
  MODIFY COLUMN `status` ENUM('DRAFT','PUBLISHED') NOT NULL COMMENT '发布状态：DRAFT=草稿，PUBLISHED=已发布';

ALTER TABLE `episode`
  COMMENT = '短剧分集表',
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '剧集主键，自增编号',
  MODIFY COLUMN `drama_id` BIGINT NOT NULL COMMENT '所属短剧 ID，关联 drama.id，删除短剧时级联删除剧集',
  MODIFY COLUMN `episode_position` INT NULL COMMENT '剧集在所属短剧中的排列顺序，从 0 开始，由 JPA 维护',
  MODIFY COLUMN `title` VARCHAR(255) NOT NULL COMMENT '剧集标题',
  MODIFY COLUMN `video_url` VARCHAR(2000) NOT NULL COMMENT '视频播放地址，内置视频使用需登录的媒体接口',
  MODIFY COLUMN `duration_seconds` INT NOT NULL COMMENT '剧集标注时长，单位：秒',
  MODIFY COLUMN `free` BIT(1) NOT NULL COMMENT '是否免费：0=非免费，1=免费；不代表可免登录观看';

ALTER TABLE `seed_marker`
  COMMENT = '演示数据初始化标记表',
  MODIFY COLUMN `id` VARCHAR(255) NOT NULL COMMENT '初始化任务唯一标识，用于避免重复写入演示数据';

ALTER TABLE `app_user`
  COMMENT = '用户与管理员账号表',
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键，自增编号',
  MODIFY COLUMN `username` VARCHAR(64) NOT NULL COMMENT '登录用户名，唯一；接口限制为 3 至 32 位字母、数字或下划线',
  MODIFY COLUMN `password_hash` VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希，不存储明文密码',
  MODIFY COLUMN `nickname` VARCHAR(64) NOT NULL COMMENT '用户昵称，用于评论、弹幕和账号信息展示',
  MODIFY COLUMN `role` ENUM('USER','ADMIN') NOT NULL COMMENT '用户角色：USER=普通观众，ADMIN=后台管理员';

ALTER TABLE `interaction`
  COMMENT = '观众评论与弹幕表',
  MODIFY COLUMN `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '互动内容主键，自增编号',
  MODIFY COLUMN `type` ENUM('COMMENT','DANMAKU') NOT NULL COMMENT '互动类型：COMMENT=评论，DANMAKU=弹幕',
  MODIFY COLUMN `text` VARCHAR(500) NOT NULL COMMENT '互动正文；接口限制评论最多 500 字，弹幕最多 30 字',
  MODIFY COLUMN `drama_id` BIGINT NOT NULL COMMENT '所属短剧 ID，关联 drama.id，删除短剧时级联删除互动',
  MODIFY COLUMN `episode_id` BIGINT NULL COMMENT '所属剧集 ID，关联 episode.id；弹幕必填，评论可为空；删除剧集时级联删除其互动',
  MODIFY COLUMN `at_seconds` DOUBLE NULL COMMENT '弹幕出现的播放时间点，单位：秒，必须大于等于 0；评论可为空',
  MODIFY COLUMN `lane` INT NULL COMMENT '弹幕轨道：0=上轨，1=中轨，2=下轨；评论可为空',
  MODIFY COLUMN `status` ENUM('VISIBLE','HIDDEN') NOT NULL COMMENT '显示状态：VISIBLE=观众可见，HIDDEN=后台隐藏；删除操作直接删除记录',
  MODIFY COLUMN `user_id` BIGINT NOT NULL COMMENT '发布者用户 ID，关联 app_user.id，由登录会话确定；删除用户时级联删除其互动',
  MODIFY COLUMN `created_at` DATETIME(6) NOT NULL COMMENT '创建时间，由服务端生成，精确到微秒';
