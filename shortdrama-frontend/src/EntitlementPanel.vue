<script setup>
import { onMounted, ref } from 'vue'
import { api } from './api'
const props = defineProps({ dramas: Array })
const emit = defineEmits(['notice'])
const users = ref([])
const query = ref('')
const selected = ref(null)
const expiry = ref('')
const episodeId = ref('')
const error = ref('')
const busy = ref(false)
const detail = ref(null)
async function load() { try { users.value = await api('/admin/users'); error.value = '' } catch (e) { error.value = e.message } }
async function select(user) {
  selected.value = user; detail.value = user; episodeId.value = ''
  const date = new Date(Date.now() + 30 * 86400000)
  expiry.value = new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}
async function mutate(path, method, body) {
  busy.value = true; error.value = ''
  try { await api(`/admin/users/${selected.value.id}/${path}`, { method, body }); await load(); detail.value = users.value.find(u => u.id === selected.value.id); emit('notice', '观看权益已更新') }
  catch (e) { error.value = e.message }
  finally { busy.value = false }
}
function grantMembership() {
  const date = new Date(expiry.value)
  if (!Number.isFinite(+date) || date <= new Date()) { error.value = '请选择未来的会员到期时间'; return }
  mutate('membership', 'POST', { expiresAt: date.toISOString() })
}
onMounted(load)
</script>
<template>
  <div class="page-heading"><div><p class="eyebrow">AUDIENCE ACCESS</p><h1>用户与权益</h1><p class="subheading">为观众开通会员或解锁指定剧集，也可撤销权益。</p></div></div>
  <p v-if="error" class="form-error" role="alert">{{ error }}</p>
  <div class="entitlement-layout"><section class="user-list"><input v-model="query" placeholder="搜索用户名或昵称" aria-label="搜索用户" /><button v-for="user in users.filter(u => `${u.username} ${u.nickname}`.includes(query))" :key="user.id" :class="{ selected: selected?.id === user.id }" @click="select(user)"><strong>{{ user.nickname || user.username }}</strong><small>@{{ user.username }} · {{ user.role === 'ADMIN' ? '管理员' : '观众' }}</small></button></section>
    <section v-if="selected" class="entitlement-editor"><h2>{{ selected.nickname || selected.username }}</h2><p>当前会员：{{ detail?.membershipExpiresAt ? `有效期至 ${new Date(detail.membershipExpiresAt).toLocaleString('zh-CN')}` : '未开通' }}</p><label>会员到期时间<input v-model="expiry" type="datetime-local" /></label><div class="feature-toolbar"><button class="primary-button" :disabled="busy" @click="grantMembership">开通 / 调整会员</button><button class="secondary-button" :disabled="busy" @click="mutate('membership', 'DELETE')">撤销会员</button></div><hr /><label>单集解锁<select v-model="episodeId"><option value="">请选择剧集</option><optgroup v-for="drama in dramas" :key="drama.id" :label="drama.title"><option v-for="(ep,index) in drama.episodes" :key="ep.id" :value="ep.id">第 {{ index + 1 }} 集 · {{ ep.title }}{{ ep.free ? '（免费）' : '' }}</option></optgroup></select></label><div class="feature-toolbar"><button class="primary-button" :disabled="busy || !episodeId" @click="mutate(`episode-unlocks/${episodeId}`, 'POST')">解锁本集</button><button class="secondary-button" :disabled="busy || !episodeId" @click="mutate(`episode-unlocks/${episodeId}`, 'DELETE')">撤销本集解锁</button></div><p class="subheading">{{ detail?.unlockedEpisodeIds?.length ? `已解锁剧集 ID：${detail.unlockedEpisodeIds.join('、')}` : '暂无单集解锁记录' }}</p></section>
    <div v-else class="empty-admin">选择用户后管理观看权益</div>
  </div>
</template>
