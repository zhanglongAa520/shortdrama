<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
const props = defineProps({ media: Object, episodeKey: String, autoNext: Boolean })
const emit = defineEmits(['seeked', 'update:autoNext', 'scrubbing'])
const position = ref(0)
const duration = ref(0)
const dragging = ref(false)
const thumbnail = ref(null)
const hasFrame = ref(false)
let resume = false
let detach = () => {}
function time(value) { const sec = Math.max(0, Math.floor(value || 0)); return `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, '0')}` }
function draw() {
  if (!dragging.value || !thumbnail.value || !props.media?.videoWidth) return
  try {
    const canvas = thumbnail.value
    canvas.width = 160; canvas.height = 90
    const video = props.media
    const scale = Math.min(160 / video.videoWidth, 90 / video.videoHeight)
    const ctx = canvas.getContext('2d'); ctx.fillStyle = '#101510'; ctx.fillRect(0, 0, 160, 90)
    ctx.drawImage(video, (160 - video.videoWidth * scale) / 2, (90 - video.videoHeight * scale) / 2, video.videoWidth * scale, video.videoHeight * scale)
    hasFrame.value = true
  } catch { hasFrame.value = false }
}
function begin() {
  if (dragging.value || !props.media || !duration.value) return
  resume = !props.media.paused
  dragging.value = true; hasFrame.value = false
  emit('scrubbing', true)
  props.media.pause()
}
function seek(event) {
  begin()
  position.value = Number(event.target.value)
  if (props.media) props.media.currentTime = position.value
  draw()
}
function finish() {
  if (!dragging.value) return
  dragging.value = false
  emit('scrubbing', false)
  emit('seeked', position.value)
  if (resume) props.media?.play().catch(() => {})
  resume = false
}
watch(() => props.media, media => {
  detach()
  position.value = 0; duration.value = 0; dragging.value = false
  if (!media) return
  const sync = () => { duration.value = Number.isFinite(media.duration) ? media.duration : 0; if (!dragging.value) position.value = media.currentTime || 0 }
  const frame = () => { sync(); draw() }
  const listeners = { loadedmetadata: sync, durationchange: sync, timeupdate: sync, seeked: frame, emptied: sync }
  Object.entries(listeners).forEach(([name, handler]) => media.addEventListener(name, handler))
  detach = () => Object.entries(listeners).forEach(([name, handler]) => media.removeEventListener(name, handler))
  sync()
}, { immediate: true, flush: 'post' })
watch(() => props.episodeKey, () => { dragging.value = false; resume = false; hasFrame.value = false; emit('scrubbing', false) })
window.addEventListener('pointerup', finish)
window.addEventListener('pointercancel', finish)
onBeforeUnmount(() => { detach(); window.removeEventListener('pointerup', finish); window.removeEventListener('pointercancel', finish) })
</script>
<template>
  <div class="playback-scrubber" @click.stop @wheel.stop @touchstart.stop @touchend.stop>
    <div v-show="dragging" class="seek-thumbnail"><canvas ref="thumbnail" v-show="hasFrame" /><span v-if="!hasFrame">正在读取画面</span><strong>{{ time(position) }}</strong></div>
    <div class="scrubber-caption"><span>{{ time(position) }} <small>/ {{ time(duration) }}</small></span><label><input type="checkbox" :checked="autoNext" @change="emit('update:autoNext', $event.target.checked)" />自动连播</label></div>
    <input class="seek-range" type="range" min="0" :max="duration || 1" step="0.1" :value="position" :disabled="!duration" aria-label="播放进度" :aria-valuetext="`${time(position)} / ${time(duration)}`" @pointerdown="begin" @keydown.stop="begin" @input="seek" @change="finish" />
  </div>
</template>
