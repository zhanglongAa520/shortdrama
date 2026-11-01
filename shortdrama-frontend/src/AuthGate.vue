<script setup>
import { ref } from 'vue'
import { ArrowRight, LockKeyhole, UserRound, LoaderCircle } from 'lucide-vue-next'
import { authenticate, session, signOut } from './api'
const props = defineProps({ admin: Boolean })
const mode = ref('login')
const username = ref(props.admin ? 'admin' : '')
const password = ref('')
const nickname = ref('')
const busy = ref(false)
const error = ref('')
async function submit() {
  if (busy.value) return
  error.value = ''
  busy.value = true
  try {
    const user = await authenticate(mode.value, { username: username.value.trim(), password: password.value, nickname: nickname.value.trim() || username.value.trim() })
    password.value = ''
    if (props.admin && user.role !== 'ADMIN') error.value = '当前账号没有后台权限，请使用管理员账号登录'
  } catch (cause) { error.value = cause.message }
  finally { busy.value = false }
}
async function switchAccount() {
  try { await signOut() } catch (cause) { error.value = cause.message }
}
</script>

<template>
  <main class="auth-screen">
    <div class="auth-art"><img src="/posters/after-rain.svg" alt="" /><div><span>YINGKE STORIES</span><h1>每一帧，<br />都是故事。</h1><p>把片刻，留给心动的故事。</p></div></div>
    <section class="auth-panel">
      <a href="/" class="auth-logo"><img src="/brand/yingke-logo-menu.png" alt="映刻 YINGKE" /></a>
      <div v-if="!session.ready" class="auth-loading"><LoaderCircle class="spinning" :size="24" />正在连接…</div>
      <template v-else-if="admin && session.user && session.user.role !== 'ADMIN'">
        <h2>需要管理员账号</h2><p class="auth-subtitle">当前账号 {{ session.user.username }} 只能访问观众端。</p><button class="auth-submit" @click="switchAccount">切换账号</button><a class="auth-back" href="/">返回观看</a>
      </template>
      <template v-else>
        <span class="auth-eyebrow">{{ admin ? 'CREATOR STUDIO' : 'WELCOME TO YINGKE' }}</span>
        <h2>{{ admin ? '登录管理后台' : mode === 'login' ? '欢迎回到映刻' : '开启你的观影之旅' }}</h2>
        <p class="auth-subtitle">{{ admin ? '管理内容与观众互动。' : '登录后观看短剧，留下评论和弹幕。' }}</p>
        <form class="auth-form" @submit.prevent="submit">
          <label>用户名<div><UserRound :size="18" /><input v-model="username" name="username" autocomplete="username" required minlength="3" maxlength="32" pattern="[A-Za-z0-9_]{3,32}" placeholder="3～32 位字母、数字或下划线" /></div></label>
          <label v-if="mode === 'register'">昵称<div><UserRound :size="18" /><input v-model="nickname" autocomplete="nickname" maxlength="40" placeholder="让大家认识你" /></div></label>
          <label>密码<div><LockKeyhole :size="18" /><input v-model="password" name="password" type="password" :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" required minlength="8" maxlength="72" placeholder="至少 8 位" /></div></label>
          <p v-if="error" class="form-error" role="alert">{{ error }}</p>
          <button class="auth-submit" :disabled="busy"><LoaderCircle v-if="busy" class="spinning" :size="17" /><template v-else>{{ mode === 'login' ? '登录' : '注册并登录' }}<ArrowRight :size="17" /></template></button>
        </form>
        <p v-if="!admin" class="auth-switch">{{ mode === 'login' ? '还没有账号？' : '已有账号？' }}<button @click="mode = mode === 'login' ? 'register' : 'login'; error = ''">{{ mode === 'login' ? '立即注册' : '去登录' }}</button></p>
        <a class="auth-back" :href="admin ? '/' : '/admin'">{{ admin ? '返回观众端' : '管理后台' }}</a>
      </template>
    </section>
  </main>
</template>
