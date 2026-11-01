# AI 短剧平台架构

> 本文描述本轮目标架构与接口契约。接口和数据库脚本由实现与测试结果确认；本文不将尚未验证的能力描述为已交付。

## 1. 系统边界

平台包含移动端 H5、管理后台和 Java API。H5 负责注册登录、刷剧、选集、评论和弹幕；管理后台负责内容维护与互动审核；Spring Boot 负责身份会话、权限校验、CSRF、业务规则及 MySQL 持久化。后续 App 使用 WebView 加载同一 H5。

```text
H5 / WebView ───────┐
                    ├── HTTPS /api ── Spring Boot ── Spring Data JPA ── MySQL
管理后台（ADMIN） ──┘             │
                                  └── /api/media/demo/{filename}
```

首版后端保存账号、剧目、分集、评论和弹幕。演示媒体由受保护的后端路径提供；生产媒体上传、转码、CDN、防盗链、付费权益和 AI 生成任务仍是后续边界。

## 2. 身份、会话与授权

- 未登录用户只能访问注册、登录和获取 CSRF Token 所需接口；剧目、互动、演示媒体和管理接口均要求已登录。
- 注册创建普通 `USER`，客户端不得通过注册参数指定 `ADMIN`。管理员账号应由受控的初始化或数据库运维流程创建。
- 登录成功后服务端建立 Session，浏览器通过 Cookie 携带会话。前端所有 API 请求需允许携带 Cookie；不要把密码或长期凭据存入 `localStorage`。
- 改变状态的请求必须携带 CSRF Header。前端先调用 `GET /api/auth/csrf`，从响应读取 `token` 和 `headerName`，再以该响应指定的 Header 名发送 Token，不能硬编码 Header 名。
- `/api/admin/**` 在服务端校验 `ADMIN` 角色。普通用户即使知道管理接口路径也不能查询、编辑或删除管理数据。
- `POST /api/auth/logout` 成功返回 `204`，并使服务端 Session 失效。生产环境 Cookie 应启用 `HttpOnly`、`Secure` 和合适的 `SameSite`；跨域部署还需显式配置可信 Origin、凭据 CORS 与 CSRF 策略。

建议的错误语义为：未登录 `401`、已登录但权限不足 `403`、CSRF 缺失或无效 `403`、参数错误 `400`、资源不可见或不存在 `404`。前端应分别处理重新登录、权限提示、刷新 CSRF 和表单错误。

## 3. 核心数据模型

| 实体 | 核心字段 | 规则 |
| --- | --- | --- |
| User | `id`, `username`, `nickname`, `passwordHash`, `role` | `username` 唯一；密码仅保存安全哈希；`role` 为 `USER` 或 `ADMIN`。 |
| Drama | `id`, `title`, `description`, `genre`, `coverUrl`, `status`, `episodes` | `status` 为 `DRAFT` 或 `PUBLISHED`；普通用户只可见已发布剧目。 |
| Episode | `id`, `title`, `videoUrl`, `durationSeconds`, `free` | 隶属一个剧目，按剧目中的数组顺序播放。 |
| Interaction | `id`, `dramaId`, `episodeId`, `user`, `type`, `text`, `at`, `lane`, `status`, `createdAt` | `type` 为 `COMMENT` 或 `DANMAKU`；`status` 为 `VISIBLE` 或 `HIDDEN`。 |

评论的 `episodeId` 可省略，用于剧目级讨论。弹幕的 `episodeId` 必填；`at` 表示视频时间点，`lane` 表示显示轨道，两者应由后端校验合法范围。互动作者由当前 Session 推导，不能信任客户端传入的用户 ID。普通用户的互动读取只返回 `VISIBLE`；后台可查询全部状态。后台隐藏保留记录并可恢复，删除为永久操作且需要二次确认。

删除剧目时一并删除其分集和全部互动；删除分集时一并删除属于该分集的互动。MySQL 外键使用 `ON DELETE CASCADE`，服务层删除剧目时也显式清理互动。通过剧目 `PUT` 全量替换分集所触发的互动清理仍需由实现测试确认。

## 4. API 契约

请求和响应使用 JSON，统一前缀为 `/api`。以下为本轮目标接口；最终行为以实现与自动化测试结果为准。

### 4.1 认证

| 方法 | 路径 | 访问控制 | 契约 |
| --- | --- | --- | --- |
| `GET` | `/api/auth/csrf` | 未登录可访问 | 返回 `{"token":"...","headerName":"..."}`。 |
| `GET` | `/api/auth/me` | 已登录 | 返回当前用户 `{"id":1,"username":"...","nickname":"...","role":"USER|ADMIN"}`。 |
| `POST` | `/api/auth/register` | 未登录 | 注册普通用户并建立 Session，返回与 `me` 相同的用户对象。 |
| `POST` | `/api/auth/login` | 未登录 | 校验凭据、建立 Session，返回与 `me` 相同的用户对象。 |
| `POST` | `/api/auth/logout` | 已登录 | 使当前 Session 失效，成功返回 `204`。 |

