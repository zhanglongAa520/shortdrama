<script setup>
import { computed } from 'vue'
import { Play, Bookmark, Trash2, Search, Crown } from 'lucide-vue-next'
const props = defineProps({ tab: String, dramas: Array, favorites: Array, history: Array, recommendations: Object, user: Object, entitlements: Object, genre: String, query: String })
const emit = defineEmits(['select', 'remove-favorite', 'remove-history', 'clear-history', 'search', 'genre', 'logout', 'membership'])
const genres = computed(() => ['全部', ...new Set(props.dramas.map(d => d.genre))])
const filtered = computed(() => props.dramas.filter(d => (props.genre === '全部' || d.genre === props.genre) && (!props.query?.trim() || `${d.title} ${d.description} ${d.genre}`.includes(props.query.trim()))))
const favoriteItems = computed(() => props.dramas.filter(d => props.favorites.includes(d.id)))
const histories = computed(() => {
  const seen = new Set()
  return props.history.filter(h => { if (seen.has(h.dramaId)) return false; seen.add(h.dramaId); return true }).map(h => ({ ...h, drama: props.dramas.find(d => d.id === h.dramaId) })).filter(h => h.drama)
})
const featured = computed(() => props.recommendations?.featured || [])
const hot = computed(() => props.recommendations?.hot || [])
function progressText(h) { const drama = h.drama; const index = drama.episodes.findIndex(ep => ep.id === h.episodeId); const seconds = Math.floor(h.progressSeconds || 0); return `看到第 ${index + 1} 集 · ${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}` }
</script>
<template>
  <div class="collection-view">
    <div v-if="tab === 'profile'" class="account-bar"><span>{{ user?.nickname || user?.username }} <small>@{{ user?.username }}</small></span><button @click="emit('logout')">退出登录</button></div>
    <div v-if="tab === 'profile'" class="membership-card"><Crown :size="24" /><div><strong>{{ entitlements?.membershipActive ? '映刻会员' : '开启更多好故事' }}</strong><small>{{ entitlements?.membershipActive ? `有效期至 ${new Date(entitlements.membershipExpiresAt).toLocaleDateString('zh-CN')}` : '会员可观看非免费剧集，单集也可独立解锁。' }}</small></div><button @click="emit('membership')">{{ entitlements?.membershipActive ? '查看权益' : '了解会员' }}</button></div>
    <header class="collection-header"><div><span class="eyebrow">YINGKE STORIES</span><h1>{{ tab === 'explore' ? '发现好故事' : tab === 'favorites' ? '我的追剧' : '观看历史' }}</h1><p>{{ tab === 'explore' ? '精选与热播，从这里开始。' : '登录账号后，换一台设备也能接着看。' }}</p></div><button class="collection-search" title="搜索" @click="emit('search')"><Search :size="20" /></button></header>
    <template v-if="tab === 'explore'">
      <section v-if="featured.length" class="theater-section"><h2>精选推荐 <small>值得留出时间的故事</small></h2><div class="featured-strip"><button v-for="item in featured" :key="item.id" @click="emit('select', item.id)"><img :src="item.coverUrl" :alt="item.title" /><span><small>映刻精选 · {{ item.genre }}</small><strong>{{ item.title }}</strong><p>{{ item.description }}</p></span></button></div></section>
      <section v-if="hot.length" class="theater-section"><h2>热播榜 <small>按独立观众数排序</small></h2><div class="hot-ranking"><button v-for="(item,index) in hot.slice(0, 6)" :key="item.id" @click="emit('select', item.id)"><b>{{ String(index + 1).padStart(2, '0') }}</b><img :src="item.coverUrl" :alt="item.title" /><span><strong>{{ item.title }}</strong><small>{{ item.viewCount || 0 }} 位观众 · {{ item.genre }}</small></span><Play :size="16" /></button></div></section>
      <div class="genre-pills"><button v-for="name in genres" :key="name" :class="{selected: genre === name}" @click="emit('genre', name)">{{ name }}</button></div>
      <div class="poster-grid"><button v-for="item in filtered" :key="item.id" class="poster-tile" @click="emit('select', item.id)"><img :src="item.coverUrl" :alt="item.title" /><span class="poster-tile-shade"></span><span class="poster-tile-info"><small>{{ item.genre }} · {{ item.episodes.length }} 集</small><strong>{{ item.title }}</strong></span><span class="tile-play"><Play :size="18" /></span></button></div>
    </template>
    <template v-else-if="tab === 'favorites'"><div class="personal-grid"><article v-for="item in favoriteItems" :key="item.id" class="personal-item"><button @click="emit('select', item.id)"><img :src="item.coverUrl" :alt="item.title" /><span><strong>{{ item.title }}</strong><small>{{ item.genre }} · {{ item.episodes.length }} 集</small></span></button><button class="personal-remove" :aria-label="`取消追剧 ${item.title}`" @click="emit('remove-favorite', item.id)"><Bookmark :size="16" />取消追剧</button></article></div><div v-if="!favoriteItems.length" class="collection-empty">还没有追剧，在播放页收藏喜欢的故事吧。</div></template>
    <template v-else><div v-if="histories.length" class="history-toolbar"><span>{{ histories.length }} 部短剧</span><button @click="emit('clear-history')">清空历史</button></div><div class="personal-grid"><article v-for="h in histories" :key="h.dramaId" class="personal-item"><button @click="emit('select', h.dramaId)"><img :src="h.drama.coverUrl" :alt="h.drama.title" /><span><strong>{{ h.drama.title }}</strong><small>{{ progressText(h) }}</small></span></button><button class="personal-remove" :aria-label="`删除历史 ${h.drama.title}`" @click="emit('remove-history', h.dramaId)"><Trash2 :size="15" />删除记录</button></article></div><div v-if="!histories.length" class="collection-empty">暂无观看记录，看过的故事会保存在这里。</div></template>
  </div>
</template>
