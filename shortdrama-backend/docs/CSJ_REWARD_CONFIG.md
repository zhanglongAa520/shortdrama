# 穿山甲激励视频服务端配置

本项目接入穿山甲直客激励视频回调，不使用 GroMore 回调协议。服务端实现默认关闭；在 Android/iOS 的真实 AppID、广告位 ID、SecurityKey 和奖励配置全部取得并完成真机联调前，不能将激励广告标记为已上线。

官方服务端奖励判定回调文档：<https://www.csjplatform.com/supportcenter/5416>。当前实现按该文档接收 `user_id,trans_id,reward_name,reward_amount,extra,sign`，并使用 `sha256(SecurityKey:trans_id)` 校验 `sign`。

## 环境变量

公共配置：

```text
CSJ_REWARDS_ENABLED=false
CSJ_ATTEMPT_TTL=PT10M
```

Android 配置：

```text
CSJ_ANDROID_ENABLED=false
CSJ_ANDROID_APP_ID=
CSJ_ANDROID_PLACEMENT_ID=
CSJ_ANDROID_SECURITY_KEY=
CSJ_ANDROID_REWARD_NAME=
CSJ_ANDROID_REWARD_AMOUNT=0
```

iOS 配置：

```text
CSJ_IOS_ENABLED=false
CSJ_IOS_APP_ID=
CSJ_IOS_PLACEMENT_ID=
CSJ_IOS_SECURITY_KEY=
CSJ_IOS_REWARD_NAME=
CSJ_IOS_REWARD_AMOUNT=0
```

先分别填完整端配置并完成回调验证，再打开对应端的 `*_ENABLED`，最后打开总开关 `CSJ_REWARDS_ENABLED`。两端不得共用或互换 SecurityKey。`CSJ_ATTEMPT_TTL` 使用 ISO-8601 Duration，例如 `PT10M`。

## 平台与客户端对接

穿山甲后台的奖励回调地址配置为部署域名下的公网 HTTPS 地址：

```text
https://<api-domain>/api/rewards/csj/callback
```

App 内 H5 先调用 `POST /api/rewards/csj/attempts`，把响应中的 `providerUserId` 作为穿山甲 `user_id`、`extra` 作为穿山甲同名透传字段交给对应平台原生 SDK。原生 SDK 的“播放完成”事件不能直接授予权益；H5 只轮询 `GET /api/rewards/csj/attempts/{id}`，直到服务端收到并验证穿山甲回调后返回 `GRANTED`。

`GET /api/rewards/csj/config` 只提供客户端初始化所需的 AppID、广告位和可用状态，不返回 SecurityKey。SecurityKey 只能存在于服务端环境变量或密钥管理系统中，不能写入 H5、App 包、日志或错误响应。

当前实现没有双密钥轮换窗口。轮换 SecurityKey 或关闭某端配置时，该端新的回调会 fail closed；应先停止创建新 attempt，等待现有 attempt TTL 结束，再切换密钥并恢复服务。
