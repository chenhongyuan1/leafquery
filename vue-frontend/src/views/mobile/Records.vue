<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

const router = useRouter()
const historyRecords = ref([])
const isLoading = ref(true)

onMounted(async () => {
  try {
    const userStr = localStorage.getItem('user')
    if (userStr) {
      const user = JSON.parse(userStr)
      if (user && user.userId) {
        // 请求云端数据
        const response = await axios.get(`/api/record/list?userId=${user.userId}`)
        if (response.data && response.data.code === 200) {
          // 由于后端返回的是 Record 对象，包含 createTime 等字段
          historyRecords.value = response.data.data.map(item => ({
            id: item.id,
            name: item.pestName,
            confidence: item.confidence,
            time: item.createTime, // 后端是 Date 格式或时间戳
            imageUrl: item.imageUrl
          }))
          isLoading.value = false
          return
        }
      }
    }
  } catch (e) {
    console.error('从云端获取记录失败, 降级使用本地存储:', e)
  }

  // 兜底：使用本地记录
  const saved = localStorage.getItem('leafquery_history')
  if (saved) {
    historyRecords.value = JSON.parse(saved)
  }
  isLoading.value = false
})

const goBack = () => {
  router.back()
}

// 格式化时间戳
const formatTime = (timestamp) => {
  if (!timestamp || isNaN(Number(timestamp))) return '未知时间'
  
  const date = new Date(Number(timestamp))
  const now = new Date()
  const diff = now - date
  
  // 如果是今天内的
  if (date.toDateString() === now.toDateString()) {
    if (diff < 60000) return '刚刚'
    if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
    return `${Math.floor(diff / 3600000)}小时前`
  }
  
  // 如果是昨天
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (date.toDateString() === yesterday.toDateString()) {
    return `昨天 ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
  }
  
  // 其他日期
  return `${date.getMonth() + 1}月${date.getDate()}日`
}

// 获取不带置信度的纯名字
const getCleanName = (name) => {
  if (!name) return '未知'
  return name.replace(/\(.*\)/, '').trim()
}

// 根据条件获取置信度标签颜色
const getConfidenceColor = (confidence) => {
  if (confidence >= 0.8) return 'bg-green-100 text-green-700 border-green-200'
  if (confidence >= 0.5) return 'bg-orange-100 text-orange-700 border-orange-200'
  return 'bg-red-100 text-red-700 border-red-200'
}

// 获取占位 emoji
const getEmoji = (name) => {
  if (name.includes('健康')) return '🌿'
  if (name.includes('虫')) return '🐛'
  if (name.includes('病')) return '🦠'
  return '🍃'
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
      <div class="w-10"></div> <!-- Placeholder to center title -->
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
      <div v-if="historyRecords.length === 0" class="flex-1 flex flex-col items-center justify-center -mt-20 opacity-70" v-motion-fade>
        <div class="w-32 h-32 bg-slate-100 rounded-full flex items-center justify-center text-5xl mb-6 shadow-inner">
          📭
        </div>
        <h3 class="text-lg font-bold text-slate-700">暂无识别记录</h3>
        <p class="text-sm text-slate-400 mt-2 text-center max-w-[200px]">您还没有进行过作物的病害识别，快去首页试一试吧！</p>
      </div>

      <!-- 记录列表 -->
      <div v-else class="space-y-4">
        <transition-group 
          enter-active-class="transition duration-500 ease-out"
          enter-from-class="opacity-0 translate-y-8 scale-95"
          enter-to-class="opacity-100 translate-y-0 scale-100"
        >
          <div 
            v-for="(item, index) in historyRecords" 
            :key="item.id || index"
            class="bg-white rounded-[1.5rem] p-4 flex items-center shadow-[0_4px_20px_rgb(0,0,0,0.03)] border border-slate-50 active:bg-slate-50 transition-colors"
            :style="{ transitionDelay: `${index * 50}ms` }"
          >
            <!-- 左侧图片/Emoji缩略图 -->
            <div class="w-16 h-16 rounded-2xl bg-slate-100 flex items-center justify-center text-3xl overflow-hidden shrink-0 shadow-inner mr-4 relative">
              <img v-if="item.imageUrl && item.imageUrl.trim() !== ''" :src="item.imageUrl" class="w-full h-full object-cover" @error="$event.target.style.display='none'; $event.target.nextElementSibling.style.display='inline'" />
              <span v-show="!item.imageUrl || item.imageUrl.trim() === ''">{{ getEmoji(item.name) }}</span>
              <!-- 图片上的识别类型小标记 -->
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
                <span v-else-if="item.name.includes('健康')" class="px-2 py-0.5 rounded-md border bg-slate-50 text-slate-500 border-slate-200 text-[10px] font-bold">
                  未见异常
                </span>
              </div>
            </div>
          </div>
        </transition-group>
      </div>
      
    </div>
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
