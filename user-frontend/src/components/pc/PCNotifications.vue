<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import dayjs from 'dayjs'
import { AUTH_CHANGE_EVENT } from '../../utils/accountSecurity'
import {
  fetchAnnouncements,
  getLocalAnnouncementReadIds,
  isAnnouncementRead,
  markAnnouncementAsRead
} from '../../utils/announcementReadState'

const props = defineProps({
  isDark: {
    type: Boolean,
    default: false
  }
})

const showModal = ref(false)
const announcements = ref([])
const loading = ref(true)
const selectedItem = ref(null)
const localReadIds = ref(getLocalAnnouncementReadIds())

const isRead = (announcement) => isAnnouncementRead(announcement, localReadIds.value)
const unreadCount = computed(() => announcements.value.filter(item => !isRead(item)).length)

const loadAnnouncements = async () => {
  loading.value = true
  localReadIds.value = getLocalAnnouncementReadIds()

  try {
    announcements.value = await fetchAnnouncements('/api/discovery/announcements')
  } catch (error) {
    console.error('Failed to load announcements', error)
    announcements.value = []
  } finally {
    loading.value = false
  }
}

const openAnnouncement = async (item) => {
  if (!isRead(item)) {
    const previousRead = item.read
    item.read = true

    try {
      const nextReadIds = await markAnnouncementAsRead(item.id)
      if (nextReadIds.length > 0) {
        localReadIds.value = nextReadIds
      }
    } catch (error) {
      item.read = previousRead
      console.error('Failed to mark announcement as read', error)
    }
  }

  selectedItem.value = item
}

const typeLabel = (type) => {
  const map = { info: '系统', warning: '提醒', urgent: '紧急' }
  return map[type] || '通知'
}

const typeColor = (type) => {
  if (type === 'urgent') return 'bg-red-50 text-red-600 border-red-200'
  if (type === 'warning') return 'bg-amber-50 text-amber-600 border-amber-200'
  if (type === 'info') return 'bg-blue-50 text-blue-600 border-blue-200'
  return 'bg-slate-50 text-slate-600 border-slate-200'
}

const formatDate = (dateString) => dayjs(dateString).format('YYYY-MM-DD HH:mm')

const formatContent = (content) => {
  if (!content) return ''
  return content.replace(/\n/g, '<br/>')
}

const handleAuthChange = () => {
  loadAnnouncements()
}

onMounted(() => {
  loadAnnouncements()
  window.addEventListener(AUTH_CHANGE_EVENT, handleAuthChange)
})

onUnmounted(() => {
  window.removeEventListener(AUTH_CHANGE_EVENT, handleAuthChange)
})
</script>

