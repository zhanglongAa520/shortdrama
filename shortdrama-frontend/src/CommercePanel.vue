<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Crown, X, Play, RefreshCw } from 'lucide-vue-next'
import { api } from './api'
const props = defineProps({ episode: Object, entitlements: Object })
const emit = defineEmits(['close', 'unlocked', 'notice'])
const config = ref(null), ads = ref(null), error = ref(''), busy = ref(false), message = ref(''), attempt = ref(null), order = ref(null)
const product = ref(props.episode ? 'EPISODE' : 'MEMBERSHIP')
let timer, stopped = false, pollBusy = false
const platform = computed(() => window.webkit?.messageHandlers?.yingkeRewarded ? 'IOS' : window.YingkeNative?.showRewardedAd ? 'ANDROID' : null)
const adReady = computed(() => props.episode && platform.value && ads.value?.enabled && ads.value?.platforms?.[platform.value]?.enabled && ads.value?.platforms?.[platform.value]?.configured)
function money(cents) { return cents == null ? '待配置' : `¥${(cents / 100).toFixed(2)}` }
async function load() {
  const results = await Promise.allSettled([api('/payments/config'), api('/rewards/csj/config')])
  if (results[0].status === 'fulfilled') config.value = results[0].value
  if (results[1].status === 'fulfilled') ads.value = results[1].value
  if (results.some(r => r.status === 'rejected')) error.value = '部分渠道信息加载失败，请稍后重试'
  try { const saved = sessionStorage.getItem('yingke-pending-order'); if (saved) order.value = { id: saved } } catch {}
}
async function poll() {
  if (stopped || pollBusy) return
  pollBusy = true
  try {
    if (attempt.value) {
      const result = await api(`/rewards/csj/attempts/${attempt.value.id}`)
      if (result.status === 'REWARDED' || result.status === 'GRANTED') { attempt.value = null; message.value = '本集已解锁'; emit('unlocked') }
      else if (result.status === 'EXPIRED' || new Date(result.expiresAt).getTime() < Date.now()) { attempt.value = null; message.value = '本次广告验证已过期，请重试' }
    }
    if (order.value) {
      const result = await api(`/payments/orders/${order.value.id}`)
      if (result.status === 'PAID') { order.value = null; sessionStorage.removeItem('yingke-pending-order'); message.value = '支付成功，权益已生效'; emit('unlocked') }
      else if (['CLOSED', 'FAILED', 'EXPIRED'].includes(result.status)) { order.value = null; sessionStorage.removeItem('yingke-pending-order'); message.value = '订单未完成，可重新发起支付' }
    }
  } catch (e) { error.value = e.message; if (e.status === 404 || e.status === 403) { order.value = null; sessionStorage.removeItem('yingke-pending-order') } }
  finally { pollBusy = false }
}
async function reward() {
  if (!adReady.value || busy.value || attempt.value) return
  busy.value = true; error.value = ''
  try {
    const result = await api('/rewards/csj/attempts', { method: 'POST', body: { episodeId: props.episode.id, platform: platform.value } })
    attempt.value = result
    const settings = ads.value.platforms[platform.value]
    const payload = { attemptId: result.id, appId: settings.appId, placementId: settings.placementId, userId: result.providerUserId, extra: result.extra }
    if (platform.value === 'IOS') window.webkit.messageHandlers.yingkeRewarded.postMessage(payload)
    else window.YingkeNative.showRewardedAd(JSON.stringify(payload))
    message.value = '请完成广告观看，验证通过后自动解锁。'
  } catch (e) { attempt.value = null; error.value = e.message }
  finally { busy.value = false }
}
async function pay(provider) {
  if (!config.value?.providers?.[provider]?.configured || busy.value) return
  busy.value = true; error.value = ''
  try {
    const returnUrl = `${location.origin}/?paymentReturn=1`
    const result = await api('/payments/orders', { method: 'POST', body: { provider, productType: product.value, episodeId: product.value === 'EPISODE' ? props.episode?.id : undefined, returnUrl } })
    order.value = result; sessionStorage.setItem('yingke-pending-order', String(result.id))
    if (result.paymentUrl) {
      const url = new URL(result.paymentUrl)
      if (!['https:', 'http:'].includes(url.protocol)) throw new Error('支付地址无效，请重试')
      window.location.assign(url.href)
    } else { message.value = '订单已创建，请查询支付状态' }
  } catch (e) { error.value = e.message }
  finally { busy.value = false }
}
function nativeResult(event) {
  if (!attempt.value || String(event.detail?.attemptId) !== String(attempt.value.id)) return
  if (['FAILED', 'CANCELLED', 'CLOSED'].includes(event.detail?.status)) message.value = '广告已关闭。若已完成观看，正在等待服务端验证。'
  poll()
}
onMounted(async () => { await load(); if (stopped) return; await poll(); if (!stopped) { timer = setInterval(poll, 2500); window.addEventListener('yingke:reward-result', nativeResult) } })
onBeforeUnmount(() => { stopped = true; clearInterval(timer); window.removeEventListener('yingke:reward-result', nativeResult) })
</script>
<template>
  <div class="drawer-backdrop" @click.self="emit('close')" @wheel.stop @touchstart.stop @touchend.stop><section class="commerce-panel" role="dialog" aria-modal="true" aria-label="观看权益"><header><Crown :size="26" /><button class="icon-button" aria-label="关闭观看权益" @click="emit('close')"><X /></button></header><h2>{{ episode ? '下一段故事，等你解锁' : '映刻会员' }}</h2><p>{{ episode ? episode.title : entitlements?.membershipActive ? `当前会员有效期至 ${new Date(entitlements.membershipExpiresAt).toLocaleString('zh-CN')}` : '会员有效期内，畅看已发布的会员剧集。' }}</p>
    <div class="product-options"><button v-if="episode" :class="{selected: product === 'EPISODE'}" @click="product = 'EPISODE'"><strong>解锁本集</strong><span>{{ money(config?.episode?.priceCents) }}</span><small>解锁后可重复观看本集</small></button><button :class="{selected: product === 'MEMBERSHIP'}" @click="product = 'MEMBERSHIP'"><strong>{{ config?.membership?.title || '月度会员' }}</strong><span>{{ money(config?.membership?.priceCents) }}</span><small>{{ config?.membership?.days || 30 }} 天会员权益</small></button></div>
    <div class="payment-buttons"><button :disabled="busy || !config?.providers?.WECHAT?.configured" @click="pay('WECHAT')">微信支付{{ config?.providers?.WECHAT?.configured ? '' : ' · 暂未开通' }}</button><button :disabled="busy || !config?.providers?.ALIPAY?.configured" @click="pay('ALIPAY')">支付宝{{ config?.providers?.ALIPAY?.configured ? '' : ' · 暂未开通' }}</button></div>
    <div v-if="episode" class="reward-option"><button :disabled="!adReady || busy || !!attempt" @click="reward"><Play :size="16" />{{ attempt ? '等待奖励验证…' : '观看广告解锁本集' }}</button><small>{{ !platform ? '激励广告需在映刻 App 内观看' : !adReady ? '广告渠道暂未开通' : '完成观看并验证成功后解锁当前集' }}</small></div>
    <p v-if="message" class="commerce-message" role="status">{{ message }}</p><p v-if="error" class="form-error" role="alert">{{ error }}</p><button class="rights-refresh" :disabled="busy" @click="emit('unlocked')"><RefreshCw :size="14" />已开通？刷新观看权益</button><small class="commerce-footnote">当前也可由管理员在后台开通会员或单集权益。</small>
  </section></div>
</template>
