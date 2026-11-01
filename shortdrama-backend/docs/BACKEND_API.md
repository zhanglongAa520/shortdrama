# AI 短剧后端

Java 21、Spring Boot 3.5、Spring Security、Spring Data JPA、Flyway 和 MySQL。H2 仅用于本地回退与自动化业务测试，Flyway 在 H2 配置中关闭。

## MySQL 启动与自动迁移

复制 YAML 模板，填写 MySQL、Redis、OSS 及首次创建管理员所用密码：

```bash
cd shortdrama/shortdrama-backend
cp application-local.example.yml application-local.yml
python3 scripts/start_backend.py
```

配置结构见 `application-local.example.yml`：数据库使用 `spring.datasource`，Redis 使用 `spring.data.redis`，共享计数使用 `app.social-cache`，OSS 使用 `oss`。`mysql` profile 自动导入后端根目录的 `application-local.yml`，该文件已加入 Git 忽略。默认配置和 MySQL profile 配置分别为 `src/main/resources/application.yml`、`application-mysql.yml`。

数据库辅助脚本使用同一个 YAML，首次运行前安装 `python3 -m pip install -r scripts/requirements.txt`（在后端目录执行）。

连接 URL 带 `createDatabaseIfNotExist=true`。数据库账号有建库权限时，空库会自动创建并执行 Flyway V1-V10；之后每次重启只执行新增迁移，再由 Hibernate `validate` 校验实体。正式环境可提前创建数据库并给应用账号仅授予该库所需权限。

已有五表、29 字段且没有 Flyway 历史的旧库会 baseline 为 V1，再自动执行中文注释 V2 和后续迁移。baseline 前置守卫会核对已知表和字段；不匹配时启动失败且不删除数据。迁移序列、Navicat 查看方式和隔离验证见 [SQL 说明](../sql/README.md)。

首次启动且没有 `admin` 时，后端使用 `YINGKE_ADMIN_PASSWORD` 的 BCrypt 哈希创建管理员；未设置时不创建默认管理员，也不存在内置默认密码。

## 素材库

服务器需要可执行的 `ffprobe`（FFmpeg 提供）。默认从 `PATH` 查找，也可通过 `FFPROBE_EXECUTABLE=/absolute/path/to/ffprobe` 指定。素材保存在 `YINGKE_ASSET_ROOT`，该目录没有静态文件映射。

| 类型 | 支持格式 | 默认上限 | 可信元数据 |
| --- | --- | --- | --- |
| `COVER` | JPEG、PNG、WebP | 10 MiB | 服务端识别 MIME 和字节数 |
| `VIDEO` | MP4、WebM | 500 MiB | 服务端识别 MIME、字节数；`ffprobe` 时长向上取整 |

上传先写入素材目录内的临时文件，检查容量、文件签名和声明 MIME；视频还必须通过 `ffprobe`。服务器生成 UUID 文件名，成功后才写数据库；校验、`ffprobe` 或数据库保存失败会清理临时与成品文件。

删除素材时先在同一目录原子移动到 `.delete-*` 隔离名，再删除数据库元数据。数据库失败会恢复原文件；元数据删除成功但隔离文件清理失败会返回错误并保留隔离文件。应用启动不会按数据库结果自动删除目录文件，避免测试库或多实例误删共享存储；遗留文件必须通过指定数据库的运维核对流程处理。

| 方法 | 路径 | 权限与用途 |
| --- | --- | --- |
| POST | `/api/admin/assets` | `ADMIN`；multipart 的 `type` 和 `file` |
| GET | `/api/admin/assets?type=COVER|VIDEO` | `ADMIN`；列表 |
| GET | `/api/admin/assets/{id}` | `ADMIN`；元数据 |
| GET | `/api/admin/assets/{id}/content` | `ADMIN`；封面或视频预览，视频支持 Range |
| DELETE | `/api/admin/assets/{id}` | `ADMIN`；被剧目/分集引用时返回 409 |
| GET | `/api/assets/{id}/content` | 已登录；仅封面，视频直接访问被拒绝 |

素材响应字段为：

