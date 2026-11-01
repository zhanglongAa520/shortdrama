<script setup>
import { onMounted, ref, watch } from 'vue'
import { RefreshCw, Eye, EyeOff, Trash2, MessageSquare } from 'lucide-vue-next'
import { api } from './api'
const props = defineProps({ dramas: Array })
const emit = defineEmits(['notice'])
const type = ref('ALL')
const status = ref('ALL')
const items = ref([])
const busy = ref(false)
const error = ref('')
let sequence = 0
async function load() {
  const request = ++sequence
  busy.value = true
  const query = new URLSearchParams()
  if (type.value !== 'ALL') query.set('type', type.value)
  if (status.value !== 'ALL') query.set('status', status.value)
  try { const result = await api(`/admin/interactions?${query}`); if (request === sequence) { items.value = result; error.value = '' } }
  catch (cause) { if (request === sequence) error.value = cause.message }
  finally { if (request === sequence) busy.value = false }
}
watch([type, status], load)
onMounted(load)
async function toggle(item) {
  try {
    await api(`/admin/interactions/${item.id}`, { method: 'PATCH', body: { status: item.status === 'VISIBLE' ? 'HIDDEN' : 'VISIBLE' } })
    emit('notice', item.status === 'VISIBLE' ? '已隐藏，观众端将不再显示' : '已恢复显示')
    await load()
  } catch (cause) { error.value = cause.message }
}
async function remove(item) {
  if (!confirm('确定删除这条互动内容？删除后无法恢复。')) return
  try { await api(`/admin/interactions/${item.id}`, { method: 'DELETE' }); emit('notice', '已删除'); await load() }
  catch (cause) { error.value = cause.message }
}
function dramaTitle(id) { return props.dramas?.find(drama => drama.id === id)?.title || `剧目 ${id}` }
</script>
<template>
  <div class="page-heading"><div><p class="eyebrow">COMMUNITY MODERATION</p><h1>互动管理</h1><p class="subheading">管理观众评论与弹幕，隐藏后可随时恢复。</p></div><button class="secondary-button" :disabled="busy" @click="load"><RefreshCw :size="16" />刷新</button></div>
  <div class="moderation-filters"><label>类型 <select v-model="type" aria-label="互动类型"><option value="ALL">全部互动</option><option value="COMMENT">评论</option><option value="DANMAKU">弹幕</option></select></label><label>状态 <select v-model="status" aria-label="显示状态"><option value="ALL">全部状态</option><option value="VISIBLE">显示中</option><option value="HIDDEN">已隐藏</option></select></label><span>共 {{ items.length }} 条</span></div>
  <p v-if="error" class="form-error" role="alert">{{ error }}</p>
  <div class="moderation-list"><article v-for="item in items" :key="item.id" class="moderation-item"><div class="moderation-content"><div class="moderation-meta"><span class="status-pill" :class="item.type === 'DANMAKU' ? 'draft' : 'published'">{{ item.type === 'DANMAKU' ? '弹幕' : '评论' }}</span><strong>{{ item.nickname || item.username }}</strong><small>{{ dramaTitle(item.dramaId) }}<template v-if="item.type === 'DANMAKU'"> · 第 {{ (props.dramas?.find(d => d.id === item.dramaId)?.episodes?.findIndex(ep => ep.id === item.episodeId) ?? -1) + 1 }} 集 · {{ Number(item.at || 0).toFixed(1) }} 秒</template></small></div><p>{{ item.text }}</p><small>{{ new Date(item.createdAt).toLocaleString('zh-CN') }} · ID {{ item.id }}</small></div><div class="moderation-actions"><span class="status-pill" :class="item.status === 'VISIBLE' ? 'published' : 'draft'">{{ item.status === 'VISIBLE' ? '显示中' : '已隐藏' }}</span><button class="secondary-button" @click="toggle(item)"><EyeOff v-if="item.status === 'VISIBLE'" :size="15" /><Eye v-else :size="15" />{{ item.status === 'VISIBLE' ? '隐藏' : '恢复' }}</button><button class="icon-button danger" aria-label="删除互动" @click="remove(item)"><Trash2 :size="17" /></button></div></article><div v-if="!items.length" class="empty-admin">{{ busy ? '正在加载…' : '暂无符合条件的互动内容' }}</div></div>
</template>
