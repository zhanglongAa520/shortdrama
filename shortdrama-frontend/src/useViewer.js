import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { api, session } from './api'

function preference(key, fallback) { try { return JSON.parse(localStorage.getItem(key)) ?? fallback } catch { return fallback } }
export function useViewer(note) {
  const dramas = ref([]), favorites = ref([]), history = ref([]), recommendations = ref({ featured: [], hot: [] })
  const entitlements = ref(null), activeId = ref(null), episodeIndex = ref(0), activeTab = ref('feed')
  const genre = ref('全部'), query = ref(''), ready = ref(false), apiOnline = ref(true)
  const video = ref(null), playing = ref(false), muted = ref(true), buffering = ref(false), videoError = ref(false)
  const source = ref(''), showEpisodes = ref(false), showSearch = ref(false), showComments = ref(false), showCommerce = ref(false)
  const autoNext = ref(preference('yingke-auto-next', true)), scrubbing = ref(false), holding = ref(false), resumeMessage = ref('')
  const likes = ref([]), socialById = ref({}), socialPending = ref({})
  const socialRequests = new Map()
  let socialSequence = 0
  const social = computed(() => socialById.value[current.value?.id] || null)
  let sequence = 0, bound = null, pendingResume = 0, lastSave = 0, saveQueue = Promise.resolve()
  let holdTimer, holdStart, holdRate = 1, suppressClick = false, wheelLocked = false, gestureActive = false, ignoreTouchEnd = false
  let resumeAfterOverlay = false
  const genres = computed(() => ['全部', ...new Set(dramas.value.map(d => d.genre).filter(Boolean))])
  const filteredDramas = computed(() => dramas.value.filter(d => (genre.value === '全部' || d.genre === genre.value) && (!query.value.trim() || `${d.title} ${d.description} ${d.genre}`.includes(query.value.trim()))))
  const current = computed(() => filteredDramas.value.find(d => d.id === activeId.value) || filteredDramas.value[0])
  const currentEpisode = computed(() => current.value?.episodes?.[episodeIndex.value] || current.value?.episodes?.[0])
  const locked = computed(() => !!currentEpisode.value?.locked)
  const overlay = computed(() => showEpisodes.value || showSearch.value || showComments.value || showCommerce.value)

  async function load() {
    ready.value = false
    socialById.value = {}; socialPending.value = {}; socialRequests.clear()
    try {
      const [catalog, saved, recent, rights, picks] = await Promise.all([api('/dramas'), api('/me/favorites'), api('/me/history'), api('/me/entitlements'), api('/dramas/recommendations')])
      dramas.value = catalog; favorites.value = saved.map(d => d.id); history.value = recent; entitlements.value = rights; recommendations.value = picks
      likes.value = []
      const requested = Number(new URLSearchParams(location.search).get('drama'))
      const first = catalog.find(d => d.id === requested) || catalog.find(d => d.id === recent[0]?.dramaId) || catalog[0]
      activeId.value = first?.id ?? null
      const last = recent.find(h => h.dramaId === first?.id)
      episodeIndex.value = Math.max(0, first?.episodes.findIndex(ep => ep.id === last?.episodeId) ?? 0)
      apiOnline.value = true; ready.value = true
      if (new URLSearchParams(location.search).has('paymentReturn')) { activeTab.value = 'profile'; showCommerce.value = true }
    } catch (e) { apiOnline.value = false; note(e.message); ready.value = true }
  }
  function saveProgress(force = false) {
    const media = video.value, snapshot = bound
    if (!snapshot || !media || media.readyState < 1 || !session.user || scrubbing.value) return saveQueue
    if (!force && Date.now() - lastSave < 5000) return saveQueue
    if (!Number.isFinite(media.currentTime)) return saveQueue
    lastSave = Date.now()
    const userId = session.user.id, progressSeconds = Math.min(media.currentTime, media.duration || media.currentTime)
    const updatedAt = new Date().toISOString()
    const local = { dramaId: snapshot.dramaId, episodeId: snapshot.episodeId, progressSeconds, updatedAt }
    history.value = [local, ...history.value.filter(h => h.dramaId !== local.dramaId)]
    saveQueue = saveQueue.catch(() => {}).then(async () => {
      if (session.user?.id !== userId) return
      try { await api(`/me/history/${snapshot.episodeId}`, { method: 'PUT', body: { progressSeconds } }) }
      catch (e) { if (session.user?.id === userId && force) note(`进度保存失败：${e.message}`) }
    })
    return saveQueue
  }
  async function prepare() {
    endHold()
    saveProgress(true)
    video.value?.pause()
    const request = ++sequence
    bound = null; source.value = ''; playing.value = false; buffering.value = false; videoError.value = false; resumeMessage.value = ''
    if (!ready.value || !session.user || activeTab.value !== 'feed' || !currentEpisode.value) return
    const dramaId = current.value.id, episode = currentEpisode.value
    if (episode.locked) return
    buffering.value = true
    try {
      const result = await api(`/dramas/${dramaId}/episodes/${episode.id}/playback`, { method: 'POST' })
      if (request !== sequence || !session.user) return
      if (result.episode?.id !== episode.id || result.episode.locked || !result.episode.videoUrl) throw new Error('播放信息无效，请重试')
      pendingResume = result.resumeAtSeconds || 0
      bound = { dramaId, episodeId: episode.id }
      lastSave = 0; source.value = result.episode.videoUrl
      await nextTick()
      video.value?.load()
    } catch (e) {
      if (request !== sequence) return
      buffering.value = false
      if (e.status === 403) { episode.locked = true; note('本集需要观看权益，请先解锁') }
      else { videoError.value = true; note(e.message) }
    }
  }
  function metadata() {
    const media = video.value
    if (!media || !bound) return
    if (pendingResume >= 1 && pendingResume < media.duration - 0.8) {
      media.currentTime = pendingResume
      resumeMessage.value = `已续播至 ${Math.floor(pendingResume / 60)}:${String(Math.floor(pendingResume % 60)).padStart(2, '0')}`
    }
    pendingResume = 0
    if (!overlay.value && activeTab.value === 'feed') media.play().catch(() => { buffering.value = false })
  }
  async function playbackStarted() {
    playing.value = true; buffering.value = false
    const snapshot = bound, media = video.value
    if (!snapshot || !media || media.paused || scrubbing.value || overlay.value || activeTab.value !== 'feed'
        || snapshot.episodeId !== currentEpisode.value?.id || session.user?.role !== 'USER'
        || snapshot.reported || snapshot.reporting) return
    snapshot.eventId ||= crypto.randomUUID()
    snapshot.reporting = true
    try {
      await api('/me/playback-events', { method: 'POST', body: { eventId: snapshot.eventId, episodeId: snapshot.episodeId } })
      snapshot.reported = true
    } catch {
      // Playback remains usable if analytics are temporarily unavailable. A later
      // playing event retries this same id, so pause/resume never double counts.
    } finally { snapshot.reporting = false }
  }
  function updateProgress() { if (!scrubbing.value) saveProgress() }
  function paused() { playing.value = false; if (!scrubbing.value) saveProgress(true) }
  async function ended() {
    const finishedId = currentEpisode.value?.id
    playing.value = false
    await saveProgress(true)
    if (finishedId !== currentEpisode.value?.id || !autoNext.value || overlay.value || activeTab.value !== 'feed') return
    if (episodeIndex.value + 1 < current.value.episodes.length) { episodeIndex.value++; note('正在进入下一集') }
    else note('已看完本剧，去发现更多故事吧')
  }
  function selectDrama(id) {
    genre.value = '全部'; query.value = ''
    const drama = dramas.value.find(d => d.id === id)
    const recent = history.value.find(h => h.dramaId === id)
    activeId.value = id
    episodeIndex.value = Math.max(0, drama?.episodes.findIndex(ep => ep.id === recent?.episodeId) ?? 0)
    activeTab.value = 'feed'; showEpisodes.value = false; showSearch.value = false; showComments.value = false
  }
  function chooseEpisode(index) { episodeIndex.value = index; showEpisodes.value = false }
  function moveDrama(direction) {
    const list = filteredDramas.value, index = list.findIndex(d => d.id === current.value?.id)
    if (index < 0 || list.length < 2) return
    const next = list[(index + direction + list.length) % list.length]
    activeId.value = next.id
    const recent = history.value.find(h => h.dramaId === next.id)
    episodeIndex.value = Math.max(0, next.episodes.findIndex(ep => ep.id === recent?.episodeId))
  }
  function onWheel(event) {
    if (overlay.value || wheelLocked || activeTab.value !== 'feed' || scrubbing.value || Math.abs(event.deltaY) < 24) return
    wheelLocked = true; moveDrama(event.deltaY > 0 ? 1 : -1); setTimeout(() => { wheelLocked = false }, 650)
  }
  function holdDown(event) {
    if (event.button != null && event.button !== 0) return
    if (!video.value || locked.value || overlay.value) return
    clearTimeout(holdTimer); holdStart = { x: event.clientX, y: event.clientY }; gestureActive = true; ignoreTouchEnd = false; suppressClick = false
    holdTimer = setTimeout(() => {
      if (!video.value || video.value.paused) return
      holdRate = video.value.playbackRate; video.value.playbackRate = Math.max(2, holdRate); holding.value = true; suppressClick = true
    }, 450)
  }
  function holdMove(event) {
    if (holdStart && Math.hypot(event.clientX - holdStart.x, event.clientY - holdStart.y) > 12) { suppressClick = true; endHold() }
  }
  function endHold() { clearTimeout(holdTimer); if (holding.value && video.value) video.value.playbackRate = holdRate; holding.value = false }
  function onTouchEnd(event) {
    if (ignoreTouchEnd || !gestureActive || overlay.value || activeTab.value !== 'feed' || scrubbing.value) { ignoreTouchEnd = false; gestureActive = false; return }
    const wasHold = holding.value, delta = (holdStart?.y || 0) - event.changedTouches[0].clientY
    endHold(); gestureActive = false
    if (!wasHold && Math.abs(delta) > 65) { suppressClick = true; moveDrama(delta > 0 ? 1 : -1) }
  }
  function cancelGesture() { endHold(); gestureActive = false; ignoreTouchEnd = true; suppressClick = true }
  function togglePlayback() {
    if (suppressClick) { suppressClick = false; return }
    if (!video.value || locked.value || videoError.value) return
    if (video.value.paused) video.value.play().catch(() => note('请再次点击播放'))
    else video.value.pause()
  }
  function toggleSound() { muted.value = !muted.value; if (video.value) { video.value.muted = muted.value; if (!muted.value && !video.value.volume) video.value.volume = 0.8 } }
  function applySocial(result) {
    socialById.value[result.dramaId] = result
    likes.value = result.liked ? [...new Set([...likes.value, result.dramaId])] : likes.value.filter(id => id !== result.dramaId)
    favorites.value = result.favorited ? [...new Set([...favorites.value, result.dramaId])] : favorites.value.filter(id => id !== result.dramaId)
  }
  function socialRequestCurrent(id, request, userId) { return session.user?.id === userId && socialRequests.get(id) === request }
  async function refreshSocial(id = current.value?.id) {
    if (!ready.value || !session.user || !id || socialPending.value[id]) return
    const userId = session.user.id, request = ++socialSequence
    socialRequests.set(id, request)
    try {
      const result = await api(`/dramas/${id}/social`)
      if (socialRequestCurrent(id, request, userId)) applySocial(result)
    } catch { /* Unknown counts stay unavailable; never substitute made-up zeros. */ }
  }
  function updateCommentCount({ dramaId, count }) {
    if (socialById.value[dramaId]) socialById.value[dramaId].commentCount = count
    refreshSocial(dramaId)
  }
  async function toggleId(type, id) {
    if (!session.user || socialPending.value[id] || (type === 'likes' && !socialById.value[id])) return
    const userId = session.user.id, request = ++socialSequence
    socialRequests.set(id, request); socialPending.value[id] = true
    const exists = type === 'likes' ? likes.value.includes(id) : favorites.value.includes(id)
    try {
      if (type === 'likes') {
        const result = await api(`/me/likes/${id}`, { method: exists ? 'DELETE' : 'PUT' })
        if (socialRequestCurrent(id, request, userId)) applySocial(result)
      } else {
        await api(`/me/favorites/${id}`, { method: exists ? 'DELETE' : 'POST' })
        if (!socialRequestCurrent(id, request, userId)) return
        favorites.value = exists ? favorites.value.filter(value => value !== id) : [...new Set([...favorites.value, id])]
        if (socialById.value[id]) socialById.value[id].favoriteCount = null
        note(exists ? '已取消追剧' : '已加入追剧')
      }
    } catch (error) { if (socialRequestCurrent(id, request, userId)) note(error.message) }
    finally {
      if (socialRequestCurrent(id, request, userId)) {
        delete socialPending.value[id]
        await refreshSocial(id)
      }
    }
  }
  async function removeHistory(id) {
    if (id == null && !confirm('确定清空本账号的全部观看历史？')) return
    await saveQueue
    try { await api(id == null ? '/me/history' : `/me/history/${id}`, { method: 'DELETE' }); history.value = id == null ? [] : history.value.filter(h => h.dramaId !== id); note('观看记录已删除') }
    catch (e) { note(e.message) }
  }
  async function refreshRights() {
    try {
      const [rights, catalog] = await Promise.all([api('/me/entitlements'), api('/dramas')])
      entitlements.value = rights; dramas.value = catalog
      showCommerce.value = false
      await prepare()
    } catch (e) { note(`权益刷新失败：${e.message}`) }
  }
  async function refreshCollection() {
    if (!ready.value || !session.user || activeTab.value === 'feed') return
    await saveQueue
    try { const [recent, saved, picks, rights] = await Promise.all([api('/me/history'), api('/me/favorites'), api('/dramas/recommendations'), api('/me/entitlements')]); history.value = recent; favorites.value = saved.map(d => d.id); recommendations.value = picks; entitlements.value = rights }
    catch (e) { note(e.message) }
  }
  function clear() { ++sequence; bound = null; ready.value = false; source.value = ''; dramas.value = []; favorites.value = []; history.value = []; likes.value = []; socialById.value = {}; socialPending.value = {}; socialRequests.clear(); entitlements.value = null; activeId.value = null; episodeIndex.value = 0; showCommerce.value = false; showEpisodes.value = false; showSearch.value = false; showComments.value = false; activeTab.value = 'feed'; endHold() }
  watch([() => currentEpisode.value?.id, activeTab, ready], prepare)
  watch(activeTab, refreshCollection)
  watch([() => current.value?.id, activeTab, ready], () => { if (activeTab.value === 'feed') refreshSocial() })
  const socialTimer = setInterval(() => { if (!document.hidden && activeTab.value === 'feed') refreshSocial() }, 20000)
  watch(overlay, open => {
    if (open) { resumeAfterOverlay = !!video.value && !video.value.paused; video.value?.pause() }
    else if (resumeAfterOverlay && activeTab.value === 'feed' && !locked.value) video.value?.play().catch(() => {})
  })
  watch(autoNext, value => localStorage.setItem('yingke-auto-next', JSON.stringify(value)))
  const visibility = () => { if (document.hidden) { endHold(); saveProgress(true) } else { refreshCollection(); if (activeTab.value === 'feed') refreshSocial() } }
  const blur = () => endHold()
  document.addEventListener('visibilitychange', visibility)
  window.addEventListener('blur', blur)
  window.addEventListener('pointerup', endHold)
  onBeforeUnmount(() => { clearInterval(socialTimer); saveProgress(true); endHold(); document.removeEventListener('visibilitychange', visibility); window.removeEventListener('blur', blur); window.removeEventListener('pointerup', endHold) })
  return { dramas, favorites, history, recommendations, entitlements, activeId, episodeIndex, activeTab, genre, query, apiOnline, video, playing, muted, buffering, videoError, source, showEpisodes, showSearch, showComments, showCommerce, autoNext, scrubbing, holding, resumeMessage, likes, social, socialPending, refreshSocial, updateCommentCount, genres, filteredDramas, current, currentEpisode, locked, load, saveProgress, prepare, metadata, playbackStarted, updateProgress, paused, ended, selectDrama, chooseEpisode, moveDrama, onWheel, onTouchEnd, holdDown, holdMove, endHold, cancelGesture, togglePlayback, toggleSound, toggleId, removeHistory, refreshRights, clear }
}