```json
{
  "id": 12,
  "type": "VIDEO",
  "originalFilename": "episode-01.mp4",
  "contentType": "video/mp4",
  "sizeBytes": 1048576,
  "durationSeconds": 63,
  "reference": "asset:12",
  "previewUrl": "/api/admin/assets/12/content",
  "createdAt": "2026-11-01T01:00:00Z"
}
```

封面的 `reference` 为登录可读的 `/api/assets/{id}/content`。视频的持久化引用为 `asset:{id}`；普通用户不能直接读取视频资产，必须通过 `/api/media/episodes/{episodeId}`，由分集权益服务每次重新校验后调用 `AssetStorageService.openVideoReference`。

## 会话与 CSRF

认证使用 HttpOnly `JSESSIONID`。浏览器跨端口调用需设置 Fetch `credentials: "include"`。所有 `POST`、`PUT`、`PATCH`、`DELETE` 先调用 `GET /api/auth/csrf`，并使用响应的 `headerName` 和 `token`。注册或登录轮换 Session 后必须重新取得 Token。

未登录访问受保护资源返回 401，普通用户访问 `/api/admin/**` 返回 403。用户视频始终通过分集 ID 播放接口进行权益校验。

## 用户短剧与权益接口

以下接口均需登录。写接口还必须携带当前 Session 对应的 CSRF Token。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/dramas` | 已发布短剧列表；每集包含 `free`、`locked`，锁集的 `videoUrl` 为 `null` |
| GET | `/api/dramas/{id}` | 已发布短剧详情，按当前用户权益生成分集播放地址 |
| GET | `/api/dramas/recommendations` | 返回 `{featured, hot}`；精选按推荐位升序，热榜按唯一观看用户数降序 |
| GET | `/api/dramas/{id}/social` | 已发布短剧的真实点赞、公开评论、收藏数及当前账号状态 |
| PUT | `/api/me/likes/{dramaId}` | 点赞已发布短剧，重复调用保持幂等；返回更新后的 Social DTO |
| DELETE | `/api/me/likes/{dramaId}` | 取消点赞，重复调用保持幂等；返回更新后的 Social DTO |
| GET | `/api/me/favorites` | 当前用户收藏列表 |
| POST | `/api/me/favorites/{dramaId}` | 收藏已发布短剧，重复调用保持幂等 |
| DELETE | `/api/me/favorites/{dramaId}` | 取消收藏 |
| GET | `/api/me/history` | 观看历史，按最近更新时间倒序 |
| PUT | `/api/me/history/{episodeId}` | 保存进度，请求体为 `{"progressSeconds": 12.5}`；同一分集采用最后写入生效，达到 95% 记为完成 |
| DELETE | `/api/me/history/{dramaId}` | 清除指定短剧的历史 |
| DELETE | `/api/me/history` | 清空当前用户全部历史 |
| POST | `/api/dramas/{dramaId}/episodes/{episodeId}/playback` | 取得可播放分集、续播秒数和下一集 ID，返回 `{episode,resumeAtSeconds,nextEpisodeId}` |
| GET | `/api/me/entitlements` | 返回会员状态、到期时间与已解锁分集 ID |

分集满足任一条件即可播放：免费、当前用户会员有效、已单集解锁或用户为管理员。锁集不会向列表和详情响应泄露视频地址。平台托管视频统一返回 `/api/media/episodes/{episodeId}`，该地址每次请求都会重新校验短剧发布状态和用户权益，并支持单段 HTTP Range。

非免费分集只能保存内置托管地址或素材引用 `asset:{id}`，不能直接保存公网视频 URL。`GET /api/media/demo/{filename}` 只供管理员检查内置视频；普通用户不能绕过分集鉴权读取视频素材。

Social DTO 为 `{dramaId,likeCount,commentCount,favoriteCount,liked,favorited}`。计数由后端直接聚合持久数据，客户端不能提交或修改：评论数只包含状态为 `VISIBLE` 的 `COMMENT`，不包含弹幕和隐藏评论；点赞与收藏包含普通账号和管理员账号。三个 Social 接口均拒绝草稿或不存在的短剧。

### Redis 社交计数缓存

MySQL 中的点赞关系、评论状态和收藏关系始终是事实源。Redis 只缓存 `likeCount`、`commentCount`、`favoriteCount` 三个全局计数；`liked`、`favorited` 和短剧发布状态每次仍从 MySQL 读取。默认关闭缓存，在本地 YAML 设置 `app.social-cache.enabled: true` 后启用。连接参数集中在 `spring.data.redis`，包括 `host`、`port`、`database`、`password`、`ssl.enabled`、`connect-timeout` 和 `timeout`。

默认 key 前缀是 `ai-short-drama:social:`，可通过 `app.social-cache.key-prefix` 修改。应用只操作以下两类精确 key，不执行 `FLUSHDB`、全库扫描或删除其他业务 key：

```text
ai-short-drama:social:drama:{dramaId}:epoch
ai-short-drama:social:drama:{dramaId}:counts:{epoch}
```

计数 key 保存 JSON 并使用 `app.social-cache.ttl`，默认 30 秒；epoch key 在点赞、收藏、公开评论创建、隐藏、恢复、删除，删分集或删剧的数据库事务成功提交后递增。回滚不会修改 Redis。写接口响应直接聚合当前 MySQL 事务，不读取旧缓存。冷缓存使用独立的 `READ_COMMITTED` 事务读取已提交计数，并在写缓存前再次核对 epoch；并发 mutation 即使发生在冷建中间，旧结果也只可能写入已经不可达的旧 epoch key。

Redis 连接或命令失败时请求直接降级查询 MySQL，不阻断业务事务。失效失败会在当前应用进程记录 dirty 状态，Redis 恢复后的下一次读取会先补做 epoch 递增，因此同一运行实例不会重新使用故障前旧计数。由于项目没有持久 outbox，如果数据库提交后 Redis 不可用且应用在此窗口内重启，或者请求切换到不知道该 dirty 状态的另一实例，Redis 中旧计数最多可能存续一个计数 TTL（默认 30 秒）；随后自动过期并从 MySQL 重建。需要跨重启、多实例零窗口强一致时，应新增持久 outbox，而不是把 Redis 当作事实源。

## 真实播放事件与运营看板

H5 在视频实际开始播放时调用 `POST /api/me/playback-events`：

```json
{
  "eventId": "b0f6dbd7-b131-4d0b-bb5d-798df3df1f7e",
  "episodeId": 12
}
```

`eventId` 由客户端为一次实际播放开始生成 UUID。同一账号、同一分集重复提交同一 UUID 会返回原事件，不会重复计数；同一 UUID 被其他账号或不同分集使用返回 409。后端只接受普通用户，且会重新检查短剧发布状态和观看权益，管理员不能写入运营播放事件。

管理员通过 `GET /api/admin/dashboard?from=YYYY-MM-DD&to=YYYY-MM-DD` 获取真实聚合数据。日期采用 `Asia/Shanghai` 自然日且包含首尾日期；默认返回今天及前 6 天，最长 93 天，不允许未来或反向区间。响应包含：

- 当前普通用户、有效会员、短剧、已发布短剧和分集总量。
- 区间新增用户、播放、去重观众、已支付订单、收入分、广告尝试/成功奖励、评论和弹幕。
- 每日补零趋势、支付渠道、广告平台和区间播放排行。

所有区间时间条件先转换为 UTC 数据库边界，再按上海自然日的 UTC 起止区间在 SQL 中聚合；不会加载全表后在 Java 内存统计。支付只按 `PAID` 且 `paid_at` 落入区间计入，广告尝试按 `created_at`、奖励成功按 `granted_at` 分别计入。后台账号从运营指标中排除。排行榜的播放和观众为区间事件，`favorites` 是当前普通用户累计收藏数。

V8 之前没有播放事件，不补造旧播放数据；旧账号的 `created_at` 保持 `NULL`，不会虚构历史注册趋势。项目目前没有可验证的广告收益数据，因此看板只展示广告尝试和成功奖励数。

## 管理员权益接口

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/admin/users` | 用户、会员状态和已解锁分集列表 |
| POST | `/api/admin/users/{userId}/membership` | 设置会员到期时间，请求体为 `{"expiresAt":"2026-12-01T00:00:00Z"}`，必须为未来时间 |
| DELETE | `/api/admin/users/{userId}/membership` | 撤销会员 |
| POST | `/api/admin/users/{userId}/episode-unlocks/{episodeId}` | 授予单集解锁，重复调用保持幂等 |
| DELETE | `/api/admin/users/{userId}/episode-unlocks/{episodeId}` | 撤销单集解锁 |