<template>
  <div class="relative">
    <!-- Bell Button -->
    <button 
      @click="showModal = true"
      class="w-10 h-10 rounded-full flex items-center justify-center transition-all relative overflow-visible shadow-sm ring-1"
      :class="props.isDark ? 'bg-slate-800 text-yellow-400 hover:bg-slate-700 ring-slate-700/50' : 'bg-white text-yellow-500 hover:bg-slate-50 ring-slate-900/5'"
    >
      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-5 h-5">
        <path fill-rule="evenodd" d="M5.25 9a6.75 6.75 0 0 1 13.5 0v.75c0 2.123.8 4.057 2.118 5.52a.75.75 0 0 1-.297 1.206c-1.544.57-3.16.99-4.831 1.243a3.75 3.75 0 1 1-7.48 0 24.585 24.585 0 0 1-4.831-1.244.75.75 0 0 1-.298-1.205A8.217 8.217 0 0 0 5.25 9.75V9Zm4.502 8.9a2.25 2.25 0 1 0 4.496 0 25.057 25.057 0 0 1-4.496 0Z" clip-rule="evenodd" />
      </svg>
      <!-- Unread Badge -->
      <span v-if="unreadCount > 0" class="absolute -top-1 -right-1 flex h-4 w-4 items-center justify-center rounded-full bg-red-500 text-[10px] font-bold text-white shadow-sm ring-2 ring-white" :class="props.isDark ? 'ring-slate-900' : 'ring-white'">
        {{ unreadCount > 99 ? '99+' : unreadCount }}
      </span>
    </button>

    <!-- Main List Modal -->
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0 scale-95"
      enter-to-class="opacity-100 scale-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100 scale-100"
      leave-to-class="opacity-0 scale-95"
    >
      <div v-if="showModal && !selectedItem" class="fixed inset-0 z-[100] flex items-center justify-center">
        <div class="absolute inset-0 bg-black/40 backdrop-blur-sm" @click="showModal = false"></div>
        <div class="relative z-10 w-full max-w-2xl mx-4 max-h-[85vh] rounded-[24px] overflow-hidden shadow-2xl flex flex-col"
             :class="props.isDark ? 'bg-slate-900 border border-slate-800' : 'bg-white border border-slate-200'">
          
          <div class="px-6 py-5 border-b flex justify-between items-center shrink-0" :class="props.isDark ? 'border-slate-800 bg-slate-900/90' : 'border-slate-100 bg-white/90'">
            <div class="flex items-center gap-3">
              <div class="w-10 h-10 rounded-full flex items-center justify-center" :class="props.isDark ? 'bg-slate-800 text-yellow-500' : 'bg-slate-100 text-yellow-600'">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-5 h-5"><path fill-rule="evenodd" d="M5.25 9a6.75 6.75 0 0 1 13.5 0v.75c0 2.123.8 4.057 2.118 5.52a.75.75 0 0 1-.297 1.206c-1.544.57-3.16.99-4.831 1.243a3.75 3.75 0 1 1-7.48 0 24.585 24.585 0 0 1-4.831-1.244.75.75 0 0 1-.298-1.205A8.217 8.217 0 0 0 5.25 9.75V9Zm4.502 8.9a2.25 2.25 0 1 0 4.496 0 25.057 25.057 0 0 1-4.496 0Z" clip-rule="evenodd" /></svg>
              </div>
              <div>
                <h2 class="text-lg font-bold" :class="props.isDark ? 'text-white' : 'text-slate-900'">消息通知</h2>
                <p class="text-xs font-medium" :class="props.isDark ? 'text-slate-400' : 'text-slate-500'">共 {{ announcements.length }} 条消息，{{ unreadCount }} 条未读</p>
              </div>
            </div>
            <button @click="showModal = false" class="w-8 h-8 flex items-center justify-center rounded-full hover:bg-slate-100 transition-colors" :class="props.isDark ? 'hover:bg-slate-800 text-slate-400' : 'text-slate-500'">×</button>
          </div>

          <div class="flex-1 overflow-y-auto p-6 scroll-smooth">
            <div v-if="loading" class="flex flex-col items-center justify-center py-16">
              <div class="w-8 h-8 border-4 border-slate-200 border-t-green-500 rounded-full animate-spin mb-4" :class="props.isDark ? 'border-slate-700' : ''"></div>
              <div class="text-sm font-medium" :class="props.isDark ? 'text-slate-400' : 'text-slate-500'">加载中...</div>
            </div>
            <div v-else-if="announcements.length === 0" class="flex flex-col items-center justify-center py-16 text-center">
              <div class="w-16 h-16 rounded-full flex items-center justify-center text-3xl mb-4" :class="props.isDark ? 'bg-slate-800' : 'bg-slate-100'">📭</div>
              <h3 class="font-bold text-lg" :class="props.isDark ? 'text-slate-200' : 'text-slate-800'">暂无通知</h3>
              <p class="text-sm mt-1" :class="props.isDark ? 'text-slate-400' : 'text-slate-500'">目前没有任何系统公告</p>
            </div>
            <div v-else class="space-y-3">
              <div
                v-for="announcement in announcements"
                :key="announcement.id"
                @click="openAnnouncement(announcement)"
                class="rounded-xl p-4 transition-all cursor-pointer border group"
                :class="isRead(announcement) 
                  ? (props.isDark ? 'bg-slate-800/50 border-slate-800 hover:border-slate-600' : 'bg-slate-50 border-slate-100 hover:border-slate-200 hover:bg-slate-100/50')
                  : (props.isDark ? 'bg-blue-900/20 border-blue-800/30 hover:border-blue-700' : 'bg-blue-50/50 border-blue-100 hover:border-blue-200')"
              >
                <div class="flex justify-between items-start gap-4">
                  <div class="flex items-start gap-3 flex-1 min-w-0">
                    <span v-if="!isRead(announcement)" class="w-2 h-2 mt-2 bg-red-500 rounded-full shrink-0 shadow-sm"></span>
                    <div class="flex-1 min-w-0">
                      <div class="flex justify-between items-start mb-1">
                        <div class="flex items-center gap-2">
                          <span class="text-[10px] font-bold px-1.5 py-0.5 rounded border" :class="typeColor(announcement.type)">
                            {{ typeLabel(announcement.type) }}
                          </span>
                          <span class="text-xs font-medium" :class="isRead(announcement) ? (props.isDark ? 'text-slate-500' : 'text-slate-400') : (props.isDark ? 'text-slate-400' : 'text-slate-500')">
                            {{ formatDate(announcement.createdAt) }} · {{ announcement.adminName || '管理员' }}
                          </span>
                        </div>
                      </div>
                      <h3 class="font-bold text-sm leading-snug line-clamp-1 group-hover:text-green-500 transition-colors" :class="isRead(announcement) ? (props.isDark ? 'text-slate-400' : 'text-slate-600') : (props.isDark ? 'text-slate-200' : 'text-slate-900')">
                        {{ announcement.title }}
                      </h3>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <!-- Detail Modal -->
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0 translate-x-8"
      enter-to-class="opacity-100 translate-x-0"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100 translate-x-0"
      leave-to-class="opacity-0 translate-x-8"
    >
      <div v-if="showModal && selectedItem" class="fixed inset-0 z-[110] flex items-center justify-center">
        <div class="absolute inset-0 bg-black/50 backdrop-blur-sm" @click="selectedItem = null"></div>
        <div class="relative z-10 w-full max-w-xl mx-4 max-h-[85vh] rounded-[24px] overflow-hidden shadow-2xl flex flex-col"
             :class="props.isDark ? 'bg-slate-900 border border-slate-700' : 'bg-white border border-slate-200'">
          
          <div class="px-6 py-4 border-b flex items-center justify-between shrink-0" :class="props.isDark ? 'border-slate-800' : 'border-slate-100'">
            <button @click="selectedItem = null" class="flex items-center gap-1 text-sm font-bold transition-colors" :class="props.isDark ? 'text-slate-400 hover:text-white' : 'text-slate-500 hover:text-slate-900'">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-4 h-4"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 19.5L8.25 12l7.5-7.5" /></svg>
              返回列表
            </button>
          </div>

          <div class="flex-1 overflow-y-auto p-8 scroll-smooth">
            <div class="mb-6">
              <span class="px-2 py-1 rounded border text-[11px] font-bold inline-block mb-3" :class="typeColor(selectedItem.type)">
                {{ typeLabel(selectedItem.type) }}
              </span>
              <h2 class="text-2xl font-bold leading-tight mb-4" :class="props.isDark ? 'text-white' : 'text-slate-900'">
                {{ selectedItem.title }}
              </h2>
              <div class="flex items-center gap-4 text-[13px] font-medium" :class="props.isDark ? 'text-slate-400' : 'text-slate-500'">
                <span class="flex items-center gap-1.5"><svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4"><path stroke-linecap="round" stroke-linejoin="round" d="M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z" /></svg> {{ formatDate(selectedItem.createdAt) }}</span>
                <span class="flex items-center gap-1.5"><svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-4 h-4"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z" /></svg> {{ selectedItem.adminName || '管理员' }}</span>
              </div>
            </div>
            <div class="prose prose-sm max-w-none leading-loose whitespace-pre-wrap"
                 :class="props.isDark ? 'prose-invert text-slate-300 [&_p]:text-slate-300' : 'prose-slate text-slate-700'"
                 v-html="formatContent(selectedItem.content)"></div>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>
