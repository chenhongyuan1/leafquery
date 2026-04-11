<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useFavoritesStore } from '../../stores/favoritesCloud'
import { getStoredUser } from '../../utils/accountSecurity'

const router = useRouter()
const favStore = useFavoritesStore()

const favoritesList = computed(() => favStore.favoriteItems)

// 进入收藏页时刷新收藏数据
onMounted(async () => {
  const user = getStoredUser()
  if (user?.userId) {
    await favStore.loadFavorites(user.userId)
  }
})

const goBack = () => {
  router.back()
}

// 点击收藏项查看详情 — 跳转到发现页
const handleItemClick = (item) => {
  const tabMap = { news: 0, library: 1, qna: 2 }
  const tab = tabMap[item.itemType] ?? 0
  router.push({ path: '/discovery', query: { tab: String(tab) } })
}

// 格式化时间显示
const formatTime = (ts) => {
  if (!ts) return ''
  const date = new Date(ts)
  const now = new Date()
  const diff = now - date

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

const getTagColor = (type) => {
  switch (type) {
    case 'news': return 'bg-blue-100 text-blue-600'
    case 'library': return 'bg-emerald-100 text-emerald-600'
    case 'qna': return 'bg-orange-100 text-orange-600'
    default: return 'bg-slate-100 text-slate-600'
  }
}

const getTagText = (type) => {
  switch (type) {
    case 'news': return '资讯'
    case 'library': return '图鉴'
    case 'qna': return '问答'
    default: return '其他'
  }
}

const handleUnlike = async (item) => {
   // Use dedicated removeFavorite (always removes, no toggle ambiguity)
   await favStore.removeFavorite(item)
}

</script>

<template>
  <div class="favorites-page px-4 pt-1 pb-8 min-h-screen flex flex-col relative bg-slate-50">
    <!-- Header -->
    <div class="flex items-center mb-6 pt-4 sticky top-0 z-10 bg-slate-50/90 backdrop-blur-md pb-4">
      <button @click="goBack" class="w-10 h-10 flex items-center justify-center bg-white rounded-full shadow-sm text-slate-600 active:scale-90 transition-transform relative z-10">
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-5 h-5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 19.5L8.25 12l7.5-7.5" />
        </svg>
      </button>
      <h2 class="text-xl font-bold text-slate-800 absolute left-1/2 -translate-x-1/2 w-full text-center pointer-events-none">我的收藏</h2>
    </div>

    <!-- 收藏列表 -->
    <div v-if="favoritesList.length > 0" class="flex-1 overflow-y-auto space-y-4 pb-12">
      <transition-group name="list" tag="div" class="space-y-4">
        <div 
          v-for="item in favoritesList" 
          :key="item.id || (item.itemType + '_' + item.itemId)" 
          class="bg-white rounded-2xl overflow-hidden shadow-sm border border-slate-100 p-4 flex gap-4 active:scale-[0.98] transition-all relative group cursor-pointer"
          @click="handleItemClick(item)"
        >
          <!-- 取消收藏按钮 (star icon matching Discovery page) -->
          <button 
             @click.stop="handleUnlike(item)"
             :disabled="favStore.isFavoriteSubmitting"
             class="absolute top-2 right-2 z-10 w-8 h-8 rounded-full bg-amber-50 flex items-center justify-center active:scale-90 transition-transform disabled:opacity-50 disabled:active:scale-100"
          >
             <span class="text-sm text-amber-500">⭐</span>
          </button>

          <!-- 缩略图 -->
          <div v-if="item.imageUrl" class="w-24 h-24 shrink-0 rounded-xl overflow-hidden bg-slate-100 border border-slate-100 relative">
             <img :src="item.imageUrl" alt="" class="w-full h-full object-cover">
          </div>
          
          <!-- 内容区 -->
          <div class="flex-1 flex flex-col justify-between overflow-hidden relative pr-4">
            <div>
              <div class="flex items-center space-x-2 mb-1.5">
                <span class="px-2 py-0.5 rounded text-[10px] font-bold tracking-wider" :class="getTagColor(item.itemType)">{{ getTagText(item.itemType) }}</span>
              </div>
              <h3 class="font-bold text-slate-800 text-sm leading-snug line-clamp-2 mb-1">{{ item.title }}</h3>
              <p v-if="item.description" class="text-xs text-slate-400 line-clamp-1">{{ item.description }}</p>
            </div>
            
            <div class="text-[10px] text-slate-400 mt-2 flex items-center justify-between">
              <span>收藏于: {{ formatTime(item.createTime || new Date()) }}</span>
            </div>
          </div>
        </div>
      </transition-group>
    </div>

    <!-- 空状态 -->
    <div v-else class="flex-1 flex flex-col items-center justify-center -mt-20">
      <div class="w-40 h-40 bg-slate-100 rounded-full flex items-center justify-center mb-6 shadow-inner relative overflow-hidden">
         <span class="text-6xl text-slate-300 relative z-10 mix-blend-multiply">🗂️</span>
      </div>
      <h3 class="text-lg font-bold text-slate-700">暂无收藏</h3>
      <p class="text-sm text-slate-400 mt-2 text-center max-w-[200px]">您还没有收藏任何内容，去发现页逛逛吧</p>
    </div>

  </div>
</template>

<style scoped>
.list-move, .list-enter-active, .list-leave-active { transition: all 0.4s ease; }
.list-enter-from, .list-leave-to { opacity: 0; transform: translateX(30px) scale(0.9); }
.list-leave-active { position: absolute; }

:global(.dark) .favorites-page {
  background:
    radial-gradient(circle at top, rgba(16, 185, 129, 0.16), transparent 34%),
    linear-gradient(180deg, #020617 0%, #0f172a 42%, #111827 100%);
}

:global(.dark) .favorites-page :deep([class~='bg-white']),
:global(.dark) .favorites-page :deep([class~='bg-slate-50/90']) {
  background-color: rgba(15, 23, 42, 0.88) !important;
}

:global(.dark) .favorites-page :deep([class~='bg-slate-50']) {
  background-color: rgba(30, 41, 59, 0.78) !important;
}

:global(.dark) .favorites-page :deep([class~='bg-slate-100']) {
  background-color: rgba(51, 65, 85, 0.82) !important;
}

:global(.dark) .favorites-page :deep([class~='bg-amber-50']) {
  background-color: rgba(245, 158, 11, 0.16) !important;
}

:global(.dark) .favorites-page :deep([class~='border-slate-100']) {
  border-color: rgba(71, 85, 105, 0.76) !important;
}

:global(.dark) .favorites-page :deep([class~='text-slate-900']),
:global(.dark) .favorites-page :deep([class~='text-slate-800']) {
  color: #f8fafc !important;
}

:global(.dark) .favorites-page :deep([class~='text-slate-700']),
:global(.dark) .favorites-page :deep([class~='text-slate-600']) {
  color: #e2e8f0 !important;
}

:global(.dark) .favorites-page :deep([class~='text-slate-500']),
:global(.dark) .favorites-page :deep([class~='text-slate-400']),
:global(.dark) .favorites-page :deep([class~='text-slate-300']) {
  color: #94a3b8 !important;
}
</style>