## 广告与支付配置

穿山甲、微信支付和支付宝默认全部关闭。管理员可通过以下接口保存配置，保存后无需重启即可生效：

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/admin/integrations/{provider}` | 读取脱敏配置状态 |
| PUT | `/api/admin/integrations/{provider}` | 保存配置；请求体为 `{enabled,values,clearSecrets}` |

`provider` 为 `WECHAT`、`ALIPAY`、`CSJ_ANDROID` 或 `CSJ_IOS`。响应为 `{provider,enabled,configured,values,secretFieldsSet}`：`values` 永不包含密钥，`secretFieldsSet` 仅指出哪些密钥已有效设置。PUT 中密钥字段传空白表示保留原值，只有放入 `clearSecrets` 才会明确清除；启用渠道时配置不完整返回 400，禁用时允许保存草稿和清除密钥。

- `WECHAT`：`appId`、`mchId`、`merchantSerialNo`、`apiV3Key`、`privateKey`、`notifyUrl`。`privateKey` 为完整 PEM。
- `ALIPAY`：`appId`、`sellerId`、`privateKey`、`publicKey`、`notifyUrl`、`gateway`。密钥接受常见 Base64 或 PEM，`gateway` 只能为 `PRODUCTION` 或 `SANDBOX`，不能配置任意 URL。
- `CSJ_ANDROID`、`CSJ_IOS`：`appId`、`placementId`、`securityKey`、`rewardName`、`rewardAmount`。

数据库中的渠道 values 使用 AES-256-GCM 整体加密。主密钥优先读取 Base64 编码的 32 字节 `INTEGRATION_MASTER_KEY`；未设置时，首次保存配置会原子创建 `INTEGRATION_MASTER_KEY_FILE`，默认 `shortdrama-backend/data/integrations.key`，POSIX 权限为 `0600`。必须将该密钥与数据库一起备份并分开保管；数据库已有密文但密钥丢失时应用拒绝解密，不会生成替代密钥。

环境变量配置继续作为兼容回退：某 provider 尚无数据库记录时沿用原环境变量；一旦后台保存，该 provider 的数据库行（包括 `enabled`）成为权威配置。后台启用 CSJ 平台不受旧 `CSJ_REWARDS_ENABLED=false` 阻挡。

未配置完整凭据时业务接口返回 503，不能创建模拟成功结果。

- 穿山甲：`app.rewards.csj.*`，总开关及 Android/iOS 开关默认 `false`。
- 微信支付：`payment.wechat.*`，使用官方 API v3 Java SDK `wechatpay-java 0.2.17`。
- 支付宝：`payment.alipay.*`，使用 `alipay-sdk-java 4.40.576.ALL`。

支付返回地址默认仅允许 `localhost` 和 `127.0.0.1` 的 5173、5174 端口；生产环境通过 `PAYMENT_ALLOWED_RETURN_ORIGINS` 配置实际可信来源。会员月卡和单集价格分别由 `MEMBERSHIP_PRICE_CENTS`、`MEMBERSHIP_DAYS`、`EPISODE_PRICE_CENTS` 配置，订单会保存成交快照。

支付回调为 `/api/payments/callbacks/wechat` 与 `/api/payments/callbacks/alipay`。业务层必须在幂等发放权益前完成验签或解密，并核对商户、金额、服务端订单和支付状态。

支付相关环境变量如下。价格单位均为分；修改商品价格或会员天数只影响新订单，创建订单时会将商品参数快照到订单记录。

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `INTEGRATION_MASTER_KEY` | 空 | Base64 编码的 32 字节配置主密钥；生产环境建议由密钥管理系统注入 |
| `INTEGRATION_MASTER_KEY_FILE` | `./data/integrations.key` | 未注入主密钥时使用的独立 `0600` 密钥文件 |
| `WECHAT_PAYMENT_ENABLED` | `false` | 微信 H5 支付总开关 |
| `WECHAT_PAYMENT_APP_ID` | 空 | 微信应用 ID |
| `WECHAT_PAYMENT_MCH_ID` | 空 | 微信商户号 |
| `WECHAT_PAYMENT_MERCHANT_SERIAL_NO` | 空 | 商户 API 证书序列号 |
| `WECHAT_PAYMENT_API_V3_KEY` | 空 | API v3 密钥 |
| `WECHAT_PAYMENT_PRIVATE_KEY` | 空 | 完整商户私钥 PEM |
| `WECHAT_PAYMENT_PRIVATE_KEY_PATH` | 空 | 兼容旧部署的商户私钥文件路径，直接 PEM 优先 |
| `WECHAT_PAYMENT_NOTIFY_URL` | 空 | 公网 HTTPS 微信回调地址 |
| `ALIPAY_PAYMENT_ENABLED` | `false` | 支付宝手机网站支付总开关 |
| `ALIPAY_PAYMENT_APP_ID` | 空 | 支付宝应用 ID |
| `ALIPAY_SELLER_ID` | 空 | 签约支付宝账号对应的 seller ID，回调会强制核对 |
| `ALIPAY_PAYMENT_PRIVATE_KEY` | 空 | 应用 RSA2 私钥 |
| `ALIPAY_PUBLIC_KEY` | 空 | 支付宝公钥 |
| `ALIPAY_PAYMENT_NOTIFY_URL` | 空 | 公网 HTTPS 支付宝回调地址 |
| `ALIPAY_GATEWAY` | `https://openapi.alipay.com/gateway.do` | 支付宝网关，沙箱环境可覆盖 |
| `PAYMENT_ALLOWED_RETURN_ORIGINS` | `http://localhost:5173,http://localhost:5174` | 前端同步跳转地址允许的 origin，逗号分隔；订单中的 `returnUrl` 必须匹配 |
| `MEMBERSHIP_PRICE_CENTS` | `1990` | 会员商品价格 |
| `MEMBERSHIP_DAYS` | `30` | 每份会员商品增加的天数 |
| `EPISODE_PRICE_CENTS` | `290` | 单集解锁价格 |

