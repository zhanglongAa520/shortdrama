# MySQL 数据库说明

数据库结构已由 Flyway 管理，版本脚本位于：

```text
shortdrama-backend/src/main/resources/db/migration/mysql/
```

启用 `mysql` profile 启动后端时会自动创建数据库（账号需有建库权限）、执行待处理迁移，并在完成后由 Hibernate `validate` 校验实体映射。Navicat 可用于查看 `yingke_drama.flyway_schema_history`，不需要手工逐个执行迁移。

## 迁移序列

| 版本 | 内容 |
| --- | --- |
| V1 | Flyway 接管前的五表结构 |
| V2 | 五表中文注释 |
| V3 | 推荐、会员、分集解锁、收藏、续播和观看去重 |
| V4 | 封面与视频素材库 |
| V5 | 穿山甲激励广告奖励尝试 |
| V6 | 微信与支付宝支付订单 |
| V7 | 后台第三方渠道加密配置 |
| V8 | 真实播放事件、用户创建时间与运营看板数据基础 |
| V9 | 持久短剧点赞关系与社交统计数据基础 |
| V10 | 私有 OSS 素材存储元数据 |

版本脚本一旦在任何数据库执行，不得修改文件内容或复用版本号；后续变化必须新增 V11、V12 等迁移，避免 Flyway 校验和不一致。

## 已有数据库

已有 `yingke_drama` 若恰好是旧版五张表、29 个字段且没有 `flyway_schema_history`，启动时会安全 baseline 到 V1，再自动执行中文注释 V2 及以后版本。Flyway 的 `beforeBaseline` 守卫会核对表和字段集合；结构不匹配时拒绝 baseline 和启动，不会删除或重建表。

`001_init_mysql.sql` 与 `002_add_chinese_comments.sql` 仅保留为遗留结构参考和隔离迁移测试输入。新环境不应执行它们。

## 隔离验证

配置 `shortdrama-backend/application-local.yml` 后运行：

```bash
python3 shortdrama-backend/scripts/verify_flyway_mysql.py
```

脚本创建两个带随机后缀的临时数据库：一个空库，一个无 Flyway 历史的五表旧库。它对两者各启动两次非 Web 后端，检查 V1-V9、baseline、重复启动和旧数据保留，然后只删除本次创建的随机数据库。脚本不会连接或修改 `yingke_drama`。
