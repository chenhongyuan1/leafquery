<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useFarmStore } from '../../stores/farmCloud'

const router = useRouter()
const farmStore = useFarmStore()
const historyRecords = computed(() => (farmStore.identificationHistory || []).map(item => ({
  id: item.id,
  name: item.pestName || item.name || '',
  confidence: Number(item.confidence || 0),
  time: item.time || item.createTime,
  imageUrl: item.imageUrl || ''
})))
const isLoading = ref(true)
const showClearConfirm = ref(false)
const isClearing = ref(false)
const deletingId = ref(null)

onMounted(async () => {
  await farmStore.initialize()
  isLoading.value = false
})

const goBack = () => {
  router.back()
}

const deleteRecord = async (item, index) => {
  if (deletingId.value) return
  deletingId.value = item.id
  await farmStore.removeIdentification(item.id)
  deletingId.value = null
}

const clearAllRecords = async () => {
  isClearing.value = true
  await farmStore.clearIdentificationHistory()
  showClearConfirm.value = false
  isClearing.value = false
}
// 格式化时间戳
const formatTime = (timestamp) => {
  if (!timestamp) return '未知时间'

  const timeNum = Number(timestamp)
  const date = isNaN(timeNum) ? new Date(timestamp) : new Date(timeNum)

  if (isNaN(date.getTime())) return String(timestamp) || '未知时间'
  const now = new Date()
  const diff = now - date

  if (date.toDateString() === now.toDateString()) {
    if (diff < 60000) return '刚刚'
    if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
    return `${Math.floor(diff / 3600000)}小时前`
  }

  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (date.toDateString() === yesterday.toDateString()) {
    return `昨天 ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
  }

  return `${date.getMonth() + 1}月${date.getDate()}日`
}

const getCleanName = (name) => {
  if (!name) return '未知'
  return name.replace(/\(.*\)/, '').trim()
}

const getConfidenceColor = (confidence) => {
  if (confidence >= 0.8) return 'bg-green-100 text-green-700 border-green-200'
  if (confidence >= 0.5) return 'bg-orange-100 text-orange-700 border-orange-200'
  return 'bg-red-100 text-red-700 border-red-200'
}

const getEmoji = (name) => {
  if (name.includes('健康')) return '🌿'
  if (name.includes('虫')) return '🐛'
  if (name.includes('病')) return '🦠'
  return '📌'
}
</script>

<template>
  <div class="records-page min-h-full bg-slate-50 flex flex-col relative overflow-y-auto overflow-x-hidden">
    <!-- Header -->
    <div class="sticky top-0 z-50 bg-white/80 backdrop-blur-md border-b border-slate-100 flex items-center justify-between px-4 py-4">
      <button @click="goBack" class="p-2 -ml-2 text-slate-400 hover:text-slate-600 active:scale-95 transition-all outline-none">
        <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
        </svg>
      </button>
      <h1 class="text-lg font-extrabold text-slate-800 tracking-wide">识别记录</h1>
      <!-- 清空按钮 -->
      <button
        v-if="historyRecords.length > 0"
        @click="showClearConfirm = true"
        class="p-2 -mr-2 text-slate-400 hover:text-red-500 active:scale-95 transition-all outline-none"
        title="清空记录"
      >
        <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
          <path stroke-linecap="round" stroke-linejoin="round" d="m14.74 9-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0" />
        </svg>
      </button>
      <div v-else class="w-10"></div>
    </div>

    <!-- Background Decoration -->
    <div class="absolute top-20 right-0 w-64 h-64 bg-green-100/40 rounded-full blur-3xl translate-x-1/3 pointer-events-none"></div>
    <div class="absolute bottom-20 left-0 w-64 h-64 bg-blue-100/40 rounded-full blur-3xl -translate-x-1/3 pointer-events-none"></div>

    <div class="flex-1 flex flex-col pt-6 pb-24 px-6 relative z-10 custom-scrollbar">
      <!-- 统计栏 -->
      <div v-if="historyRecords.length > 0" class="flex justify-between items-end mb-6" v-motion-slide-visible-once-bottom>
        <div>
          <h2 class="text-2xl font-black text-slate-800">历史足迹</h2>
          <p class="text-sm font-medium text-slate-500 mt-1">共为您记录了 <span class="text-green-600 font-bold">{{ historyRecords.length }}</span> 次分析</p>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="historyRecords.length === 0 && !isLoading" class="flex-1 flex flex-col items-center justify-center -mt-20 opacity-70" v-motion-fade>
        <div class="w-32 h-32 bg-slate-100 rounded-full flex items-center justify-center text-5xl mb-6 shadow-inner">
          📭
        </div>
        <h3 class="text-lg font-bold text-slate-700">暂无识别记录</h3>
        <p class="text-sm text-slate-400 mt-2 text-center max-w-[200px]">您还没有进行过作物病害识别，快去首页试一试吧。</p>
      </div>

      <!-- 记录列表 -->
      <div v-else class="space-y-4">
        <transition-group
          enter-active-class="transition duration-500 ease-out"
          enter-from-class="opacity-0 translate-y-8 scale-95"
          enter-to-class="opacity-100 translate-y-0 scale-100"
          leave-active-class="transition duration-300 ease-in"
          leave-from-class="opacity-100 translate-x-0"
          leave-to-class="opacity-0 -translate-x-full"
        >
          <div
            v-for="(item, index) in historyRecords"
            :key="item.id || index"
            class="bg-white rounded-[1.5rem] p-4 flex items-center shadow-[0_4px_20px_rgb(0,0,0,0.03)] border border-slate-50 transition-colors group"
            :style="{ transitionDelay: `${index * 50}ms` }"
          >
            <!-- 左侧图片 / Emoji 缩略图 -->
            <div class="w-16 h-16 rounded-2xl bg-slate-100 flex items-center justify-center text-3xl overflow-hidden shrink-0 shadow-inner mr-4 relative">
              <img v-if="item.imageUrl && item.imageUrl.trim() !== ''" :src="item.imageUrl" class="w-full h-full object-cover" @error="$event.target.style.display='none'; $event.target.nextElementSibling.style.display='inline'" />
              <span v-show="!item.imageUrl || item.imageUrl.trim() === ''">{{ getEmoji(item.name) }}</span>
              <div v-if="item.imageUrl" class="absolute bottom-1 right-1 w-5 h-5 bg-white/90 backdrop-blur rounded-full flex items-center justify-center text-[10px] shadow-sm">
                {{ getEmoji(item.name) }}
              </div>
            </div>

            <!-- 中间信息 -->
            <div class="flex-1 min-w-0 flex flex-col justify-center">
              <div class="flex items-center justify-between mb-1">
                <h3 class="text-base font-bold text-slate-800 truncate pr-2">{{ getCleanName(item.name) }}</h3>
                <span class="text-xs font-semibold text-slate-400 shrink-0">{{ formatTime(item.time) }}</span>
              </div>
              <div class="flex items-center space-x-2">
                <span class="px-2 py-0.5 rounded-md border text-[10px] font-bold" :class="getConfidenceColor(item.confidence)">
                  匹配率 {{ (item.confidence * 100).toFixed(1) }}%
                </span>
                <span v-if="item.name === '通用农业咨询'" class="px-2 py-0.5 rounded-md border bg-blue-50 text-blue-600 border-blue-200 text-[10px] font-bold flex items-center">
                  <span class="mr-1">🤖</span> AI 问答
                </span>
                <span v-else-if="item.name && item.name.includes('健康')" class="px-2 py-0.5 rounded-md border bg-slate-50 text-slate-500 border-slate-200 text-[10px] font-bold">
                  未见异常
                </span>
              </div>
            </div>

            <!-- 删除按钮 -->
            <button
              @click.stop="deleteRecord(item, index)"
              :disabled="deletingId === item.id"
              class="ml-3 shrink-0 w-9 h-9 rounded-xl flex items-center justify-center text-slate-300 hover:text-red-500 hover:bg-red-50 active:scale-90 transition-all opacity-0 group-hover:opacity-100 focus:opacity-100"
              :class="{ 'opacity-100': deletingId === item.id }"
              title="删除此条记录"
            >
              <svg v-if="deletingId !== item.id" xmlns="http://www.w3.org/2000/svg" class="w-4.5 h-4.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6 18 18 6M6 6l12 12" />
              </svg>
              <div v-else class="w-4 h-4 border-2 border-red-400 border-t-transparent rounded-full animate-spin"></div>
            </button>
          </div>
        </transition-group>
      </div>
    </div>

    <!-- 清空确认弹窗 -->
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div v-if="showClearConfirm" class="fixed inset-0 z-[200] flex items-center justify-center" @click.self="showClearConfirm = false">
        <div class="absolute inset-0 bg-slate-900/40 backdrop-blur-sm"></div>
        <div class="relative bg-white rounded-3xl p-6 mx-8 max-w-sm w-full shadow-2xl" @click.stop>
          <div class="text-center mb-5">
            <div class="w-16 h-16 mx-auto bg-red-50 rounded-full flex items-center justify-center text-3xl mb-4">🗑️</div>
            <h3 class="text-lg font-bold text-slate-800">确认清空全部记录？</h3>
            <p class="text-sm text-slate-500 mt-2">此操作会删除您全部的 <strong class="text-red-500">{{ historyRecords.length }}</strong> 条识别记录及其图片，且不可恢复。</p>
          </div>
          <div class="flex gap-3">
            <button
              @click="showClearConfirm = false"
              class="flex-1 py-3 px-4 rounded-2xl bg-slate-100 text-slate-600 font-bold text-sm active:scale-95 transition-all"
            >
              取消
            </button>
            <button
              @click="clearAllRecords"
              :disabled="isClearing"
              class="flex-1 py-3 px-4 rounded-2xl bg-red-500 text-white font-bold text-sm active:scale-95 transition-all shadow-lg shadow-red-500/20 flex items-center justify-center gap-2"
            >
              <div v-if="isClearing" class="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
              {{ isClearing ? '清除中...' : '确认清空' }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  display: none;
}
.custom-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}

:global(.dark) .records-page {
  background:
    radial-gradient(circle at top, rgba(16, 185, 129, 0.16), transparent 34%),
    linear-gradient(180deg, #020617 0%, #0f172a 42%, #111827 100%);
}

:global(.dark) .records-page :deep([class~='bg-white']),
:global(.dark) .records-page :deep([class~='bg-white/80']) {
  background-color: rgba(15, 23, 42, 0.88) !important;
}

:global(.dark) .records-page :deep([class~='bg-slate-50']) {
  background-color: rgba(30, 41, 59, 0.78) !important;
}

:global(.dark) .records-page :deep([class~='bg-slate-100']) {
  background-color: rgba(51, 65, 85, 0.82) !important;
}

:global(.dark) .records-page :deep([class~='bg-green-100/40']) {
  background-color: rgba(16, 185, 129, 0.12) !important;
}

:global(.dark) .records-page :deep([class~='bg-blue-100/40']) {
  background-color: rgba(59, 130, 246, 0.12) !important;
}

:global(.dark) .records-page :deep([class~='bg-green-100']) {
  background-color: rgba(16, 185, 129, 0.18) !important;
}

:global(.dark) .records-page :deep([class~='bg-orange-100']) {
  background-color: rgba(249, 115, 22, 0.18) !important;
}

:global(.dark) .records-page :deep([class~='bg-red-100']) {
  background-color: rgba(239, 68, 68, 0.18) !important;
}

:global(.dark) .records-page :deep([class~='border-slate-50']),
:global(.dark) .records-page :deep([class~='border-slate-100']),
:global(.dark) .records-page :deep([class~='border-slate-200']) {
  border-color: rgba(71, 85, 105, 0.76) !important;
}

:global(.dark) .records-page :deep([class~='border-green-200']) {
  border-color: rgba(52, 211, 153, 0.28) !important;
}

:global(.dark) .records-page :deep([class~='border-orange-200']) {
  border-color: rgba(251, 146, 60, 0.28) !important;
}

:global(.dark) .records-page :deep([class~='border-red-200']) {
  border-color: rgba(248, 113, 113, 0.28) !important;
}

:global(.dark) .records-page :deep([class~='text-slate-900']),
:global(.dark) .records-page :deep([class~='text-slate-800']) {
  color: #f8fafc !important;
}

:global(.dark) .records-page :deep([class~='text-slate-700']),
:global(.dark) .records-page :deep([class~='text-slate-600']) {
  color: #e2e8f0 !important;
}

:global(.dark) .records-page :deep([class~='text-slate-500']),
:global(.dark) .records-page :deep([class~='text-slate-400']) {
  color: #94a3b8 !important;
}
</style>
