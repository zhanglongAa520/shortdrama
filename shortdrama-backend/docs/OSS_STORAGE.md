# OSS 私有素材存储

新上传的封面与视频在 OSS 配置完整时保存到阿里云 OSS，历史本地素材继续从 `app.asset-root` 读取。数据库通过 `storage_backend` 区分 `LOCAL` 与 `OSS`，OSS 对象 key 记录在 `object_key`。

## 对象边界

- 所有新对象使用 `ai-short-drama/assets/<UUID>.<ext>`。
- 对象 ACL 固定为 `private`，上传禁止覆盖已有 key。
- 服务端不生成签名 URL，不把 bucket 域名或对象 key 返回客户端。
- 代码不调用 List Objects，也不会扫描或批量删除任何前缀。
- 删除只处理当前素材 key。为支持数据库失败恢复，会短暂复制到 `ai-short-drama/assets/.delete/` 下的随机隔离 key，完成后立即删除。

这一命名空间不得与其他产品共享对象前缀。应用所用 RAM 用户应按最小权限只授权该前缀的 `GetObject`、`PutObject`、`DeleteObject` 与复制对象操作。

## 配置

在 `shortdrama-backend/application-local.yml` 中配置：

```yaml
oss:
  access-key-id: ''
  access-key-secret: ''
  security-token: ''
  bucket-name: ''
  region: cn-hangzhou
  endpoint: ''
```

`security-token` 仅用于临时凭据，可为空。Endpoint 必须是 HTTPS OSS 服务端点。真实凭据仅填写在忽略提交的本地 YAML 中，完整模板见 `shortdrama-backend/application-local.example.yml`。

完全未配置 OSS 时使用 LOCAL，便于现有开发环境和历史数据继续工作。只要提供了任一 OSS 凭据、Bucket 或 Endpoint，四项必需配置（AccessKey ID、Secret、Bucket、Region）就必须完整且合法，否则应用拒绝启动，避免生产环境因漏配而静默写回本地。

## 上传与播放

上传仍先写入 `app.asset-root` 下的随机临时文件，并在本机完成容量限制、文件魔数、声明 MIME 和视频 `ffprobe` 校验。校验通过后才以流方式写入私有 OSS，临时文件随即删除。若 OSS 上传成功但数据库保存失败，只删除本次生成的对象 key。

客户端引用格式保持不变：封面为 `/api/assets/{id}/content`，视频为 `asset:{id}`。视频必须通过 `/api/media/episodes/{episodeId}` 播放；服务端每次请求都重新验证登录账号和分集观看权限。

OSS 内容由后端代理：先用 Head Object 校验对象大小，再按客户端单段 Range 发起对应的 Get Object Range 请求。服务端不会把 500 MB 视频一次读入内存，也不会为播放下载完整文件到本地。

## 删除与恢复

被剧目或分集引用的素材仍返回 `409`，不得删除。未引用素材先进入同前缀隔离状态，再删除数据库元数据：

- 数据库删除失败：从隔离 key 恢复原对象并删除隔离对象。
- 数据库删除成功：删除隔离对象。
- 上传元数据保存失败：删除本次上传对象。

上述补偿均使用已知的单个 key，不依赖目录或 bucket 扫描。

## 验证

单元测试使用 mock OSS 客户端，不访问真实 bucket。有限真实验收应只使用小型测试封面和短视频，并依次验证上传、Head、首段/中段 Range、锁集权限和删除；验收对象必须位于 `ai-short-drama/assets/`，完成后按返回的素材 ID 删除。
