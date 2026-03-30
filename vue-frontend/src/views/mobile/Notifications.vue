<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import dayjs from 'dayjs'

const router = useRouter()
const announcements = ref([])
const loading = ref(true)
const selectedItem = ref(null)
const readIds = ref(JSON.parse(localStorage.getItem('read_announcements') || '[]'))

const isRead = (id) => readIds.value.includes(id)

const unreadCount = computed(() => announcements.value.filter(a => !isRead(a.id)).length)

function markAsRead(item) {
  if (!isRead(item.id)) {
    readIds.value.push(item.id)
    localStorage.setItem('read_announcements', JSON.stringify(readIds.value))
  }
  selectedItem.value = item
}

const typeLabel = (type) => {
  const map = { system: '系统', update: '更新', event: '活动' }
  return map[type] || '通知'
}

const typeColor = (type) => {
  if (type === 'system') return 'bg-red-50 text-red-600 border-red-200'
  if (type === 'update') return 'bg-blue-50 text-blue-600 border-blue-200'
  if (type === 'event') return 'bg-purple-50 text-purple-600 border-purple-200'
  return 'bg-slate-50 text-slate-600 border-slate-200'
}

const formatDate = (dateString) => {
  return dayjs(dateString).format('YYYY-MM-DD HH:mm')
}

const formatContent = (content) => {
  if (!content) return ''
  return content.replace(/\n/g, '<br/>')
}

