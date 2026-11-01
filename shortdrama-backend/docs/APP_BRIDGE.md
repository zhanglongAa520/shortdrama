# Android / iOS 广告桥接与支付接入

本工程目前是 Vue H5 + Java 后端，未包含已编译的 Android / iOS 原生宿主。H5 已实现两端广告桥接调用和服务端奖励状态查询。原生宿主需要集成穿山甲 SDK 并实现以下接口，在后台「支付与广告」填写正式配置后才能展示广告。没有 SDK、广告位或商户配置时，页面保持渠道关闭，不模拟观看或付款成功。

## 穿山甲直投激励视频

服务端契约和环境变量见 [CSJ_REWARD_CONFIG.md](CSJ_REWARD_CONFIG.md)。本次采用穿山甲直投协议，不混用 GroMore 聚合或 uni-ad 的回调配置。协议来源：[穿山甲激励视频服务端验证](https://www.csjplatform.com/supportcenter/5416)。

流程：登录观众选择被锁定的剧集 → Java 创建绑定账号、剧集和平台的短期奖励请求 → H5 调用原生桥 → SDK 展示激励视频 → 穿山甲服务器回调 Java → Java 验签、去重并解锁该集 → H5 查询请求状态后刷新权益。

Android 宿主向 WebView 注册名为 `YingkeNative` 的接口，提供 `showRewardedAd(String json)`。iOS WKWebView 注册 `WKScriptMessageHandler`，名称为 `yingkeRewarded`。H5 调用分别为：

```javascript
// Android
window.YingkeNative.showRewardedAd(JSON.stringify(payload))
// iOS
window.webkit.messageHandlers.yingkeRewarded.postMessage(payload)
```

payload 结构：

```json
{
  "attemptId": "服务器生成的请求 ID",
  "appId": "本平台的 App ID",
  "placementId": "本平台的激励视频广告位 ID",
  "userId": "服务器返回的 providerUserId",
  "extra": "服务器生成的一次性随机串"
}
```

原生端须先按穿山甲要求完成 SDK 初始化和隐私流程；只允许可信 H5 域名调用桥；广告位与原生配置核对；使用服务器传入的 `userId` 和 `extra` 原样设置 SDK 的用户标识与透传字段，不自行拼接账号或剧集 ID。密钥只保存在 Java 服务端，不能放入 H5 或 App。

广告关闭、失败或完成时，原生端可向当前 WebView 派发：

```javascript
window.dispatchEvent(new CustomEvent('yingke:reward-result', {
  detail: { attemptId: '与请求一致', status: 'CLOSED' }
}))
```

status 可为 `CLOSED`、`CANCELLED`、`FAILED` 或 `COMPLETED`。宿主必须通过 JSON 序列化安全传入数据，不拼接未经转义的 JS 字符串。此事件只更新提示并加快查询；`COMPLETED` 不会直接发放权益。是否解锁始终以 Java 服务端回调结果为准。

H5 在面板打开时每 2.5 秒查询一次请求状态；面板关闭即停止轮询。原生取消后可等请求过期再重试。服务器回调需要可被穿山甲访问的 HTTPS 地址，`127.0.0.1` 无法接收公网回调。

## 微信 / 支付宝

本次 H5 采用微信 H5 支付和支付宝手机网站支付。二者均由 Java 创建订单、服务端定价、使用支付机构签名校验回调，再发放会员或单集权益。客户端回跳仅用于展示和查询，不能改变订单状态。

原生宿主需允许支付机构的受控跳转和支付完成后的可信站点回跳，保留本应用会话。微信内置浏览器的 JSAPI 支付、原生 App 支付 SDK、iOS App Store 内购不属于本次 H5 支付适配；上线到对应分发渠道时应按实际渠道接入方式单独验证。

渠道配置优先在管理台「支付与广告」中维护，包括微信商户及应用信息、商户 API 私钥、支付宝应用和商户信息、密钥及回调地址；保存后立即生效。密钥不回显，留空保留原值，可明确清除。本地渠道兼容配置及商品价格默认值见 `shortdrama-backend/application-local.example.yml` 和后端 README。正式开通前需完成商户开通、确认回调域名及商品价格。当前未配置的渠道会显示“暂未开通”，可先通过管理台验证会员和单集解锁。

## 联调验收

- 分别使用真实 Android WebView、iOS WKWebView 检查桥接、广告弹出、关闭、奖励延迟和回到 H5。
- 检查两端奖励请求使用各自广告位，服务端密钥不出现在页面或 App 日志中。
- 完成一次真实广告后，仅目标账号和目标剧集获得权益；重复回调不重复发放。
- 分别完成微信和支付宝的真实/官方测试环境支付，核对商户、金额、回调和会员有效期；取消付款不授予权限。
- 测试退后台、浏览器回跳、登录失效、会员到期和管理员撤销后的播放行为。

上述真实渠道联调需要平台配置和原生宿主，目前只能验证接口、状态流转与拒绝未配置渠道。
