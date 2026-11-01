<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Activity, CalendarDays, ChevronDown, CreditCard, Gift, RefreshCw, Users, Video } from 'lucide-vue-next'
import { api } from './api'

const today = new Date(Date.now() + 8 * 3600_000).toISOString().slice(0, 10)
function daysBefore(days) { const date = new Date(`${today}T00:00:00Z`); date.setUTCDate(date.getUTCDate() - days); return date.toISOString().slice(0, 10) }
const from = ref(daysBefore(6)), to = ref(today), range = ref(7)
const data = ref(null), loading = ref(false), error = ref(''), metric = ref('plays'), hover = ref(null), detailsOpen = ref(false)
let requestId = 0, disposed = false
const metrics = [ { key: 'plays', label: '播放次数' }, { key: 'viewers', label: '观看人数' }, { key: 'newUsers', label: '新增用户' }, { key: 'revenueCents', label: '实收金额' } ]
const number = value => new Intl.NumberFormat('zh-CN').format(value ?? 0)
const money = value => `¥${((value ?? 0) / 100).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
const summary = computed(() => data.value?.summary || {})
const totals = computed(() => data.value?.totals || {})
const cards = computed(() => [
  { title: '新增用户', value: number(summary.value.newUsers), hint: '所选日期内注册的普通用户', icon: Users },
  { title: '播放次数', value: number(summary.value.plays), hint: '视频实际开始播放的次数', icon: Video },
  { title: '观看人数', value: number(summary.value.viewers), hint: '所选区间内去重观众', icon: Activity },
  { title: '实收金额', value: money(summary.value.revenueCents), hint: `${number(summary.value.paidOrders)} 笔成功支付订单`, icon: CreditCard },
  { title: '广告解锁成功', value: number(summary.value.rewardGranted), hint: '服务端验证成功的奖励次数', icon: Gift },
])
const daily = computed(() => data.value?.daily || [])
const metricLabel = computed(() => metrics.find(item => item.key === metric.value)?.label || '')
const chartMax = computed(() => Math.max(2, Math.ceil(Math.max(0, ...daily.value.map(row => Number(row[metric.value]) || 0)) / 2) * 2))
function point(index, value) { return { x: 48 + index / Math.max(1, daily.value.length - 1) * 652, y: 185 - (Number(value) || 0) / chartMax.value * 150 } }
const points = computed(() => daily.value.map((row, index) => point(index, row[metric.value])))
const line = computed(() => points.value.map(p => `${p.x},${p.y}`).join(' '))
const area = computed(() => points.value.length ? `M${points.value[0].x},185 L${points.value.map(p => `${p.x},${p.y}`).join(' L')} L${points.value.at(-1).x},185 Z` : '')
const hoveredDay = computed(() => daily.value[hover.value ?? daily.value.length - 1])
const dateTicks = computed(() => [...new Set([0, Math.floor((daily.value.length - 1) / 2), daily.value.length - 1])].filter(i => i >= 0))
function formatMetric(value) { return metric.value === 'revenueCents' ? money(value) : number(value) }
const updatedAt = computed(() => data.value ? new Date(data.value.generatedAt).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) : '')
const paymentName = provider => ({ WECHAT: '微信支付', ALIPAY: '支付宝' }[provider] || provider)
async function load() {
  error.value = ''
  if (!from.value || !to.value) { error.value = '请选择完整的开始和结束日期'; return }
  const days = (Date.parse(to.value) - Date.parse(from.value)) / 86400_000 + 1
  if (days < 1 || days > 93 || to.value > today) { error.value = '请选择不晚于今天、跨度不超过 93 天的日期区间'; return }
  const currentRequest = ++requestId
  loading.value = true
  try {
    const response = await api(`/admin/dashboard?${new URLSearchParams({ from: from.value, to: to.value })}`)
    if (disposed || currentRequest !== requestId) return
    data.value = response; hover.value = null
  } catch (cause) { if (!disposed && currentRequest === requestId) error.value = cause.message }
  finally { if (!disposed && currentRequest === requestId) loading.value = false }
}
function preset(days) { range.value = days; from.value = daysBefore(days - 1); to.value = today; load() }
onMounted(load)
onBeforeUnmount(() => { disposed = true; ++requestId })
</script>

<template>
  <div class="page-heading dashboard-heading"><div><p class="eyebrow">OPERATIONS DASHBOARD</p><h1>运营看板</h1><p class="subheading">从内容到收入，查看平台的实际运营表现。</p></div><button class="secondary-button" :disabled="loading" @click="load"><RefreshCw :size="16" :class="{ 'dashboard-spinning': loading }" />{{ loading ? '更新中…' : '刷新数据' }}</button></div>
  <form class="dashboard-filters" @submit.prevent="load">
    <div class="dashboard-presets" aria-label="快捷日期"><button v-for="days in [7, 30]" :key="days" type="button" :aria-pressed="range === days" :class="{ selected: range === days }" :disabled="loading" @click="preset(days)">近 {{ days }} 天</button></div>
    <div class="dashboard-dates"><CalendarDays :size="16" /><input v-model="from" type="date" aria-label="统计开始日期" :max="to || today" :disabled="loading" @input="range = null" /><span>至</span><input v-model="to" type="date" aria-label="统计结束日期" :min="from" :max="today" :disabled="loading" @input="range = null" /><button class="primary-button" :disabled="loading" type="submit">查询</button></div>
  </form>
  <p v-if="error" class="form-error dashboard-error" role="alert">{{ error }}<button v-if="!data" class="text-button" @click="load">重新加载</button></p>
  <p v-if="loading && !data" class="dashboard-loading" role="status">正在汇总运营数据…</p>
  <template v-if="data">
    <div class="dashboard-period"><span>{{ data.from }} — {{ data.to }} · 北京时间</span><span>更新于 {{ updatedAt }}</span></div>
    <p v-if="loading" class="dashboard-updating" role="status">正在更新，以下暂为上次查询结果。</p>
    <section class="dashboard-metrics" aria-label="区间运营指标"><article v-for="card in cards" :key="card.title" class="dashboard-metric"><div><span>{{ card.title }}</span><component :is="card.icon" :size="18" /></div><strong>{{ card.value }}</strong><small>{{ card.hint }}</small></article></section>
    <div class="dashboard-stock" aria-label="当前平台规模"><span>当前平台规模</span><span>普通用户 <strong>{{ number(totals.users) }}</strong></span><span>有效会员 <strong>{{ number(totals.activeMembers) }}</strong></span><span>已发布 / 全部剧目 <strong>{{ number(totals.publishedDramas) }} / {{ number(totals.dramas) }}</strong></span><span>分集 <strong>{{ number(totals.episodes) }}</strong></span></div>
    <section class="admin-panel dashboard-trend"><header class="dashboard-panel-heading"><div><h2>每日趋势</h2><p>按自然日汇总，无数据日期显示 0。</p></div><div class="dashboard-metric-tabs" aria-label="趋势指标"><button v-for="item in metrics" :key="item.key" :aria-pressed="metric === item.key" :class="{ selected: metric === item.key }" @click="metric = item.key">{{ item.label }}</button></div></header>
      <div class="dashboard-chart-caption"><span>{{ hoveredDay?.date || '—' }}</span><strong>{{ formatMetric(hoveredDay?.[metric]) }}</strong><small>{{ metricLabel }}</small></div>
      <div class="dashboard-chart"><svg viewBox="0 0 720 220" role="img" :aria-label="`${data.from}至${data.to}每日${metricLabel}趋势，可在每日明细中查看完整数据`"><defs><linearGradient id="dashboard-fill" x1="0" x2="0" y1="0" y2="1"><stop offset="0%" stop-color="#819b73" stop-opacity="0.28" /><stop offset="100%" stop-color="#819b73" stop-opacity="0.02" /></linearGradient></defs><g v-for="fraction in [0, 0.5, 1]" :key="fraction"><line x1="48" x2="700" :y1="185 - fraction * 150" :y2="185 - fraction * 150" stroke="#e8ece4" stroke-dasharray="4 5" /><text x="37" :y="189 - fraction * 150" text-anchor="end" fill="#8a9482" font-size="10">{{ metric === 'revenueCents' ? (chartMax * fraction / 100).toFixed(2) : number(Math.round(chartMax * fraction)) }}</text></g><path :d="area" fill="url(#dashboard-fill)" /><polyline :points="line" fill="none" stroke="#688456" stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round" /><g v-for="(p, index) in points" :key="daily[index].date"><circle :cx="p.x" :cy="p.y" :r="hover === index ? 5 : 3" fill="#688456" stroke="white" stroke-width="1.5" /><rect :x="p.x - 652 / Math.max(1, daily.length - 1) / 2" y="20" :width="Math.max(8, 652 / Math.max(1, daily.length - 1))" height="170" fill="transparent" @mouseenter="hover = index" @mouseleave="hover = null"><title>{{ daily[index].date }}：{{ metricLabel }} {{ formatMetric(daily[index][metric]) }}</title></rect></g><text v-for="index in dateTicks" :key="index" :x="points[index]?.x" y="211" :text-anchor="index === 0 ? 'start' : index === daily.length - 1 ? 'end' : 'middle'" fill="#8a9482" font-size="11">{{ daily[index]?.date.slice(5) }}</text></svg></div>
      <p v-if="daily.every(row => !row[metric])" class="dashboard-zero">所选区间暂无{{ metricLabel }}记录</p>
      <button class="text-button dashboard-details-toggle" :aria-expanded="detailsOpen" @click="detailsOpen = !detailsOpen">{{ detailsOpen ? '收起' : '查看' }}每日明细<ChevronDown :size="15" :style="{ transform: detailsOpen ? 'rotate(180deg)' : '' }" /></button>
      <div v-if="detailsOpen" class="dashboard-table-scroll"><table class="dashboard-table"><caption class="sr-only">所选区间每日运营数据</caption><thead><tr><th>日期</th><th>新增用户</th><th>播放次数</th><th>观看人数</th><th>支付订单</th><th>实收金额</th><th>广告请求</th><th>广告解锁</th></tr></thead><tbody><tr v-for="row in daily" :key="row.date"><td>{{ row.date }}</td><td>{{ number(row.newUsers) }}</td><td>{{ number(row.plays) }}</td><td>{{ number(row.viewers) }}</td><td>{{ number(row.paidOrders) }}</td><td>{{ money(row.revenueCents) }}</td><td>{{ number(row.rewardAttempts) }}</td><td>{{ number(row.rewardGranted) }}</td></tr></tbody></table></div>
    </section>
    <div class="dashboard-breakdowns">
      <section class="admin-panel"><header class="dashboard-panel-heading"><div><h2>支付渠道</h2><p>成功支付的订单与实收金额</p></div><CreditCard :size="19" /></header><div v-for="row in data.paymentChannels" :key="row.provider" class="dashboard-channel"><div><strong>{{ paymentName(row.provider) }}</strong><small>{{ number(row.paidOrders) }} 笔成功订单</small></div><strong>{{ money(row.revenueCents) }}</strong></div><div class="dashboard-panel-total"><span>区间实收合计</span><strong>{{ money(summary.revenueCents) }}</strong></div></section>
      <section class="admin-panel"><header class="dashboard-panel-heading"><div><h2>广告解锁</h2><p>广告请求与服务端验证成功奖励</p></div><Gift :size="19" /></header><div v-for="row in data.adPlatforms" :key="row.platform" class="dashboard-channel"><div><strong>{{ row.platform === 'IOS' ? 'iOS' : 'Android' }}</strong><small>{{ number(row.attempts) }} 次请求</small></div><strong>{{ number(row.granted) }} <small>次成功</small></strong></div><div class="dashboard-panel-total"><span>请求 / 成功</span><strong>{{ number(summary.rewardAttempts) }} / {{ number(summary.rewardGranted) }}</strong></div></section>
    </div>
    <section class="admin-panel dashboard-ranking"><header class="dashboard-panel-heading"><div><h2>热门剧目 TOP 10</h2><p>按所选区间的真实播放次数排序，收藏数为当前累计值。</p></div><Video :size="19" /></header><div v-if="!data.topDramas?.length" class="dashboard-empty">所选区间暂无播放记录，观众开始观看后将显示排行。</div><div v-else class="dashboard-table-scroll"><table class="dashboard-table"><thead><tr><th>排名</th><th>剧目</th><th>播放次数</th><th>观看人数</th><th>累计收藏</th></tr></thead><tbody><tr v-for="(row, index) in data.topDramas" :key="row.id"><td><span class="dashboard-rank" :class="{ top: index < 3 }">{{ String(index + 1).padStart(2, '0') }}</span></td><td><strong>{{ row.title }}</strong><small>{{ row.genre }}</small></td><td>{{ number(row.plays) }}</td><td>{{ number(row.viewers) }}</td><td>{{ number(row.favorites) }}</td></tr></tbody></table></div></section>
    <div class="dashboard-engagement"><span>区间互动</span><span>评论 <strong>{{ number(summary.comments) }}</strong></span><span>弹幕 <strong>{{ number(summary.danmaku) }}</strong></span><span>广告数据为请求和奖励数量，暂未接入广告收益报表。</span></div>
    <details class="dashboard-definitions"><summary>统计口径与历史数据说明</summary><ul><li>播放从本看板功能上线后的视频实际开始播放事件统计；暂停、继续及同一请求重试不重复计数。管理员播放不计入运营数据。</li><li>观看人数按账号去重。每日人数之和可能大于区间去重人数；次数与旧热榜的累计独立观众口径不同。</li><li>新增用户按注册时间统计，管理员不计入。旧账号没有准确注册时间，不回填到历史趋势。</li><li>收入按成功支付时间和订单实付金额统计，未支付订单、管理员赠送会员均不计入；当前未接入退款核算。</li><li>广告请求按创建时间统计，成功奖励按服务端验证通过时间统计；跨日回调可能使某天成功数多于请求数。</li><li>数据来自当前保留的业务记录。删除账号、剧目或互动可能影响相关统计；平台规模为查询时的当前值，不随日期筛选变化。</li></ul></details>
  </template>
</template>
