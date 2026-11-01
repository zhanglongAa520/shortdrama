<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { X, MessageSquare, Send } from 'lucide-vue-next'
import { api, session } from './api'
const props = defineProps({ dramaId: Number, title: String })
const emit = defineEmits(['close', 'count'])
const items = ref([])
const text = ref('')
const loading = ref(true)
const busy = ref(false)
const error = ref('')
let sequence = 0
async function load() {
  const request = ++sequence, dramaId = props.dramaId
  try {
    const result = await api(`/dramas/${dramaId}/interactions?type=COMMENT`)
    if (request === sequence) { items.value = result; error.value = ''; emit('count', { dramaId, count: result.length }) }
  } catch (cause) { if (request === sequence) error.value = cause.message }
  finally { if (request === sequence) loading.value = false }
}
watch(() => props.dramaId, () => { items.value = []; text.value = ''; loading.value = true; load() }, { immediate: true })
const timer = setInterval(load, 5000)
onBeforeUnmount(() => { ++sequence; clearInterval(timer) })
async function send() {
  if (busy.value || !text.value.trim()) return
  busy.value = true
  try {
    await api(`/dramas/${props.dramaId}/interactions`, { method: 'POST', body: { type: 'COMMENT', text: text.value.trim() } })
    text.value = ''
    await load()
  } catch (cause) { error.value = cause.message }
  finally { busy.value = false }
}
function time(value) { return new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) }
</script>
<template>
  <div class="drawer-backdrop" @click.self="emit('close')" @wheel.stop @touchstart.stop @touchend.stop>
    <section class="comments-drawer" role="dialog" aria-modal="true" aria-label="评论">
      <div class="drawer-handle"></div>
      <header><div><small>{{ title }}</small><h2>评论 <span>{{ items.length }}</span></h2></div><button class="icon-button" aria-label="关闭评论" @click="emit('close')"><X :size="21" /></button></header>
      <div class="comments-scroll">
        <p v-if="loading" class="comments-empty">正在加载评论…</p>
        <div v-else-if="!items.length" class="comments-empty"><MessageSquare :size="27" /><p>还没有评论，聊聊你的感受吧。</p></div>
        <article v-for="item in items" :key="item.id" class="comment-item"><div class="comment-avatar">{{ (item.nickname || item.username || '观众').slice(0, 1) }}</div><div><div class="comment-heading"><strong>{{ item.nickname || item.username }}</strong><time>{{ time(item.createdAt) }}</time></div><p>{{ item.text }}</p></div></article>
      </div>
      <form class="comment-compose" @submit.prevent="send"><label class="sr-only" for="comment-text">评论内容</label><textarea id="comment-text" v-model="text" maxlength="500" rows="3" placeholder="说说你的看法…"></textarea><div><small>{{ text.length }}/500 · {{ session.user?.nickname || session.user?.username }}</small><button :disabled="busy || !text.trim()"><Send :size="15" />{{ busy ? '发送中' : '发布评论' }}</button></div><p v-if="error" role="alert" class="form-error">{{ error }}</p></form>
    </section>
  </div>
</template>
