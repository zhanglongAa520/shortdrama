<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import {
  ArrowLeft, ArrowRight, ArrowUpRight, BarChart3, Bookmark, Check, ChevronDown,
  ChevronRight, Clapperboard, Compass, Eye, Film, Heart, Home, LayoutDashboard,
  ListVideo, Menu, MoreHorizontal, Pause, Pencil, Play, Plus, Search, Share2,
  Smartphone, Trash2, UserRound, Volume2, VolumeX, X,
  MessageSquare, LogOut, CreditCard,
} from 'lucide-vue-next'
import PlayerTools from './PlayerTools.vue'
import { playbackUrl } from './media'
import AuthGate from './AuthGate.vue'
import CommentsDrawer from './CommentsDrawer.vue'
import ModerationPanel from './ModerationPanel.vue'
import { api, checkSession, session, signOut } from './api'


import AssetLibrary from './AssetLibrary.vue'
import RecommendationPanel from './RecommendationPanel.vue'
import EntitlementPanel from './EntitlementPanel.vue'
import PlaybackScrubber from './PlaybackScrubber.vue'
import ViewerCollection from './ViewerCollection.vue'
import CommercePanel from './CommercePanel.vue'
import IntegrationSettings from './IntegrationSettings.vue'
import OperationsDashboard from './OperationsDashboard.vue'
import { useViewer } from './useViewer'
const isAdmin = location.pathname.startsWith('/admin')
const toast = ref('')
let toastTimer
function note(message) { toast.value = message; clearTimeout(toastTimer); toastTimer = setTimeout(() => { toast.value = '' }, 3500) }
const viewer = useViewer(note)
const { dramas, favorites, history, recommendations, entitlements, activeId, episodeIndex, activeTab, genre, query, apiOnline, video, playing, muted, buffering, videoError, source, showEpisodes, showSearch, showComments, showCommerce, autoNext, scrubbing, holding, resumeMessage, likes, social, socialPending, refreshSocial, updateCommentCount, genres, filteredDramas, current, currentEpisode, locked, metadata, playbackStarted, updateProgress, paused, ended, selectDrama, chooseEpisode, moveDrama, onWheel, onTouchEnd, holdDown, holdMove, endHold, cancelGesture, togglePlayback, toggleSound, toggleId, removeHistory, refreshRights } = viewer
const lockPromptDismissed = ref(false)
watch(() => currentEpisode.value?.id, () => { lockPromptDismissed.value = false })
function formatSocialCount(value) {
  if (value == null) return '—'
  if (value >= 100000000) return `${(value / 100000000).toFixed(1).replace(/\.0$/, '')}亿`
  if (value >= 10000) return `${(value / 10000).toFixed(1).replace(/\.0$/, '')}万`
  return new Intl.NumberFormat('zh-CN').format(value)
}
function closeComments() { showComments.value = false; refreshSocial() }
const retryVideo = viewer.prepare
const adminDramas = ref([]), adminView = ref('dashboard'), adminQuery = ref('')
const adminNames = { dashboard: '运营看板', overview: '总览', dramas: '剧目管理', interactions: '互动管理', recommendations: '推荐运营', assets: '素材库', entitlements: '用户权益', integrations: '支付与广告配置' }
const editorOpen = ref(false), editingId = ref(null), originalEpisodeIds = ref([])
const previewIndex = ref(0), previewItem = ref(null), assetPicker = ref(null)
const standardGenres = ['都市情感', '古风悬疑', '科幻悬疑', '治愈成长', '奇幻冒险', '其他']
function emptyForm() { return { title: '', description: '', genre: '都市情感', coverUrl: '/posters/after-rain.svg', status: 'DRAFT', episodes: [] } }
const form = ref(emptyForm())
function clone(value) { return JSON.parse(JSON.stringify(value)) }
const filteredAdmin = computed(() => adminDramas.value.filter(item => `${item.title} ${item.genre}`.includes(adminQuery.value.trim())))
const totalEpisodes = computed(() => adminDramas.value.reduce((sum, d) => sum + (d.episodes?.length || 0), 0))
const publishedCount = computed(() => adminDramas.value.filter(d => d.status === 'PUBLISHED').length)
const genreCounts = computed(() => { const counts = {}; adminDramas.value.forEach(d => { counts[d.genre] = (counts[d.genre] || 0) + 1 }); return Object.entries(counts).sort((a,b) => b[1]-a[1]) })
async function loadAdmin() { try { adminDramas.value = await api('/admin/dramas'); apiOnline.value = true } catch (e) { apiOnline.value = false; note(e.message) } }
async function shareDrama() { const url = `${location.origin}/?drama=${current.value?.id || ''}`; try { if (navigator.share) await navigator.share({title:current.value?.title,url}); else { await navigator.clipboard.writeText(url); note('链接已复制') } } catch {} }
function selectAsset(asset) {
 if (assetPicker.value?.type === 'COVER') form.value.coverUrl = asset.reference
 else if (assetPicker.value && form.value.episodes[assetPicker.value.index]) { const ep = form.value.episodes[assetPicker.value.index]; ep.videoUrl = asset.reference; ep.durationSeconds = Math.max(1, Math.ceil(asset.durationSeconds)); if (!ep.title) ep.title = asset.originalFilename }
 assetPicker.value = null
}
function openEditor(item) {
  editingId.value = item?.id ?? null
  form.value = item ? clone(item) : emptyForm()
  originalEpisodeIds.value = item?.episodes?.map(ep => ep.id) || []
  previewIndex.value = 0
  editorOpen.value = true
}

