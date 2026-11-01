import { reactive } from 'vue'

export const session = reactive({ ready: false, user: null })
let csrf = null
export async function api(path, options = {}) {
  const method = options.method || 'GET'
  const headers = { ...options.headers }
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    if (!csrf) {
      const response = await fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store' })
      if (!response.ok) throw new Error('无法建立安全会话，请刷新重试')
      csrf = await response.json()
    }
    headers[csrf.headerName] = csrf.token
  }
  let body = options.body
  if (body && typeof body !== 'string' && !(body instanceof FormData)) { body = JSON.stringify(body); headers['Content-Type'] = 'application/json' }
  const response = await fetch(`/api${path}`, { ...options, method, headers, body, credentials: 'same-origin', cache: 'no-store' })
  if (!response.ok) {
    let data
    try { data = await response.json() } catch {}
    if (response.status === 401 && !path.startsWith('/auth/')) session.user = null
    const error = new Error(data?.message || data?.detail || (response.status === 401 ? '请先登录' : response.status === 403 ? '没有操作权限，或会话已失效，请重新登录' : '请求失败，请重试'))
    error.status = response.status
    if (response.status === 403) csrf = null
    throw error
  }
  return response.status === 204 ? null : response.json()
}
export async function checkSession() {
  try { session.user = await api('/auth/me') }
  catch (error) { if (error.status !== 401) throw error; session.user = null }
  finally { session.ready = true }
}
export async function authenticate(mode, credentials) {
  const user = await api(`/auth/${mode}`, { method: 'POST', body: credentials })
  csrf = null
  session.user = user
  return user
}
export async function signOut() {
  await api('/auth/logout', { method: 'POST' })
  csrf = null
  session.user = null
}
