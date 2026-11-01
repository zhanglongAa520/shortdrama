<script setup>
import { onMounted, ref, watch } from 'vue'
import { Upload, Film, Image, Trash2, X, Check } from 'lucide-vue-next'
import { api } from './api'
const props = defineProps({ picker: Boolean, assetType: String })
const emit = defineEmits(['select', 'close', 'notice'])
const type = ref(props.assetType || '')
const items = ref([])
const busy = ref(false)
const loading = ref(false)
const error = ref('')
const preview = ref(null)
const fileInput = ref(null)
let sequence = 0
async function load() {
  const seq = ++sequence
  loading.value = true
  try { const data = await api(`/admin/assets${type.value ? '?type=' + type.value : ''}`); if (seq === sequence) { items.value = data; error.value = '' } }
  catch (e) { error.value = e.message }
  finally { if (seq === sequence) loading.value = false }
}
onMounted(load)
watch(type, load)
async function upload(event) {
  const file = event.target.files?.[0]
  if (!file) return
  busy.value = true
  error.value = ''
  try {
    const assetType = props.assetType || (file.type.startsWith('image/') ? 'COVER' : 'VIDEO')
    const body = new FormData(); body.append('file', file); body.append('type', assetType)
    const item = await api('/admin/assets', { method: 'POST', body })
    type.value = props.assetType || ''
    await load()
    emit('notice', '素材上传成功')
    if (props.picker) emit('select', item)
  } catch (e) { error.value = e.message }
  finally { busy.value = false; event.target.value = '' }
}
async function remove(item) {
  if (!confirm(`删除素材“${item.originalFilename}”？已被剧目使用的素材无法删除。`)) return
  try { await api(`/admin/assets/${item.id}`, { method: 'DELETE' }); await load() }
  catch (e) { error.value = e.message }
}
function size(value) { return value < 1048576 ? `${(value / 1024).toFixed(0)} KB` : `${(value / 1048576).toFixed(1)} MB` }
</script>
<template>
  <section class="asset-library">
    <div class="page-heading"><div><p class="eyebrow">MEDIA LIBRARY</p><h1>{{ picker ? '选择素材' : '素材库' }}</h1><p class="subheading">上传封面和视频，自动读取视频时长，重复使用已有素材。</p></div><button v-if="picker" class="icon-button" aria-label="关闭素材选择" @click="emit('close')"><X :size="21" /></button></div>
    <div class="feature-toolbar"><select v-if="!assetType" v-model="type" aria-label="素材类型"><option value="">全部素材</option><option value="COVER">封面图片</option><option value="VIDEO">视频</option></select><button class="primary-button" :disabled="busy" @click="fileInput.click()"><Upload :size="16" />{{ busy ? '上传并解析中…' : '上传素材' }}</button><input ref="fileInput" hidden type="file" :accept="assetType === 'COVER' ? 'image/jpeg,image/png,image/webp' : assetType === 'VIDEO' ? 'video/mp4,video/webm' : 'image/jpeg,image/png,image/webp,video/mp4,video/webm'" @change="upload" /><small>封面支持 JPG / PNG / WebP，视频支持 MP4 / WebM</small></div>
    <p v-if="error" class="form-error" role="alert">{{ error }}</p>
    <div class="asset-grid"><article v-for="item in items" :key="item.id" class="asset-card"><button class="asset-thumb" :aria-label="`预览 ${item.originalFilename}`" @click="preview = item"><img v-if="item.type === 'COVER'" :src="item.previewUrl" :alt="item.originalFilename" /><Film v-else :size="36" /><span>{{ item.type === 'COVER' ? '图片' : `${item.durationSeconds || 0} 秒` }}</span></button><strong :title="item.originalFilename">{{ item.originalFilename }}</strong><small>{{ size(item.sizeBytes) }} · #{{ item.id }}</small><div><button v-if="picker" class="secondary-button" @click="emit('select', item)"><Check :size="14" />使用素材</button><button v-else class="secondary-button" @click="preview = item">预览</button><button class="icon-button danger" aria-label="删除素材" @click="remove(item)"><Trash2 :size="16" /></button></div></article></div>
    <div v-if="!items.length" class="empty-admin">{{ loading ? '正在加载…' : '暂无素材，上传后即可用于剧目。' }}</div>
    <div v-if="preview" class="asset-preview" @click.self="preview = null"><button class="icon-button" aria-label="关闭素材预览" @click="preview = null"><X /></button><img v-if="preview.type === 'COVER'" :src="preview.previewUrl" :alt="preview.originalFilename" /><video v-else :src="preview.previewUrl" controls playsinline autoplay /></div>
  </section>
</template>
