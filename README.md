# 映刻 · AI 短剧 H5

## 工程目录

```text
shortdrama-backend/   Java 后端及后台管理 API
  scripts/           本地启动及数据库验证
  sql/               Navicat 初始化说明和历史 SQL
  docs/              产品、架构和接入说明
shortdrama-frontend/  H5 播放端和后台管理页面
```

后台管理页面与 H5 共用前端工程，分别通过 `/admin` 与 `/` 访问。依赖、构建产物、本地上传、数据库文件和密钥不提交 Git。

包含移动端刷剧 H5、桌面管理台和 Java API，后续可嵌入 App WebView。

## 本地启动

需要 Node.js 20.19+、Java 21+、Maven 3.9+、MySQL 及 ffprobe（素材视频时长解析）。当前机器已配置本地数据库 `yingke_drama`。

新环境复制 `shortdrama-backend/application-local.example.yml` 为 `shortdrama-backend/application-local.yml`，填写数据库连接及首次创建管理员使用的密码。在仓库根目录运行：

```bash
python3 shortdrama-backend/scripts/start_backend.py
```

另一个终端启动前端：

```bash
cd shortdrama-frontend
npm install
npm run dev
```

- H5：http://127.0.0.1:5173/
- 管理台：http://127.0.0.1:5173/admin
- 管理员用户名：`admin`；当前机器密码见忽略提交的 `shortdrama-backend/local-admin.txt`。

前端将 `/api` 代理到 8080。不要提交 `application-local.yml`、管理员密码或商户密钥。初始内容含 4 部短剧、8 集，其中 3 部已发布；每部示例剧第 1 集免费、第 2 集需要会员或单集权益。所有观看都需要登录。

## Flyway 自动迁移

MySQL 模式下，后端启动会先执行 Flyway，再由 Hibernate 校验表结构。迁移版本文件位于 `shortdrama-backend/src/main/resources/db/migration/mysql/`。

- 空库：自动执行 V1 至当前版本。
- 旧版五表数据库：先核验旧结构，baseline 至 V1，再执行 V2 中文注释及后续迁移，保留原账号和业务数据。
- 已迁移数据库：只执行新版本；重复启动不会重复创建表或覆盖数据。
- 后续 SQL 变更新增 `V11__说明.sql`、`V12__说明.sql` 等文件，重启自动执行。已经执行过的文件不要修改，否则会因校验和不一致停止启动。
- Navicat 中可以查看 `flyway_schema_history` 及每个字段的中文注释。新环境不再手工导入旧 `shortdrama-backend/sql/001_init_mysql.sql`。

迁移说明和隔离验证脚本见 [shortdrama-backend/sql/README.md](shortdrama-backend/sql/README.md)。

## 观看与账号

- 注册、登录、退出；会话认证、CSRF 校验、BCrypt 密码哈希和后台角色隔离。
- 竖屏刷剧、选集、搜索分类、声音开关与 0.5×～2× 倍速。
- 记录观看剧集和秒数，跨设备续播；播放时定期保存，暂停、切集和切换页面时保存进度。
- 自动连播开关；最后一集显示看完提示，遇锁集显示解锁入口。
- 长按播放画面临时快进，松开恢复原倍速；拖动进度时暂停并展示实际帧预览。
- 账号收藏与观看历史写入 MySQL，可取消追剧、删除单条剧目历史或清空历史。喜欢也按账号写入服务器，支持跨设备状态同步；右侧显示真实喜欢、可见评论和追剧总数，操作后更新；为 0 或暂未取得时只保留图标和文字，不展示数字。
- 发现页展示运营精选及热播榜；热度按成功取得播放许可的独立账号计数。
- 评论与弹幕共享并写入 MySQL，后台支持隐藏、恢复和删除。

## 内容后台

- 剧目及分集增删改、发布管理、预览。
- 推荐运营：精选开关与排序，较小排序值优先。
- 素材库：封面 JPG/PNG/WebP、视频 MP4/WebM 上传与预览；视频时长由服务端 ffprobe 解析。编辑剧目时可选择已有素材或上传新素材。
- 素材通过服务器生成的名称保存，视频引用为 `asset:ID`；只有管理员可直接预览视频素材，观众必须走分集媒体接口。
- 已被剧目引用的素材不能直接删除。新上传写入私有 OSS 对象，使用独立 `ai-short-drama/assets/` 前缀；旧本地素材仍可读取。视频始终经登录及权益校验后由服务端流式读取，不暴露 OSS 公网地址。
- 运营看板：默认后台首页，支持近 7 天、近 30 天和自定义日期（最多 93 天），展示新增用户、真实播放次数、区间去重观众、实收支付、广告解锁、每日趋势、渠道分布及剧目 TOP 10；提供每日明细与统计口径。
- 用户权益：开通/调整/撤销会员，解锁/撤销指定剧集。
- 支付与广告：分别管理微信 H5 支付、支付宝手机网站支付、穿山甲 Android/iOS 参数及启停状态；保存即时生效。密钥只接受输入，后台不回显，留空保留，支持明确清除。

