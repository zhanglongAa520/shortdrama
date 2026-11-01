<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { MessageSquare, Send } from 'lucide-vue-next'
import { api } from './api'

const props = defineProps({ media: Object, episodeKey: String, dramaId: Number, episodeId: Number })
const emit = defineEmits(['notice'])
const rates = [0.5, 0.75, 1, 1.25, 1.5, 2]
const lifetime = 6
function read(key, fallback) {
  try { return JSON.parse(localStorage.getItem(key)) ?? fallback } catch { return fallback }
}
const storedRate = read('yingke-playback-rate', 1)
const rate = ref(rates.includes(storedRate) ? storedRate : 1)
const enabled = ref(read('yingke-danmaku-enabled', true) !== false)
const draft = ref('')
const comments = ref([])
const sending = ref(false)
const loadError = ref('')
const clock = ref(0)
let frame = 0
let detach = () => {}
let requestSequence = 0
const visible = computed(() => enabled.value ? comments.value.filter(comment =>
  clock.value >= comment.at && clock.value < comment.at + lifetime
) : [])

function position(comment) {
  const progress = (clock.value - comment.at) / lifetime
  return { top: `${comment.lane * 32}px`, left: `${100 * (1 - progress)}%`, transform: `translateX(${-100 * progress}%)` }
}
function tick() {
  clock.value = props.media?.currentTime || 0
  if (props.media && !props.media.paused && !props.media.ended) frame = requestAnimationFrame(tick)
}
function startClock() {
  cancelAnimationFrame(frame)
  tick()
}
function syncRate() {
  if (props.media) {
    props.media.defaultPlaybackRate = rate.value
    props.media.playbackRate = rate.value
  }
}
watch(() => props.media, media => {
  detach()
  cancelAnimationFrame(frame)
  if (!media) return
  const update = () => { clock.value = media.currentTime || 0 }
  const ready = () => { syncRate(); update() }
  const listeners = { loadedmetadata: ready, play: startClock, pause: startClock,
    ended: startClock, seeking: update, seeked: update, timeupdate: update, emptied: update }
  Object.entries(listeners).forEach(([event, handler]) => media.addEventListener(event, handler))
  detach = () => Object.entries(listeners).forEach(([event, handler]) => media.removeEventListener(event, handler))
  syncRate()
  startClock()
}, { immediate: true, flush: 'post' })
async function loadComments() {
  if (!props.dramaId || !props.episodeId) return
  const sequence = ++requestSequence
  try {
    const result = await api(`/dramas/${props.dramaId}/interactions?type=DANMAKU&episodeId=${props.episodeId}`)
    if (sequence === requestSequence) { comments.value = result; loadError.value = '' }
  } catch (error) { if (sequence === requestSequence) loadError.value = error.message }
}
watch(() => props.episodeKey, () => {
  ++requestSequence
  comments.value = []
  draft.value = ''
  clock.value = 0
  loadComments()
}, { immediate: true })
const refreshTimer = setInterval(loadComments, 5000)
watch(rate, () => {
  syncRate()
  try { localStorage.setItem('yingke-playback-rate', JSON.stringify(rate.value)) } catch {}
})
watch(enabled, () => {
  try { localStorage.setItem('yingke-danmaku-enabled', JSON.stringify(enabled.value)) } catch {}
})

async function send() {
  const text = draft.value.trim()
  if (!text || sending.value) return
  if (!props.media || props.media.readyState < 2 || props.media.error) return emit('notice', '视频加载完成后可发送弹幕')
  if (props.media.ended) return emit('notice', '重播后可发送弹幕')
  const at = props.media.currentTime
  const lane = [0, 1, 2].find(candidate => !comments.value.some(comment =>
    comment.lane === candidate && Math.abs(comment.at - at) < lifetime
  ))
  if (lane === undefined) return emit('notice', '这个时间点弹幕较多，稍后再发吧')
  sending.value = true
  const key = props.episodeKey
  try {
    const comment = await api(`/dramas/${props.dramaId}/interactions`, { method: 'POST', body: { type: 'DANMAKU', text: text.slice(0, 30), episodeId: props.episodeId, at, lane } })
    if (key === props.episodeKey) {
      comments.value = [...comments.value.filter(item => item.id !== comment.id), comment]
      clock.value = props.media?.currentTime || at
      enabled.value = true
      draft.value = ''
      emit('notice', '弹幕已发送')
    }
  } catch (error) { emit('notice', error.message) }
  finally { sending.value = false }
}
onBeforeUnmount(() => { ++requestSequence; clearInterval(refreshTimer); detach(); cancelAnimationFrame(frame) })
</script>

<template>
  <div class="danmaku-layer" aria-hidden="true">
    <span v-for="comment in visible" :key="comment.id" class="danmaku-comment" :style="position(comment)">{{ comment.text }}</span>
  </div>
  <div class="player-tools" @wheel.stop @touchstart.stop @touchend.stop @click.stop>
    <button class="danmaku-toggle" :class="{ enabled }" :aria-pressed="enabled" :title="enabled ? '关闭弹幕' : '开启弹幕'" :aria-label="enabled ? '关闭弹幕' : '开启弹幕'" @click="enabled = !enabled"><MessageSquare :size="17" /><span>弹</span></button>
    <form class="danmaku-compose" @submit.prevent="send">
      <input v-model="draft" maxlength="30" :placeholder="loadError ? '弹幕重连中…' : '发条弹幕…'" aria-label="弹幕内容" :title="loadError || '最多 30 字，内容需遵守社区规范'" autocomplete="off" @keydown.enter="$event.isComposing && $event.preventDefault()" />
      <button type="submit" :disabled="sending || !draft.trim()" aria-label="发送弹幕" title="发送弹幕"><Send :size="15" /></button>
    </form>
    <select v-model.number="rate" class="playback-rate" aria-label="播放倍速" title="播放倍速">
      <option v-for="value in rates" :key="value" :value="value">{{ value }}×</option>
    </select>
  </div>
</template>