所有 `POST`、`PUT`、`PATCH`、`DELETE` 请求均按服务端 CSRF 配置发送 Token，包括认证写请求。密码字段只存在于注册和登录请求，不出现在用户响应、日志或错误详情中。

### 4.2 内容与媒体

| 方法 | 路径 | 访问控制 | 用途 |
| --- | --- | --- | --- |
| `GET` | `/api/dramas` | 已登录 | 返回 `PUBLISHED` 剧目列表。 |
| `GET` | `/api/dramas/{id}` | 已登录 | 返回已发布剧目详情；草稿或不存在返回 `404`。 |
| `GET` | `/api/media/demo/{filename}` | 已登录 | 读取白名单内的演示媒体，并支持视频 Range 请求。 |
| `GET` | `/api/admin/dramas` | `ADMIN` | 返回全部状态剧目。 |
| `POST` | `/api/admin/dramas` | `ADMIN` | 新建剧目。 |
| `PUT` | `/api/admin/dramas/{id}` | `ADMIN` | 整体更新剧目和有序分集数组。 |
| `DELETE` | `/api/admin/dramas/{id}` | `ADMIN` | 删除剧目及其分集、互动。 |
| `GET` | `/api/admin/overview` | `ADMIN` | 返回内容概览。 |

剧目请求维持 `id,title,description,genre,coverUrl,status,episodes`；分集维持 `id,title,videoUrl,durationSeconds,free`。`PUT` 以请求中的 `episodes` 作为最终列表：合法的已有分集 ID 可保留，省略 ID 表示新增，未提交的旧分集删除。

演示媒体白名单仅包含 `drama-1.mp4` 至 `drama-4.mp4`。服务端必须拒绝路径穿越和白名单外文件；Range 响应应满足浏览器拖动进度与分段播放需要。用户退出后不能再通过该接口获取媒体。

### 4.3 评论与弹幕

| 方法 | 路径 | 访问控制 | 用途 |
| --- | --- | --- | --- |
| `GET` | `/api/dramas/{dramaId}/interactions?type={type}&episodeId={id?}` | 已登录 | `type` 必填，`episodeId` 可选；返回该剧匹配的可见互动。 |
| `POST` | `/api/dramas/{dramaId}/interactions` | 已登录 | Body 为 `{type,text,episodeId?,at?,lane?}`。 |
| `GET` | `/api/admin/interactions?type={type?}&status={status?}` | `ADMIN` | 审核列表，可按类型和状态筛选。 |
| `PATCH` | `/api/admin/interactions/{id}` | `ADMIN` | Body 为 `{"status":"VISIBLE|HIDDEN"}`，用于隐藏或恢复。 |
| `DELETE` | `/api/admin/interactions/{id}` | `ADMIN` | 永久删除互动。 |

互动响应为：

```json
{
  "id": 10,
  "type": "DANMAKU",
  "text": "这一幕太精彩了",
  "dramaId": 1,
  "episodeId": 2,
  "at": 12.5,
  "lane": 1,
  "status": "VISIBLE",
  "username": "viewer01",
  "nickname": "追剧用户",
  "createdAt": "2026-11-01T10:00:00Z"
}
```

互动文本必须由后端做长度和空白校验，前端输出时按文本转义，避免存储型 XSS。创建弹幕时 `episodeId` 必填且必须属于路径中的剧目；`at` 和 `lane` 必须符合后端约束。隐藏或删除后，普通用户后续读取不能再获得该记录。

## 5. MySQL 与本地运行

本地持久化目标为 MySQL，H2 不再是本轮运行数据库。默认库名为 `yingke_drama`，初始化脚本为 `shortdrama-backend/sql/001_init_mysql.sql`。开发者先创建或确认本地目标库，再执行该脚本；可在 Navicat 中打开 SQL 文件并对目标连接运行，也可使用 MySQL CLI。导入前必须核对目标连接和数据库，避免误操作其他库。

数据库连接地址、用户名和密码通过环境变量或本地配置注入，生产密钥不得提交仓库。初始化 SQL 应由运行验证确认是否覆盖表结构、必要索引、角色字段、互动状态和演示数据；管理员初始密码不得使用公开固定值进入生产环境。JPA 自动建表可用于受控开发，正式迁移应改用 Flyway/Liquibase 等版本化脚本。

Navicat 仅是数据库管理客户端，不属于应用运行依赖。验收时需验证重启服务后账号、剧目、互动与隐藏状态仍存在，并验证普通账号无法通过直接请求访问管理数据。

## 6. WebView 与生产迁移

App WebView 必须允许目标站点的 Session Cookie，并让 H5 按同源方式获取和发送 CSRF Token。App 不应把用户名、密码或 Session ID 拼进 URL。WebView 只加载可信 HTTPS 域名，限制任意跳转、文件访问和不受控 JavaScript Bridge；返回键应先关闭弹层或详情，再退出 WebView。

生产环境建议将前端和 API 部署在同站点或明确设计 Cookie 域与 CORS，使用 TLS、Session 固定攻击防护、登录限流、密码哈希、审计日志和备份恢复。演示媒体迁移到对象存储/CDN 后，应由服务端校验登录或权益并签发短时访问地址，避免公开永久 URL 绕过内容访问控制。
