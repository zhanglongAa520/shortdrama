<script setup>
import { ref } from 'vue'
import { api } from './api'
const props = defineProps({ dramas: Array })
const emit = defineEmits(['refresh', 'notice'])
const saving = ref(null)
const error = ref('')
const drafts = ref({})
function draft(item) { return drafts.value[item.id] ||= { featured: !!item.featured, recommendationRank: item.recommendationRank || 0 } }
async function save(item) {
  saving.value = item.id; error.value = ''
  try { await api(`/admin/dramas/${item.id}/recommendation`, { method: 'PATCH', body: draft(item) }); emit('refresh'); emit('notice', '推荐设置已保存') }
  catch (e) { error.value = e.message }
  finally { saving.value = null }
}
</script>
<template>
  <div class="page-heading"><div><p class="eyebrow">EDITOR'S PICKS</p><h1>推荐与热播</h1><p class="subheading">配置精选剧目与排序。热播榜按成功播放的独立观众数排列。</p></div></div>
  <p v-if="error" class="form-error" role="alert">{{ error }}</p>
  <div class="recommendation-list"><article v-for="item in dramas" :key="item.id" class="recommendation-row"><img :src="item.coverUrl" :alt="item.title" /><div class="recommendation-info"><strong>{{ item.title }}</strong><small>{{ item.status === 'PUBLISHED' ? '已发布' : '草稿 · 不在观众端展示' }} · {{ item.viewCount || 0 }} 位观众</small></div><label><input v-model="draft(item).featured" type="checkbox" />精选推荐</label><label>排序 <input v-model.number="draft(item).recommendationRank" type="number" min="0" max="9999" :aria-label="`${item.title}推荐排序`" /></label><button class="primary-button" :disabled="saving === item.id" @click="save(item)">保存</button></article></div>
  <p class="subheading">排序数字越小越靠前，未发布的剧目不会进入推荐列表。</p>
</template>