### 支付 API

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/payments/config` | 渠道可用状态及会员、单集商品参数 |
| GET | `/api/payments/catalog` | 商品目录 |
| POST | `/api/payments/orders` | 创建微信 H5 或支付宝手机网站支付订单 |
| GET | `/api/payments/orders` | 当前用户订单，按创建时间倒序 |
| GET | `/api/payments/orders/{id}` | 查询当前用户的指定订单状态 |

创建订单请求示例：

```json
{
  "provider": "WECHAT",
  "productType": "EPISODE",
  "episodeId": 12,
  "returnUrl": "http://localhost:5173/payment/result"
}
```

`provider` 可选 `WECHAT`、`ALIPAY`；`productType` 可选 `MEMBERSHIP`、`EPISODE`。会员订单不能传 `episodeId`，单集订单必须传非免费且已发布短剧中的分集 ID。渠道配置不完整时创建订单返回 503，不生成模拟支付页。

订单响应字段为 `{id,status,provider,productType,priceCents,episodeId,paymentUrl,html,createdAt,paidAt}`。微信 H5 使用 `paymentUrl`；支付宝当前返回可跳转的 `paymentUrl`。状态为 `CREATED`、`PAID`、`CLOSED` 或 `FAILED`。支付回调完成验签、商户和金额核对后，通过订单锁与状态检查幂等发放会员或单集权益。

## 测试

```bash
mvn test
```

H2 测试沿用 JPA 建表并显式禁用 MySQL Flyway。真实 MySQL 迁移使用隔离脚本：

```bash
python3 scripts/verify_flyway_mysql.py
```

该脚本不会启动 8080，也不会修改实际 `yingke_drama` 数据库。