onMounted(async () => {
  try {
    const { data } = await axios.get('/api/discovery/announcements')
    if (data.code === 200) {
      announcements.value = data.data
    }
  } catch (err) {
    console.error('Failed to load announcements', err)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="notifications-page min-h-screen bg-slate-50 dark:bg-slate-950 flex flex-col pb-8 transition-colors">
    <!-- Header -->
    <div class="px-5 py-4 bg-white dark:bg-slate-900 sticky top-0 z-30 shadow-sm dark:shadow-[0_12px_32px_rgba(2,6,23,0.35)] flex items-center justify-between transition-colors">
      <div class="flex items-center">
        <button @click="router.back()" class="w-10 h-10 -ml-2 flex items-center justify-center text-slate-800 dark:text-slate-100 active:bg-slate-100 dark:active:bg-slate-800 rounded-full transition-colors mr-2">
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-6 h-6">
            <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 19.5L8.25 12l7.5-7.5" />
          </svg>
        </button>
        <h1 class="text-xl font-bold text-slate-900">消息通知</h1>
      </div>
      <span v-if="unreadCount > 0" class="text-xs font-bold text-white bg-red-500 rounded-full px-2 py-0.5">{{ unreadCount }} 条未读</span>
    </div>

    <!-- Content -->
    <div class="px-5 pt-6 flex-1 max-w-2xl mx-auto w-full">
      <div v-if="loading" class="flex flex-col items-center justify-center py-20">
        <div class="w-10 h-10 border-4 border-slate-100 border-t-green-500 rounded-full animate-spin mb-4"></div>
        <div class="text-slate-400 text-sm font-medium">加载中...</div>
      </div>

      <div v-else-if="announcements.length === 0" class="flex flex-col items-center justify-center py-24 text-center">
        <div class="w-20 h-20 bg-slate-100 rounded-full flex items-center justify-center text-4xl mb-4">📭</div>
        <h3 class="text-slate-800 font-bold text-lg">暂无通知</h3>
        <p class="text-slate-500 text-sm mt-1">目前没有任何系统公告</p>
      </div>

      <div v-else class="space-y-4">
        <div 
          v-for="a in announcements" 
          :key="a.id" 
          @click="markAsRead(a)"
          class="rounded-2xl p-5 shadow-[0_4px_20px_rgb(0,0,0,0.03)] dark:shadow-[0_12px_36px_rgba(2,6,23,0.3)] border transition-all hover:shadow-md cursor-pointer active:scale-[0.98]"
          :class="isRead(a.id) ? 'bg-white dark:bg-slate-900 border-slate-100 dark:border-slate-800' : 'bg-blue-50/30 dark:bg-sky-500/10 border-blue-100 dark:border-sky-500/20'"
        >
          <div class="flex justify-between items-start mb-3">
            <div class="flex items-center gap-2">
              <!-- 未读蓝点 -->
              <span v-if="!isRead(a.id)" class="w-2.5 h-2.5 bg-red-500 rounded-full shrink-0 shadow-sm shadow-red-500/50"></span>
              <span class="text-xs font-bold px-2 py-0.5 rounded border shrink-0" :class="typeColor(a.type)">
                {{ typeLabel(a.type) }}
              </span>
              <h2 class="font-bold leading-tight line-clamp-1" :class="isRead(a.id) ? 'text-slate-500 text-base' : 'text-slate-900 text-lg'">{{ a.title }}</h2>
            </div>
            <span class="text-slate-400 font-bold ml-2">›</span>
          </div>
          <div class="text-xs text-slate-400 font-medium mb-3 flex items-center gap-2 border-b border-slate-50 pb-3">
            <span class="flex items-center gap-1">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3.5 h-3.5"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z" /></svg>
              {{ a.adminName || '管理员' }}
            </span>
            <span>•</span>
            <span class="flex items-center gap-1">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-3.5 h-3.5"><path stroke-linecap="round" stroke-linejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
              {{ formatDate(a.createdAt) }}
            </span>
          </div>
          <div class="text-[13px] line-clamp-2 leading-relaxed" :class="isRead(a.id) ? 'text-slate-400' : 'text-slate-500'" v-html="formatContent(a.content)">
          </div>
        </div>
      </div>
    </div>

    <!-- Notification Detail Modal -->
    <div v-if="selectedItem" class="fixed inset-0 z-50 flex items-end sm:items-center justify-center">
      <div class="absolute inset-0 bg-slate-900/60 backdrop-blur-sm transition-opacity" @click="selectedItem = null"></div>
      <div class="bg-white dark:bg-slate-900 w-full sm:w-[90%] sm:rounded-[2rem] rounded-t-[2rem] h-[85%] sm:h-auto overflow-hidden relative z-10 shadow-2xl dark:shadow-[0_24px_70px_rgba(2,6,23,0.55)] animate-slide-up flex flex-col transition-colors">
        <div class="px-6 py-4 border-b border-slate-100 dark:border-slate-800 flex justify-between items-center relative">
           <button @click="selectedItem = null" class="w-8 h-8 flex items-center justify-center bg-slate-100 text-slate-600 rounded-full font-bold active:scale-90 transition-transform">✕</button>
           <span class="font-bold text-slate-800 absolute left-1/2 -translate-x-1/2">通知详情</span>
           <div class="w-8"></div>
        </div>
        <div class="flex-1 overflow-y-auto p-6">
           <div class="mb-4">
             <span class="px-2 py-1 rounded border text-xs font-bold inline-block mb-3" :class="typeColor(selectedItem.type)">{{ typeLabel(selectedItem.type) }}</span>
             <h2 class="text-xl font-bold text-slate-900 leading-tight mb-3">{{ selectedItem.title }}</h2>
             <div class="flex items-center space-x-4 text-xs text-slate-400 font-medium">
               <span>{{ formatDate(selectedItem.createdAt) }}</span>
               <span class="flex items-center space-x-1"><span>👤</span><span>发布人：{{ selectedItem.adminName || '管理员' }}</span></span>
             </div>
           </div>
           <div class="prose prose-slate prose-sm max-w-none text-slate-700 leading-loose whitespace-pre-wrap" v-html="formatContent(selectedItem.content)">
           </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes slide-up { from { transform: translateY(100%); } to { transform: translateY(0); } }
.animate-slide-up { animation: slide-up 0.3s cubic-bezier(0.16, 1, 0.3, 1); }

:global(.dark) .notifications-page {
  background:
    radial-gradient(circle at top, rgba(16, 185, 129, 0.16), transparent 34%),
    linear-gradient(180deg, #020617 0%, #0f172a 42%, #111827 100%);
}

:global(.dark) .notifications-page :deep([class~='bg-white']) {
  background-color: rgba(15, 23, 42, 0.88) !important;
}

:global(.dark) .notifications-page :deep([class~='bg-slate-50']) {
  background-color: rgba(30, 41, 59, 0.78) !important;
}

:global(.dark) .notifications-page :deep([class~='bg-slate-100']) {
  background-color: rgba(51, 65, 85, 0.82) !important;
}

:global(.dark) .notifications-page :deep([class~='bg-blue-50/30']) {
  background-color: rgba(59, 130, 246, 0.14) !important;
}

:global(.dark) .notifications-page :deep([class~='bg-blue-50']) {
  background-color: rgba(59, 130, 246, 0.16) !important;
}

:global(.dark) .notifications-page :deep([class~='bg-red-50']) {
  background-color: rgba(239, 68, 68, 0.16) !important;
}

:global(.dark) .notifications-page :deep([class~='bg-purple-50']) {
  background-color: rgba(168, 85, 247, 0.16) !important;
}

:global(.dark) .notifications-page :deep([class~='border-slate-100']),
:global(.dark) .notifications-page :deep([class~='border-slate-200']),
:global(.dark) .notifications-page :deep([class~='border-slate-50']) {
  border-color: rgba(71, 85, 105, 0.76) !important;
}

:global(.dark) .notifications-page :deep([class~='border-blue-100']) {
  border-color: rgba(96, 165, 250, 0.28) !important;
}

:global(.dark) .notifications-page :deep([class~='border-blue-200']) {
  border-color: rgba(96, 165, 250, 0.28) !important;
}

:global(.dark) .notifications-page :deep([class~='border-red-200']) {
  border-color: rgba(248, 113, 113, 0.28) !important;
}

:global(.dark) .notifications-page :deep([class~='border-purple-200']) {
  border-color: rgba(192, 132, 252, 0.28) !important;
}

:global(.dark) .notifications-page :deep([class~='text-slate-900']),
:global(.dark) .notifications-page :deep([class~='text-slate-800']) {
  color: #f8fafc !important;
}

:global(.dark) .notifications-page :deep([class~='text-slate-700']),
:global(.dark) .notifications-page :deep([class~='text-slate-600']) {
  color: #e2e8f0 !important;
}

:global(.dark) .notifications-page :deep([class~='text-slate-500']),
:global(.dark) .notifications-page :deep([class~='text-slate-400']) {
  color: #94a3b8 !important;
}

:global(.dark) .notifications-page :deep(.prose),
:global(.dark) .notifications-page :deep(.prose p),
:global(.dark) .notifications-page :deep(.prose li),
:global(.dark) .notifications-page :deep(.prose strong) {
  color: #e2e8f0 !important;
}

:global(.dark) .notifications-page h1,
:global(.dark) .notifications-page h2,
:global(.dark) .notifications-page h3 {
  color: #f8fafc !important;
}

:global(.dark) .notifications-page .text-slate-500,
:global(.dark) .notifications-page .text-slate-400 {
  color: #94a3b8 !important;
}
</style>
