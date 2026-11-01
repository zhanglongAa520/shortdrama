<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { CreditCard, Eye, EyeOff, Save, ShieldCheck } from 'lucide-vue-next'
import { api } from './api'

const emit = defineEmits(['notice'])
const channels = [
  { id: 'WECHAT', title: '微信支付', subtitle: 'H5 支付', fields: [
    { key: 'appId', label: '应用 App ID' }, { key: 'mchId', label: '商户号' },
    { key: 'merchantSerialNo', label: '商户 API 证书序列号' },
    { key: 'apiV3Key', label: 'API v3 密钥', secret: true, hint: '填写微信商户平台设置的 32 位 API v3 密钥。' },
    { key: 'privateKey', label: '商户 API 私钥', secret: true, multiline: true, hint: '粘贴 apiclient_key.pem 的完整内容，包含 BEGIN / END 标记。' },
    { key: 'notifyUrl', label: '支付回调地址', placeholder: 'https://你的域名/api/payments/callbacks/wechat', type: 'url' },
  ] },
  { id: 'ALIPAY', title: '支付宝', subtitle: '手机网站支付', fields: [
    { key: 'appId', label: '应用 App ID' }, { key: 'sellerId', label: '卖家支付宝用户 ID' },
    { key: 'gateway', label: '支付环境', options: [{ value: 'PRODUCTION', label: '正式环境' }, { value: 'SANDBOX', label: '沙箱环境' }] },
    { key: 'privateKey', label: '应用私钥（RSA2）', secret: true, multiline: true },
    { key: 'publicKey', label: '支付宝公钥', secret: true, multiline: true, hint: '填写支付宝公钥，须与当前应用及支付环境匹配。' },
    { key: 'notifyUrl', label: '支付回调地址', placeholder: 'https://你的域名/api/payments/callbacks/alipay', type: 'url' },
  ] },
  ...['ANDROID', 'IOS'].map(platform => ({ id: `CSJ_${platform}`, title: `穿山甲 · ${platform === 'ANDROID' ? 'Android' : 'iOS'}`, subtitle: '激励视频广告', fields: [
    { key: 'appId', label: '应用 App ID' }, { key: 'placementId', label: '激励视频广告位 ID' },
    { key: 'securityKey', label: '服务端奖励验证 Security Key', secret: true },
    { key: 'rewardName', label: '奖励名称', hint: '须与穿山甲广告位的奖励名称一致。' },
    { key: 'rewardAmount', label: '奖励数量', type: 'number', hint: '须与广告位的奖励数量一致，填写正整数。' },
  ] })),
]
const active = ref('WECHAT'), loading = ref(true), saving = ref(false), loadError = ref('')
const forms = reactive({})
const channel = computed(() => channels.find(item => item.id === active.value))
const form = computed(() => forms[active.value])
function assign(data) {
  const definition = channels.find(item => item.id === data.provider)
  forms[data.provider] = { enabled: data.enabled, configured: data.configured,
    values: Object.fromEntries(definition.fields.map(field => [field.key, field.secret ? '' : (data.values?.[field.key] ?? (field.options ? 'PRODUCTION' : ''))])),
    secretFieldsSet: data.secretFieldsSet || [], clearSecrets: [], reveal: {}, error: '', saved: false }
}
async function load() {
  loading.value = true; loadError.value = ''
  try { const results = await Promise.all(channels.map(item => api(`/admin/integrations/${item.id}`))); results.forEach(assign) }
  catch (error) { loadError.value = error.message }
  finally { loading.value = false }
}
function clearSecret(field) {
  if (form.value.clearSecrets.includes(field)) form.value.values[field] = ''
}
async function save() {
  const provider = active.value, draft = forms[provider]
  saving.value = true; draft.error = ''; draft.saved = false
  try {
    const data = await api(`/admin/integrations/${provider}`, { method: 'PUT', body: { enabled: draft.enabled, values: Object.fromEntries(Object.entries(draft.values).map(([key, value]) => [key, String(value ?? '')])), clearSecrets: [...draft.clearSecrets] } })
    assign(data); forms[provider].saved = true
    emit('notice', `${channels.find(item => item.id === provider).title}配置已保存`)
  } catch (error) { draft.error = error.message }
  finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <div class="page-heading"><div><p class="eyebrow">PAYMENTS & REWARDED ADS</p><h1>支付与广告配置</h1><p class="subheading">管理支付渠道和双端激励广告，保存后立即生效。</p></div><ShieldCheck :size="27" /></div>
  <p v-if="loading" class="subheading" role="status">正在读取渠道配置…</p>
  <div v-else-if="loadError" class="integration-load-error"><p class="form-error" role="alert">{{ loadError }}</p><button class="secondary-button" @click="load">重新加载</button></div>
  <div v-else class="integration-layout">
    <nav class="integration-tabs" aria-label="支付与广告渠道">
      <button v-for="item in channels" :key="item.id" :class="{ selected: active === item.id }" :aria-pressed="active === item.id" @click="active = item.id">
        <CreditCard :size="18" /><span><strong>{{ item.title }}</strong><small>{{ item.subtitle }}</small></span>
        <span class="integration-dot" :class="{ enabled: forms[item.id]?.enabled && forms[item.id]?.configured }"></span>
      </button>
    </nav>
    <form v-if="form" :key="active" class="admin-panel integration-form" @submit.prevent="save">
      <header class="integration-heading"><div><h2>{{ channel.title }}</h2><p>{{ channel.subtitle }}</p></div><span class="status-pill" :class="form.enabled && form.configured ? 'published' : 'draft'">{{ form.enabled && form.configured ? '已启用' : form.enabled ? '配置待完善' : '未启用' }}</span></header>
      <label class="integration-switch"><input v-model="form.enabled" type="checkbox" :disabled="saving" /><span>启用{{ channel.title }}<small>确认参数完整并已完成平台开通后，再启用渠道。</small></span></label>
      <p class="integration-secret-note">密钥保存后不回显。留空保留原值；需要移除时勾选“清除已保存值”。</p>
      <div class="integration-fields">
        <div v-for="field in channel.fields" :key="field.key" class="integration-field" :class="{ wide: field.secret || field.key === 'notifyUrl' }">
          <label :for="`integration-${active}-${field.key}`">{{ field.label }}<span v-if="field.secret" class="integration-secret-state">{{ form.secretFieldsSet.includes(field.key) ? '已设置' : '未设置' }}</span></label>
          <select v-if="field.options" :id="`integration-${active}-${field.key}`" v-model="form.values[field.key]" :disabled="saving"><option v-for="option in field.options" :key="option.value" :value="option.value">{{ option.label }}</option></select>
          <div v-else class="integration-input-wrap">
            <textarea v-if="field.multiline && form.reveal[field.key]" :id="`integration-${active}-${field.key}`" v-model="form.values[field.key]" rows="5" :disabled="saving || form.clearSecrets.includes(field.key)" autocomplete="off" spellcheck="false" placeholder="粘贴完整密钥内容"></textarea>
            <input v-else :id="`integration-${active}-${field.key}`" v-model="form.values[field.key]" :type="field.secret && !form.reveal[field.key] ? 'password' : field.type || 'text'" :min="field.type === 'number' ? 1 : undefined" :step="field.type === 'number' ? 1 : undefined" :placeholder="field.secret ? (form.secretFieldsSet.includes(field.key) ? '已设置，留空保留' : '尚未设置，请填写') : field.placeholder" :disabled="saving || form.clearSecrets.includes(field.key)" autocomplete="off" spellcheck="false" />
            <button v-if="field.secret" type="button" class="integration-reveal" :aria-label="`${form.reveal[field.key] ? '隐藏' : '显示'}${field.label}`" @click="form.reveal[field.key] = !form.reveal[field.key]"><EyeOff v-if="form.reveal[field.key]" :size="17" /><Eye v-else :size="17" /></button>
          </div>
          <small v-if="field.hint" class="integration-field-hint">{{ field.hint }}</small>
          <label v-if="field.secret && form.secretFieldsSet.includes(field.key)" class="integration-clear"><input v-model="form.clearSecrets" :value="field.key" type="checkbox" :disabled="saving" @change="clearSecret(field.key)" />清除已保存值</label>
        </div>
      </div>
      <p v-if="active.startsWith('CSJ_')" class="integration-info">穿山甲平台的服务端奖励回调地址为你的公网 HTTPS 域名加 <code>/api/rewards/csj/callback</code>。Android / iOS 使用各自的应用和广告位配置；实际展示需在 App 中集成原生 SDK。</p>
      <p v-else class="integration-info">支付回调需使用可被支付平台访问的公网 HTTPS 地址。保存仅校验配置格式，真实支付仍需商户开通和渠道联调。</p>
      <p v-if="form.error" class="form-error" role="alert">{{ form.error }}</p>
      <p v-if="form.saved" class="integration-success" role="status">配置已保存并生效。</p>
      <footer class="integration-footer"><button class="primary-button" type="submit" :disabled="saving"><Save :size="17" />{{ saving ? '保存中…' : '保存配置' }}</button></footer>
    </form>
  </div>
</template>