看板播放数据从本功能上线后开始采集，以视频实际开始播放为准；暂停继续和请求重试不重复计数，管理员播放不计入。旧账号无准确注册时间，不伪造历史新增趋势。收入仅统计成功支付订单，广告收益尚未接入；广告板块展示奖励请求与验证成功数。所有日期按北京时间统计。

## OSS 与 Redis

后端统一使用 YAML 配置：默认配置位于 `shortdrama-backend/src/main/resources/application.yml`，MySQL 配置位于 `application-mysql.yml`。复制 `shortdrama-backend/application-local.example.yml` 为同目录的 `application-local.yml`，填写 MySQL、Redis、OSS 和管理员配置；启动时自动加载，本地凭据文件不提交 Git。

MySQL 保留点赞、评论与收藏明细；Redis 存储三项共享数量，使用独立 `ai-short-drama:social:` 命名空间。互动提交后更新或失效对应计数，缓存缺失时由数据库重建；Redis 暂不可用时仍可通过数据库完成操作。零数量不显示数字。

上传与删除边界见 [OSS 存储说明](shortdrama-backend/docs/OSS_STORAGE.md)，缓存失效与故障恢复见 [Redis 说明](shortdrama-backend/docs/BACKEND_API.md#redis-社交计数缓存)。默认计数缓存 30 秒；Redis 故障同时发生重启或多实例切换时，旧计数最多保留一个缓存周期。

## 观看权益、广告与支付

免费分集仍需登录；非免费分集需要有效会员、单集解锁或管理员身份。列表中的锁集不返回视频地址；分集媒体接口在每次读取（含 Range）时检查权益。非免费内容必须使用平台托管视频，公开外链不能作为受限剧集来源。

已实现穿山甲 Android/iOS 奖励请求、原生桥接调用、服务端验签、交易去重和解锁状态查询；已实现微信 H5 支付与支付宝手机网站支付的订单创建适配、回调校验和幂等权益发放。价格和会员天数可在服务端配置，订单保存购买时快照。

管理员登录后，可进入「支付与广告」填写应用 ID、商户信息、密钥和回调地址；微信商户私钥直接粘贴 PEM 内容，无需填写服务器文件路径。支付宝可选择正式或沙箱环境。配置在数据库中加密保存，保存后即时生效，重启后保留；环境变量作为未设置后台配置时的兼容来源。首次保存时本机会创建 `shortdrama-backend/data/integrations.key`（权限 0600）；备份和迁移数据库时，也需单独备份该密钥文件，否则无法解密已有渠道配置。

当前尚无真实广告和支付商户凭据，渠道保持关闭，观众端显示“暂未开通”。实际广告展示还需要 Android/iOS 原生宿主集成 SDK，真实付款需要商户开通和公网 HTTPS 回调环境。

- [穿山甲配置](shortdrama-backend/docs/CSJ_REWARD_CONFIG.md)
- [App 桥接与真实渠道联调](shortdrama-backend/docs/APP_BRIDGE.md)
- [支付环境变量及 API](shortdrama-backend/docs/BACKEND_API.md)

## 媒体与验证

原始演示视频及音轨工程保存在 `shortdrama-backend/media-source/demo/`，带原创合成背景音乐的视频在 `shortdrama-backend/media/demo/`。这些是 12 秒播放验证片段，非真实 AI 短剧对白。运行 `python3 shortdrama-frontend/scripts/add-demo-audio.py` 可重新生成音轨。AI 剧本与视频生成工作台尚未接入。

前端构建：`cd shortdrama-frontend && npm run build`。后端测试：`cd shortdrama-backend && mvn test`。MySQL 迁移隔离测试：`python3 shortdrama-backend/scripts/verify_flyway_mysql.py`。具体执行记录与真机待验证项见 [测试计划](shortdrama-backend/docs/TEST_PLAN.md)。

本轮播放交互参考 ArtPlayer、xgplayer 和短剧 App 的功能设计，采用适配现有播放器的独立实现，未复制非商业许可项目源码或引入另一套播放器。GitHub 项目参考：[ArtPlayer](https://github.com/zhw2590582/ArtPlayer)、[xgplayer](https://github.com/bytedance/xgplayer)、[duanju](https://github.com/a289459798/duanju)。

详细说明：[产品需求](shortdrama-backend/docs/PRODUCT.md)、[系统架构](shortdrama-backend/docs/ARCHITECTURE.md)、[后端接口](shortdrama-backend/docs/BACKEND_API.md)。

## License

本项目采用 [MIT License](LICENSE)。