function openPreview(item) {
  previewIndex.value = 0
  previewItem.value = clone(item)
}

function addEpisode() {
  form.value.episodes.push({ title: `第${form.value.episodes.length + 1}集`, videoUrl: '', durationSeconds: 60, free: true })
}

function validateForm() {
  if (!form.value.title.trim()) return '请填写剧名'
  if (!form.value.genre.trim()) return '请填写类型'
  if (!form.value.coverUrl.trim()) return '请填写封面地址'
  if (form.value.episodes.some(item => !item.title?.trim() || !item.videoUrl?.trim() || !(item.durationSeconds > 0))) return '请补全每集标题、视频地址和时长'
  if (form.value.status === 'PUBLISHED' && !form.value.episodes.length) return '发布前请添加至少一集'
  return ''
}

async function saveDrama() {
  const error = validateForm()
  if (error) return note(error)
  const remainingIds = new Set(form.value.episodes.map(ep => ep.id))
  const removedCount = originalEpisodeIds.value.filter(id => !remainingIds.has(id)).length
  if (removedCount && !window.confirm(`保存将删除 ${removedCount} 集已有剧集及其互动，确定继续吗？`)) return
  const payload = clone(form.value)
  delete payload.id
  try {
    await api(editingId.value ? `/admin/dramas/${editingId.value}` : '/admin/dramas', {
      method: editingId.value ? 'PUT' : 'POST', body: payload,
    })
    await loadAdmin()
    editorOpen.value = false
    note('剧目已保存')
  } catch (error) { note(error.message) }
}

async function togglePublish(item) {
  const nextStatus = item.status === 'PUBLISHED' ? 'DRAFT' : 'PUBLISHED'
  if (nextStatus === 'PUBLISHED' && (!item.episodes?.length || item.episodes.some(ep => !ep.videoUrl))) return note('请先完善剧集视频')
  const payload = { ...clone(item), status: nextStatus }
  delete payload.id
  try {
    await api(`/admin/dramas/${item.id}`, { method: 'PUT', body: payload })
    await loadAdmin()
    note(nextStatus === 'PUBLISHED' ? '已发布到 H5' : '已转为草稿')
  } catch (error) { note(error.message) }
}

async function removeDrama(item) {
  if (!window.confirm(`确定删除《${item.title}》及全部剧集、评论和弹幕吗？`)) return
  try {
    await api(`/admin/dramas/${item.id}`, { method: 'DELETE' })
    await loadAdmin()
    note('剧目已删除')
  } catch (error) { note(error.message) }
}


async function logout() { await viewer.saveProgress(true); video.value?.pause(); try { await signOut(); viewer.clear(); adminDramas.value = []; editorOpen.value = false; previewItem.value = null } catch (e) { note(e.message) } }
async function onAuthenticated() { if (isAdmin) { if (session.user?.role === 'ADMIN') await loadAdmin() } else await viewer.load() }
onMounted(async () => { try { await checkSession() } catch (e) { note('无法连接服务，请确认后端已启动') } })
watch(() => session.user, user => { if (user) onAuthenticated(); else viewer.clear() })
</script>

<template>
  <AuthGate v-if="!session.ready || !session.user || (isAdmin && session.user.role !== 'ADMIN')" :admin="isAdmin" />
  <div v-else-if="isAdmin" class="admin-shell">
    <aside class="admin-sidebar">
      <a href="/" class="admin-brand" aria-label="映刻首页"><img class="brand-logo" src="/brand/yingke-logo-menu.png" alt="映刻 YINGKE" /></a>
      <div class="sidebar-caption">工作空间</div>
      <button class="sidebar-link" :class="{ selected: adminView === 'dashboard' }" @click="adminView = 'dashboard'"><BarChart3 :size="18" />运营看板</button>
      <button class="sidebar-link" :class="{ selected: adminView === 'overview' }" @click="adminView = 'overview'"><LayoutDashboard :size="18" />总览</button>
      <button class="sidebar-link" :class="{ selected: adminView === 'dramas' }" @click="adminView = 'dramas'"><Film :size="18" />剧目管理<span class="nav-count">{{ adminDramas.length }}</span></button>
      <button class="sidebar-link" :class="{ selected: adminView === 'interactions' }" @click="adminView = 'interactions'"><MessageSquare :size="18" />互动管理</button>
      <button class="sidebar-link" :class="{ selected: adminView === 'recommendations' }" @click="adminView = 'recommendations'"><BarChart3 :size="18" />推荐运营</button>
      <button class="sidebar-link" :class="{ selected: adminView === 'assets' }" @click="adminView = 'assets'"><Film :size="18" />素材库</button>
      <button class="sidebar-link" :class="{ selected: adminView === 'entitlements' }" @click="adminView = 'entitlements'"><UserRound :size="18" />用户权益</button>
      <button class="sidebar-link" :class="{ selected: adminView === 'integrations' }" @click="adminView = 'integrations'"><CreditCard :size="18" />支付与广告</button>
      <div class="sidebar-bottom">
        <div class="demo-notice"><span class="online-dot"></span> 本地演示工作台</div>
        <a class="sidebar-link" href="/"><Smartphone :size="18" />查看 H5 <ArrowUpRight :size="15" class="link-trail" /></a>
      </div>
    </aside>

    <div class="admin-main">
      <header class="admin-topbar"><div class="breadcrumb">内容中心 <ChevronRight :size="15" /> <strong>{{ adminNames[adminView] }}</strong></div><div class="topbar-right"><span class="api-badge" :class="{ offline: !apiOnline }">{{ apiOnline ? 'API 已连接' : '连接异常' }}</span><span class="avatar">{{ (session.user?.nickname || session.user?.username || 'A').slice(0, 1) }}</span><button class="icon-button" title="退出登录" @click="logout"><LogOut :size="18" /></button></div></header>

      <main class="admin-content" v-if="adminView === 'dashboard'"><OperationsDashboard /></main>
      <main class="admin-content" v-else-if="adminView === 'overview'">
        <div class="page-heading"><div><p class="eyebrow">CONTENT OVERVIEW</p><h1>工作台总览</h1><p class="subheading">管理剧目、编排剧集，准备下一次发布。</p></div><button class="primary-button" @click="openEditor()"><Plus :size="18" />新建剧目</button></div>
        <div class="metrics-grid">
          <div class="metric"><div class="metric-top"><span>全部剧目</span><Film :size="19" /></div><strong>{{ adminDramas.length }}</strong><small>剧目总数</small></div>
          <div class="metric"><div class="metric-top"><span>已发布</span><Eye :size="19" /></div><strong>{{ publishedCount }}</strong><small>在 H5 对观众可见</small></div>
          <div class="metric"><div class="metric-top"><span>剧集总数</span><ListVideo :size="19" /></div><strong>{{ totalEpisodes }}</strong><small>覆盖所有剧目</small></div>
        </div>
        <div class="admin-columns">
          <section class="admin-panel"><div class="panel-heading"><div><h2>最近编辑</h2><p>继续完善你的内容</p></div><button class="text-button" @click="adminView = 'dramas'">查看全部 <ArrowRight :size="16" /></button></div><div v-if="!adminDramas.length" class="empty-admin">还没有剧目，点击右上角新建。</div><div v-for="item in adminDramas.slice(0, 4)" :key="item.id" class="recent-row"><img :src="item.coverUrl" alt="" /><div class="recent-text"><strong>{{ item.title }}</strong><small>{{ item.genre }} · {{ item.episodes?.length || 0 }} 集</small></div><span class="status-pill" :class="item.status === 'PUBLISHED' ? 'published' : 'draft'">{{ item.status === 'PUBLISHED' ? '已发布' : '草稿' }}</span><button class="icon-button" title="编辑剧目" @click="openEditor(item)"><Pencil :size="16" /></button></div></section>
          <section class="admin-panel genre-panel"><div class="panel-heading"><div><h2>内容分布</h2><p>按题材统计剧目数量</p></div><BarChart3 :size="19" /></div><div v-if="!genreCounts.length" class="empty-admin">暂无内容数据</div><div v-for="[name, count] in genreCounts" :key="name" class="genre-row"><div class="genre-line"><span>{{ name }}</span><strong>{{ count }}</strong></div><div class="genre-track"><span :style="{ width: `${Math.max(14, count / Math.max(adminDramas.length, 1) * 100)}%` }"></span></div></div></section>
        </div>
        <div class="scope-note">当前已启用管理员权限校验。评论和弹幕可在「互动管理」中隐藏、恢复或删除。</div>
      </main>

      <main class="admin-content" v-else-if="adminView === 'dramas'">
        <div class="page-heading"><div><p class="eyebrow">DRAMA LIBRARY</p><h1>剧目管理</h1><p class="subheading">编辑内容与剧集，控制在 H5 的展示状态。</p></div><button class="primary-button" @click="openEditor()"><Plus :size="18" />新建剧目</button></div>
        <div class="table-toolbar"><div class="search-field"><Search :size="18" /><input v-model="adminQuery" placeholder="搜索剧名或题材" aria-label="搜索剧目" /></div><span>共 {{ filteredAdmin.length }} 部剧目</span></div>
        <div class="table-wrap"><table class="drama-table"><thead><tr><th>剧目</th><th>题材</th><th>剧集</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in filteredAdmin" :key="item.id"><td><div class="table-title"><img :src="item.coverUrl" alt="" /><div><strong>{{ item.title }}</strong><small>{{ item.description || '暂无简介' }}</small></div></div></td><td>{{ item.genre }}</td><td>{{ item.episodes?.length || 0 }} 集</td><td><span class="status-pill" :class="item.status === 'PUBLISHED' ? 'published' : 'draft'">{{ item.status === 'PUBLISHED' ? '已发布' : '草稿' }}</span></td><td><div class="row-actions"><button class="icon-button" title="预览" @click="openPreview(item)"><Eye :size="17" /></button><button class="icon-button" title="编辑" @click="openEditor(item)"><Pencil :size="17" /></button><button class="row-command" @click="togglePublish(item)">{{ item.status === 'PUBLISHED' ? '转草稿' : '发布' }}</button><button class="icon-button danger" title="删除" @click="removeDrama(item)"><Trash2 :size="17" /></button></div></td></tr></tbody></table><div v-if="!filteredAdmin.length" class="empty-admin">没有找到匹配的剧目</div></div>
      </main>
      <main v-else class="admin-content"><ModerationPanel v-if="adminView === 'interactions'" :dramas="adminDramas" @notice="note" /><RecommendationPanel v-else-if="adminView === 'recommendations'" :dramas="adminDramas" @refresh="loadAdmin" @notice="note" /><AssetLibrary v-else-if="adminView === 'assets'" @notice="note" /><EntitlementPanel v-else-if="adminView === 'entitlements'" :dramas="adminDramas" @notice="note" /><IntegrationSettings v-else-if="adminView === 'integrations'" @notice="note" /></main>
    </div>

    <div v-if="editorOpen" class="modal-backdrop" @click.self="editorOpen = false"><div class="editor-modal" role="dialog" aria-modal="true" aria-label="剧目编辑"><header class="modal-header"><div><p class="eyebrow">CONTENT EDITOR</p><h2>{{ editingId ? '编辑剧目' : '新建剧目' }}</h2></div><button class="icon-button" title="关闭" @click="editorOpen = false"><X :size="20" /></button></header><div class="modal-body"><div class="editor-grid"><label>剧名 <input v-model="form.title" placeholder="输入剧名" /></label><label>题材 <select v-model="form.genre"><option v-if="form.genre && !standardGenres.includes(form.genre)" :value="form.genre">{{ form.genre }}</option><option v-for="name in standardGenres" :key="name" :value="name">{{ name }}</option></select></label></div><label>简介 <textarea v-model="form.description" rows="3" placeholder="一句话介绍这部剧"></textarea></label><label>封面地址 <input v-model="form.coverUrl" placeholder="/posters/your-cover.jpg" /><button class="secondary-button asset-select" @click="assetPicker = {type: 'COVER'}">从素材库选择 / 上传封面</button></label><div class="editor-grid"><label>发布状态 <select v-model="form.status"><option value="DRAFT">草稿</option><option value="PUBLISHED">已发布</option></select></label><div class="cover-preview"><img :src="form.coverUrl" alt="封面预览" /><span>封面预览</span></div></div><div class="episode-heading"><div><h3>剧集列表</h3><p>按显示顺序填写，每集需要可播放的视频地址。</p></div><button class="secondary-button" @click="addEpisode"><Plus :size="16" />添加剧集</button></div><div v-if="!form.episodes.length" class="episodes-empty">还没有剧集</div><div v-for="(episode, index) in form.episodes" :key="index" class="episode-editor"><div class="episode-number">{{ String(index + 1).padStart(2, '0') }}</div><div class="episode-fields"><input v-model="episode.title" :aria-label="`第 ${index + 1} 集标题`" placeholder="剧集标题" /><input v-model="episode.videoUrl" :aria-label="`第 ${index + 1} 集视频地址`" placeholder="视频 URL，例如 /api/media/demo/drama-1.mp4" /><button class="secondary-button asset-select" @click="assetPicker = {type: 'VIDEO', index}">从素材库选择 / 上传视频</button><div class="episode-meta"><label>时长（秒）<input v-model.number="episode.durationSeconds" type="number" min="1" /></label><label class="checkbox-label"><input v-model="episode.free" type="checkbox" />免费集（关闭后需会员或单集解锁）</label></div></div><button class="icon-button danger" title="移除本集" @click="form.episodes.splice(index, 1)"><Trash2 :size="17" /></button></div></div><footer class="modal-footer"><button class="secondary-button" @click="editorOpen = false">取消</button><button class="primary-button" @click="saveDrama"><Check :size="17" />保存剧目</button></footer></div></div>
    <div v-if="previewItem" class="preview-backdrop" @click.self="previewItem = null"><div class="preview-modal" role="dialog" aria-modal="true" aria-label="剧集预览"><div class="preview-screen"><video :key="`${previewItem.id}-${previewIndex}`" controls autoplay muted playsinline :poster="previewItem.coverUrl" :src="playbackUrl(previewItem.episodes?.[previewIndex]?.videoUrl)"></video><div v-if="!previewItem.episodes?.length" class="preview-empty">这部剧还没有剧集</div></div><div class="preview-detail"><div class="preview-top"><div><small>{{ previewItem.status === 'PUBLISHED' ? '已发布' : '草稿预览' }} · {{ previewItem.genre }}</small><h2>{{ previewItem.title }}</h2></div><button class="icon-button" title="关闭预览" @click="previewItem = null"><X :size="20" /></button></div><p>{{ previewItem.description }}</p><div class="preview-episodes"><button v-for="(ep, index) in previewItem.episodes" :key="ep.id || index" :class="{ active: previewIndex === index }" @click="previewIndex = index">{{ index + 1 }} · {{ ep.title }}</button></div></div></div></div>
  </div>

  <div v-else class="viewer-shell" @wheel.passive="onWheel" @touchcancel="cancelGesture" @touchend.passive="onTouchEnd">
    <aside class="viewer-sidebar"><a href="/" class="viewer-brand" aria-label="映刻首页"><img class="brand-logo" src="/brand/yingke-logo-menu.png" alt="映刻 YINGKE" /><img class="brand-icon" src="/brand/yingke-icon.png" alt="" aria-hidden="true" /></a><div class="viewer-nav"><button :class="{ selected: activeTab === 'feed' }" @click="activeTab = 'feed'"><Home :size="20" />推荐</button><button :class="{ selected: activeTab === 'explore' }" @click="activeTab = 'explore'"><Compass :size="20" />发现</button><button :class="{ selected: activeTab === 'favorites' }" @click="activeTab = 'favorites'"><Bookmark :size="20" />追剧</button><button :class="{ selected: activeTab === 'profile' }" @click="activeTab = 'profile'"><UserRound :size="20" />我的</button></div><div class="viewer-sidebar-bottom"><span>{{ session.user?.nickname || session.user?.username }}，你好</span><button class="viewer-logout" @click="logout"><LogOut :size="15" />退出登录</button><a href="/admin">创作者工作台 <ArrowUpRight :size="14" /></a></div></aside>

    <main class="viewer-main">
      <div v-if="activeTab === 'feed'" class="feed-layout">
        <div class="feed-header"><div class="mobile-brand"><img src="/brand/yingke-logo-menu.png" alt="映刻 YINGKE" /></div><div class="feed-tabs"><button :class="{ active: genre === '全部' }" @click="genre = '全部'">推荐</button><button v-for="item in genres.filter(item => item !== '全部').slice(0, 2)" :key="item" :class="{ active: genre === item }" @click="genre = item">{{ item }}</button></div><button class="header-search" title="搜索短剧" @click="showSearch = true"><Search :size="21" /></button></div>
        <div v-if="!apiOnline" class="offline-banner">连接暂时中断 <button @click="loadPublic">重试连接</button></div>
        <div v-if="current" class="reel" :style="{ '--poster': `url('${current.coverUrl}')` }"><div class="reel-poster"></div><video ref="video" class="reel-video" :poster="current.coverUrl" :src="source || undefined" playsinline webkit-playsinline :muted="muted" @volumechange="muted = $event.target.muted" @play="playing = true" @playing="playbackStarted" @pause="paused" @waiting="buffering = true" @canplay="buffering = false" @timeupdate="updateProgress" @ended="ended" @loadedmetadata="metadata" @error="if (source) { videoError = true; buffering = false }"></video><div class="reel-shade"></div><div class="reel-hit" @pointerdown="holdDown" @pointermove="holdMove" @pointerup="endHold" @pointercancel="cancelGesture" @contextmenu.prevent @click="togglePlayback"></div><div v-if="holding" class="hold-hint">2× 快进中 · 松开恢复</div><div v-else-if="resumeMessage && !locked" class="resume-hint">{{ resumeMessage }}</div><div v-if="locked && !lockPromptDismissed" class="locked-episode" @keydown.esc.stop="lockPromptDismissed = true"><button class="locked-close" type="button" title="关闭解锁提示" aria-label="关闭解锁提示" @click.stop="lockPromptDismissed = true"><X :size="22" /></button><Film :size="30" /><strong>解锁这一集，继续故事</strong><p>会员可看，或单独解锁本集。</p><button @click="showCommerce = true">查看解锁方式</button></div><button v-if="locked && lockPromptDismissed" class="unlock-reminder" @click.stop="showCommerce = true">本集需解锁 · 查看解锁方式</button><button v-if="!locked && source && !playing && !videoError && !buffering" class="center-play" title="播放" @click="togglePlayback"><Play :size="30" fill="currentColor" /></button><div v-if="buffering && !videoError" class="video-error"><span class="loading-ring"></span><strong>正在缓冲</strong></div><div v-if="videoError" class="video-error"><Film :size="26" /><strong>视频暂时无法播放</strong><span>请检查该集的视频地址</span><button class="retry-button" @click="retryVideo">重试播放</button></div><div class="reel-meta"><div class="demo-tag">{{ currentEpisode?.free ? '免费集' : locked ? '待解锁' : '已解锁' }}</div><div class="series-line"><span class="series-seq">{{ String(filteredDramas.findIndex(item => item.id === current.id) + 1).padStart(2, '0') }} / {{ String(filteredDramas.length).padStart(2, '0') }}</span><span class="series-divider"></span><span>{{ current.genre }}</span></div><h1><button class="title-button" @click="showEpisodes = true">{{ current.title }}</button></h1><p>{{ current.description }}</p><button class="episode-trigger" @click="showEpisodes = true"><ListVideo :size="17" />{{ currentEpisode?.title || '选集' }} <ChevronRight :size="16" /></button></div><div class="reel-actions"><button title="喜欢" :disabled="!social || socialPending[current.id]" @click="toggleId('likes', current.id)"><span :class="{ active: likes.includes(current.id) }"><Heart :size="26" :fill="likes.includes(current.id) ? 'currentColor' : 'none'" /></span><small class="social-label"><b v-if="social?.likeCount > 0" class="social-count" :title="social?.likeCount == null ? '数量加载中' : `${social.likeCount} 次喜欢`">{{ formatSocialCount(social?.likeCount) }}</b><span>喜欢</span></small></button><button title="评论" @click="showComments = true"><span><MessageSquare :size="25" /></span><small class="social-label"><b v-if="social?.commentCount > 0" class="social-count" :title="social?.commentCount == null ? '数量加载中' : `${social.commentCount} 条评论`">{{ formatSocialCount(social?.commentCount) }}</b><span>评论</span></small></button><button title="追剧" :disabled="socialPending[current.id]" @click="toggleId('favorites', current.id)"><span :class="{ active: favorites.includes(current.id) }"><Bookmark :size="25" :fill="favorites.includes(current.id) ? 'currentColor' : 'none'" /></span><small class="social-label"><b v-if="social?.favoriteCount > 0" class="social-count" :title="social?.favoriteCount == null ? '数量加载中' : `${social.favoriteCount} 人追剧`">{{ formatSocialCount(social?.favoriteCount) }}</b><span>追剧</span></small></button><button title="选集" @click="showEpisodes = true"><span><ListVideo :size="25" /></span><small>选集</small></button><button title="分享" @click="shareDrama"><span><Share2 :size="24" /></span><small>分享</small></button><button :title="muted ? '开启声音' : '静音'" @click="toggleSound"><span><VolumeX v-if="muted" :size="24" /><Volume2 v-else :size="24" /></span><small>{{ muted ? '开声音' : '已开声' }}</small></button></div><PlayerTools v-if="!locked" :drama-id="current.id" :episode-id="currentEpisode?.id" :media="video" :episode-key="`${current.id}:${currentEpisode?.id ?? episodeIndex}`" @notice="note" /><PlaybackScrubber v-if="!locked" :media="video" :episode-key="String(currentEpisode?.id)" v-model:auto-next="autoNext" @scrubbing="scrubbing = $event" @seeked="viewer.saveProgress(true)" /></div>
        <div v-else class="viewer-empty">暂时没有可看的短剧</div>
        <div class="reel-switch"><button title="上一部" @click="moveDrama(-1)"><ChevronDown :size="19" /></button><button title="下一部" @click="moveDrama(1)"><ChevronDown :size="19" /></button></div>
      </div>

      <ViewerCollection v-else :tab="activeTab" :dramas="dramas" :favorites="favorites" :history="history" :recommendations="recommendations" :user="session.user" :entitlements="entitlements" :genre="genre" :query="query" @select="selectDrama" @remove-favorite="toggleId('favorites', $event)" @remove-history="removeHistory" @clear-history="removeHistory(null)" @search="showSearch = true" @genre="genre = $event" @logout="logout" @membership="showCommerce = true" />
    </main>

    <aside class="viewer-aside"><div class="aside-heading"><span>正在热映</span><span class="aside-counter">{{ String(dramas.length).padStart(2, '0') }} 部</span></div><button v-for="(item, index) in dramas" :key="item.id" class="aside-item" :class="{ current: current?.id === item.id && activeTab === 'feed' }" @click="selectDrama(item.id)"><span class="aside-rank">{{ String(index + 1).padStart(2, '0') }}</span><img :src="item.coverUrl" alt="" /><span class="aside-item-text"><strong>{{ item.title }}</strong><small>{{ item.genre }} · {{ item.episodes?.length || 0 }} 集</small></span><ChevronRight :size="15" /></button><div class="aside-foot"><span class="demo-dot"></span> 当前展示演示内容</div></aside>

    <CommercePanel v-if="showCommerce" :episode="activeTab === 'feed' && locked ? currentEpisode : null" :entitlements="entitlements" @close="showCommerce = false" @unlocked="refreshRights" @notice="note" /><CommentsDrawer v-if="showComments && current" :drama-id="current.id" :title="current.title" @close="closeComments" @count="updateCommentCount" />
    <nav class="mobile-nav"><button :class="{ selected: activeTab === 'feed' }" @click="activeTab = 'feed'"><Home :size="22" /><span>推荐</span></button><button :class="{ selected: activeTab === 'explore' }" @click="activeTab = 'explore'"><Compass :size="22" /><span>发现</span></button><button :class="{ selected: activeTab === 'favorites' }" @click="activeTab = 'favorites'"><Bookmark :size="22" /><span>追剧</span></button><button :class="{ selected: activeTab === 'profile' }" @click="activeTab = 'profile'"><UserRound :size="22" /><span>我的</span></button></nav>

    <div v-if="showEpisodes" class="drawer-backdrop" @click.self="showEpisodes = false"><div class="episode-drawer"><div class="drawer-handle"></div><header><div><span>{{ current?.genre }} · {{ current?.episodes?.length || 0 }} 集</span><h2>{{ current?.title }}</h2></div><button class="icon-button" title="关闭选集" @click="showEpisodes = false"><X :size="21" /></button></header><p class="drawer-description">{{ current?.description }}</p><div class="drawer-section">选集 <small>按顺序播放</small></div><div class="episode-list"><button v-for="(episode, index) in current?.episodes || []" :key="episode.id || index" :class="{ selected: index === episodeIndex }" @click="chooseEpisode(index)"><span class="ep-index">{{ String(index + 1).padStart(2, '0') }}</span><span class="ep-name">{{ episode.title }}<small>{{ Math.ceil((episode.durationSeconds || 0) / 60) }} 分钟 · {{ episode.free ? '免费' : episode.locked ? '需解锁' : '已解锁' }}</small></span><Play v-if="index === episodeIndex" :size="17" fill="currentColor" /><ChevronRight v-else :size="17" /></button></div></div></div>
    <div v-if="showSearch" class="search-overlay"><div class="search-dialog"><div class="search-dialog-top"><Search :size="20" /><input v-model="query" autofocus placeholder="搜索剧名、题材或故事" aria-label="搜索短剧" @keyup.enter="activeTab = 'explore'; showSearch = false" /><button title="关闭搜索" @click="showSearch = false"><X :size="20" /></button></div><div class="search-results"><span class="result-heading">{{ query ? '搜索结果' : '全部短剧' }}</span><button v-for="item in filteredDramas" :key="item.id" @click="selectDrama(item.id)"><img :src="item.coverUrl" alt="" /><span><strong>{{ item.title }}</strong><small>{{ item.genre }} · {{ item.episodes?.length || 0 }} 集</small></span><ChevronRight :size="18" /></button><div v-if="!filteredDramas.length" class="no-result">没有找到相关短剧</div></div></div></div>
  </div>
  <div v-if="toast" class="toast">{{ toast }}</div>
  <div v-if="assetPicker" class="modal-backdrop asset-picker-backdrop" @click.self="assetPicker = null"><div class="asset-picker-modal"><AssetLibrary picker :asset-type="assetPicker.type" @close="assetPicker = null" @select="selectAsset" @notice="note" /></div></div>
</template>
