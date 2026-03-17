<script setup>
import { ref, computed, onMounted } from 'vue'
import { useFavoritesStore } from '../stores/favorites'
import NewsCard from '../components/NewsCard.vue'
import QnACard from '../components/QnACard.vue'
import axios from 'axios'

const API_BASE = 'http://localhost:8080/api/discovery'

const favStore = useFavoritesStore()

// Categories
const categories = ['推荐', '知识库', '问答圈']
const activeCategory = ref(0)
const searchQuery = ref('')
const selectedPlant = ref(null)
const isPosting = ref(false)
const selectedLibraryItem = ref(null)
const selectedNewsItem = ref(null)

// Reactive data stores (populated from API)
const newsData = ref([])
const plantsData = ref([])
const knowledgeData = ref([])
const qnaData = ref([])
const loading = ref(false)

// Q&A Post State
const postText = ref('')
const postImages = ref([])

// ========== Fetch data from backend on mount ==========
const fetchNews = async () => {
  try {
    const { data } = await axios.get(`${API_BASE}/news`)
    newsData.value = (data.data || []).map(item => ({
      ...item,
      type: 'news',
      id: item.newsId,
      date: formatTime(item.createdAt)
    }))
  } catch (e) { console.error('获取资讯失败', e) }
}

const fetchPlants = async () => {
  try {
    const { data } = await axios.get(`${API_BASE}/plants`)
    plantsData.value = (data.data || []).map(item => ({
      ...item,
      id: 'p' + item.plantId,
      name: item.name,
      enName: item.enName,
      tag: item.tag,
      desc: item.description,
      date: item.createdAt
    }))
  } catch (e) { console.error('获取植物列表失败', e) }
}

const fetchKnowledge = async (plantId) => {
  try {
    const url = plantId ? `${API_BASE}/knowledge?plantId=${plantId}` : `${API_BASE}/knowledge`
    const { data } = await axios.get(url)
    knowledgeData.value = (data.data || []).map(item => ({
      ...item,
      type: 'library',
      id: item.knowledgeId,
      plant: item.plantName,
      conditionType: item.conditionType,
      title: item.title,
      tag: item.tag,
      image: item.imageUrl,
      desc: item.description,
      prevention: safeParseJSON(item.prevention)
    }))
  } catch (e) { console.error('获取知识库失败', e) }
}

const fetchQna = async () => {
  try {
    const { data } = await axios.get(`${API_BASE}/qna`)
    qnaData.value = (data.data || []).map(item => ({
      ...item,
      type: 'qna',
      id: item.postId,
      user: item.userName,
      userAvatar: item.userAvatar || '',
      time: formatTime(item.createdAt),
      content: item.content,
      images: safeParseJSON(item.images),
      expertReply: item.expertReply ? { expertName: item.expertName || '专家', content: item.expertReply } : null,
      likes: item.likes || 0,
      comments: (item.comments || []).map(c => ({
        user: c.userName,
        content: c.content,
        time: formatTime(c.createdAt)
      }))
    }))
  } catch (e) { console.error('获取问答数据失败', e) }
}

// Utility: safe JSON parse
const safeParseJSON = (str) => {
  if (!str) return []
  try { return JSON.parse(str) } catch { return [str] }
}

// Utility: format timestamp to relative time
const formatTime = (ts) => {
  if (!ts) return ''
  const now = new Date()
  const date = new Date(ts)
  const diffMs = now - date
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return '刚刚'
  if (diffMin < 60) return `${diffMin}分钟前`
  const diffHour = Math.floor(diffMin / 60)
  if (diffHour < 24) return `${diffHour}小时前`
  const diffDay = Math.floor(diffHour / 24)
  if (diffDay < 30) return `${diffDay}天前`
  return date.toLocaleDateString('zh-CN')
}

// Load all data on component mount
onMounted(async () => {
  loading.value = true
  await Promise.all([fetchNews(), fetchPlants(), fetchQna()])
  loading.value = false
})

// ========== Computed: current items for active tab ==========
const currentItems = computed(() => {
  let items = []
  switch(activeCategory.value) {
    case 0: items = newsData.value; break;
    case 1:
      if (!selectedPlant.value) {
        items = plantsData.value;
      } else {
        items = knowledgeData.value;
      }
      break;
    case 2: items = qnaData.value; break;
    default: items = [];
  }

  // Search Filtering
  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    items = items.filter(item => {
      const matchTitle = item.title && item.title.toLowerCase().includes(q)
      const matchName = item.name && item.name.toLowerCase().includes(q)
      const matchEnName = item.enName && item.enName.toLowerCase().includes(q)
      const matchDesc = item.desc && item.desc.toLowerCase().includes(q)
      const matchContent = item.content && item.content.toLowerCase().includes(q)
      return matchTitle || matchName || matchEnName || matchDesc || matchContent
    })
  }

  return items
})

const getConditionColor = (conditionType) => {
  switch(conditionType) {
    case '健康': return 'bg-emerald-500'
    case '真菌病害': return 'bg-orange-500'
    case '病毒病害': return 'bg-amber-500'
    case '细菌病害': return 'bg-yellow-600'
    case '缺素/生理障碍': return 'bg-blue-400'
    case '虫害': return 'bg-rose-500'
    default: return 'bg-slate-500'
  }
}

// ========== Actions ==========
const handleImageUpload = () => {
    if (postImages.value.length < 3) postImages.value.push(Date.now())
}

const selectPlant = async (plant) => {
  selectedPlant.value = plant
  await fetchKnowledge(plant.plantId)
}

const submitPost = async () => {
    if (!postText.value) return
    try {
      await axios.post(`${API_BASE}/qna`, {
        userId: 2,
        content: postText.value,
        images: JSON.stringify(postImages.value.map(() => '/img_uploaded'))
      })
      postText.value = ''
      postImages.value = []
      isPosting.value = false
      activeCategory.value = 2
      await fetchQna()
    } catch (e) { console.error('发帖失败', e) }
}

const openLibraryDetail = (item) => {
  if (item.type === 'library') {
    selectedLibraryItem.value = item
  }
}
</script>

<template>
  <div class="bg-slate-50 min-h-full pb-8">
    <!-- Header -->
    <div class="bg-white px-6 pt-4 pb-2 sticky top-0 z-40 shadow-sm">
      <div class="flex justify-between items-center mb-4">
        <h1 class="text-3xl font-bold text-slate-900 tracking-tight">发现</h1>
        <button 
           v-if="activeCategory === 2"
           @click="isPosting = true"
           class="bg-slate-900 text-white px-4 py-2 rounded-xl text-sm font-bold shadow-lg shadow-slate-900/20 active:scale-95 transition-transform flex items-center space-x-1"
        >
           <span>✎</span>
           <span>提问</span>
        </button>
      </div>
      <div class="relative group mb-2">
        <input v-model="searchQuery" type="text" placeholder="搜索全站内容..." class="w-full bg-slate-50 text-slate-800 rounded-2xl py-3 pl-10 pr-4 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-green-500/20 transition-all border border-transparent focus:border-green-100" />
        <span class="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">🔍</span>
      </div>
    </div>

    <!-- Tabs -->
    <div class="bg-white/95 backdrop-blur-md px-6 pb-2 sticky top-[130px] z-30 border-b border-slate-50 transition-all duration-300">
      <div class="flex space-x-6 overflow-x-auto scrollbar-hide py-2">
        <button v-for="(cat, index) in categories" :key="index" @click="{ activeCategory = index; selectedPlant = null; }" class="flex-shrink-0 text-sm font-bold transition-all relative pb-2 whitespace-nowrap" :class="activeCategory === index ? 'text-green-600 scale-105' : 'text-slate-400'">
          {{ cat }}
          <div v-if="activeCategory === index" class="absolute bottom-0 left-1/2 -translate-x-1/2 w-4 h-1 bg-green-500 rounded-full"></div>
        </button>
      </div>
    </div>

    <!-- Feed Content -->
    <div class="px-4 py-4 space-y-4 mb-20 min-h-[400px]">
      <!-- Loading State -->
      <div v-if="loading" class="text-center py-20 text-slate-400">
        <span class="text-4xl block mb-2 animate-spin">⏳</span>
        <p>加载中...</p>
      </div>

      <transition-group v-else name="list" tag="div" class="space-y-4">
        <!-- Plant List View for Knowledge Base -->
        <div v-if="activeCategory === 1 && !selectedPlant" class="space-y-4" key="plant-list">
          <div 
             v-for="plant in currentItems" 
             :key="plant.id"
             @click="selectPlant(plant)"
             class="bg-white rounded-2xl p-5 shadow-sm border border-slate-100 active:scale-[0.98] transition-transform cursor-pointer relative"
          >
             <div class="flex justify-between items-start mb-3">
               <div class="flex items-center space-x-2">
                 <h3 class="text-lg font-bold text-slate-900">{{ plant.name }}</h3>
                 <span class="text-sm text-slate-500">({{ plant.enName }})</span>
               </div>
               <span class="text-slate-400 font-bold">›</span>
             </div>
             <div class="flex items-center space-x-3 mb-3">
                <span class="bg-green-100 text-green-600 px-2 py-0.5 rounded text-xs font-medium">{{ plant.tag }}</span>
             </div>
             <p class="text-sm text-slate-600 leading-relaxed">{{ plant.desc }}</p>
          </div>
        </div>

        <!-- Detail Grid layout for Knowledge Base -->
        <div v-else-if="activeCategory === 1 && selectedPlant" key="plant-detail">
          <div class="flex items-center space-x-3 mb-4">
             <button @click="selectedPlant = null" class="w-8 h-8 flex items-center justify-center bg-slate-200 text-slate-600 rounded-full font-bold">‹</button>
             <h2 class="text-xl font-bold text-slate-800">{{ selectedPlant.name }} 病虫害图鉴</h2>
          </div>
          <div class="grid grid-cols-2 gap-4">
            <div 
              v-for="item in currentItems" 
              :key="item.id" 
              @click="openLibraryDetail(item)"
              class="bg-white rounded-2xl overflow-hidden shadow-sm border border-slate-100 active:scale-95 transition-transform cursor-pointer group relative"
            >
               <!-- Favorite Button (Library Card) -->
               <button 
                 @click.stop="favStore.toggleFavorite(item)"
                 class="absolute top-2 right-2 z-10 w-8 h-8 rounded-full bg-white/80 backdrop-blur-sm flex items-center justify-center shadow-sm active:scale-90 transition-transform hover:scale-110"
               >
                 <span class="text-sm transition-transform duration-300" :class="favStore.isFavorite(item) ? 'scale-110 grayscale-0' : 'grayscale text-slate-300'">
                   {{ favStore.isFavorite(item) ? '⭐' : '☆' }}
                 </span>
               </button>

              <div class="h-32 bg-slate-200 relative overflow-hidden">
                 <div class="absolute inset-0 bg-gradient-to-t from-black/50 to-transparent z-[5]"></div>
                 <img :src="item.image" :alt="item.title" class="absolute inset-[0] w-full h-full object-cover group-hover:scale-110 transition-transform duration-500" />
                 <div class="absolute bottom-2 left-2 z-10 flex space-x-1">
                   <span class="text-white px-1.5 py-0.5 rounded text-[10px] font-medium shadow-sm" :class="getConditionColor(item.conditionType)">{{ item.conditionType }}</span>
                 </div>
              </div>
              <div class="p-3">
                <h3 class="font-bold text-slate-800 mb-1">{{ item.title }}</h3>
                <p class="text-[10px] text-slate-400 line-clamp-2">{{ item.desc }}</p>
              </div>
            </div>
          </div>
        </div>

        <!-- List layout for Recommendation & QnA -->
        <template v-else>
          <div v-for="(item, index) in currentItems" :key="item.id" class="relative group cursor-pointer" @click="item.type === 'news' ? selectedNewsItem = item : null">
             <!-- Favorite Button (News/QnA List Item) -->
             <button 
               @click.stop="favStore.toggleFavorite(item)"
               class="absolute top-2 right-2 z-[5] w-8 h-8 rounded-full bg-white/50 backdrop-blur-[2px] flex items-center justify-center active:scale-90 transition-transform hover:scale-110 hover:bg-white"
             >
               <span class="text-sm transition-transform duration-300" :class="favStore.isFavorite(item) ? 'scale-110 grayscale-0' : 'grayscale text-slate-300'">
                 {{ favStore.isFavorite(item) ? '⭐' : '☆' }}
               </span>
             </button>

            <QnACard v-if="item.type === 'qna'" v-bind="item" />
            <NewsCard v-else v-bind="item" class="h-32 flex-row" />
          </div>
        </template>
      </transition-group>
      
      <!-- Empty State -->
      <div v-if="!loading && currentItems.length === 0" class="text-center py-20 text-slate-400">
         <span class="text-4xl block mb-2">🍃</span>
         <p>暂无相关内容</p>
      </div>
    </div>

    <!-- Library Detail Modal -->
    <div v-if="selectedLibraryItem" class="absolute inset-0 z-50 flex items-end sm:items-center justify-center">
      <div class="absolute inset-0 bg-slate-900/60 backdrop-blur-sm" @click="selectedLibraryItem = null"></div>
      <div class="bg-white w-full sm:w-[90%] sm:rounded-[2rem] rounded-t-[2rem] h-[80%] sm:h-auto overflow-hidden relative z-10 shadow-2xl animate-slide-up">
        <div class="h-64 bg-slate-200 relative overflow-hidden">
          <button @click="selectedLibraryItem = null" class="absolute top-4 right-4 w-8 h-8 bg-black/20 backdrop-blur-md rounded-full text-white flex items-center justify-center font-bold z-20">✕</button>
          <img :src="selectedLibraryItem.image" :alt="selectedLibraryItem.title" class="absolute inset-0 w-full h-full object-cover" />
          <div class="absolute bottom-0 inset-x-0 h-32 bg-gradient-to-t from-black/70 to-transparent"></div>
          <div class="absolute bottom-6 left-6">
            <div class="flex space-x-2 mb-2">
              <span class="text-white px-2 py-1 rounded-lg text-xs font-bold inline-block shadow-sm" :class="getConditionColor(selectedLibraryItem.conditionType)">{{ selectedLibraryItem.conditionType }}</span>
              <span v-if="selectedLibraryItem.plant !== '全部'" class="bg-black/50 backdrop-blur-md text-white px-2 py-1 rounded-lg text-xs font-bold inline-block">{{ selectedLibraryItem.plant }}</span>
            </div>
            <h2 class="text-3xl font-bold text-white">{{ selectedLibraryItem.title }}</h2>
          </div>
        </div>
        <div class="p-6 overflow-y-auto h-[calc(100%-16rem)]">
          <h3 class="font-bold text-slate-800 mb-2 flex items-center"><span class="w-1 h-4 rounded-full mr-2" :class="getConditionColor(selectedLibraryItem.conditionType)"></span>{{ selectedLibraryItem.conditionType === '健康' ? '生长特征' : '症状特征' }}</h3>
          <p class="text-slate-600 text-sm leading-relaxed mb-6">{{ selectedLibraryItem.desc }}</p>
          <h3 class="font-bold text-slate-800 mb-2 flex items-center"><span class="w-1 h-4 rounded-full mr-2" :class="selectedLibraryItem.conditionType === '健康' ? 'bg-blue-500' : 'bg-orange-500'"></span>{{ selectedLibraryItem.conditionType === '健康' ? '养护建议' : '防治方法' }}</h3>
          <div class="space-y-2">
            <div v-for="(method, index) in (selectedLibraryItem.prevention || ['暂无数据。'])" :key="index" class="bg-slate-50 p-3 rounded-xl text-xs text-slate-600 border border-slate-100">{{ method }}</div>
          </div>
        </div>
        <div class="p-4 border-t border-slate-50 flex space-x-3">
          <button 
             @click="favStore.toggleFavorite(selectedLibraryItem)"
             class="flex-1 py-3 rounded-xl font-bold text-sm transition-colors flex items-center justify-center space-x-2"
             :class="favStore.isFavorite(selectedLibraryItem) ? 'bg-amber-50 text-amber-500' : 'bg-slate-100 text-slate-600'"
          >
             <span>{{ favStore.isFavorite(selectedLibraryItem) ? '⭐ 已收藏' : '☆ 收藏图鉴' }}</span>
          </button>
          <button class="flex-1 py-3 rounded-xl bg-slate-900 text-white font-bold text-sm">立即识别</button>
        </div>
      </div>
    </div>

    <!-- News Detail Modal -->
    <div v-if="selectedNewsItem" class="absolute inset-0 z-50 flex items-end sm:items-center justify-center">
      <div class="absolute inset-0 bg-slate-900/60 backdrop-blur-sm transition-opacity" @click="selectedNewsItem = null"></div>
      <div class="bg-white w-full sm:w-[90%] sm:rounded-[2rem] rounded-t-[2rem] h-[85%] sm:h-auto overflow-hidden relative z-10 shadow-2xl animate-slide-up flex flex-col">
        <div class="px-6 py-4 border-b border-slate-100 flex justify-between items-center relative">
           <button @click="selectedNewsItem = null" class="w-8 h-8 flex items-center justify-center bg-slate-100 text-slate-600 rounded-full font-bold active:scale-90 transition-transform">✕</button>
           <span class="font-bold text-slate-800 absolute left-1/2 -translate-x-1/2">资讯详情</span>
           <button 
               @click.stop="favStore.toggleFavorite(selectedNewsItem)"
               class="w-8 h-8 rounded-full flex items-center justify-center active:scale-90 transition-transform"
             >
               <span class="text-xl transition-transform duration-300" :class="favStore.isFavorite(selectedNewsItem) ? 'scale-110 grayscale-0' : 'grayscale text-slate-300'">
                 {{ favStore.isFavorite(selectedNewsItem) ? '⭐' : '☆' }}
               </span>
           </button>
        </div>
        <div class="flex-1 overflow-y-auto p-6">
           <div class="mb-4">
             <span class="bg-blue-100 text-blue-600 px-2 py-1 rounded text-xs font-medium inline-block mb-3">{{ selectedNewsItem.tag }}</span>
             <h2 class="text-xl font-bold text-slate-900 leading-tight mb-3">{{ selectedNewsItem.title }}</h2>
             <div class="flex items-center space-x-4 text-xs text-slate-400">
               <span>{{ selectedNewsItem.date }}</span>
               <span class="flex items-center space-x-1"><span>👁️</span><span>{{ selectedNewsItem.views }} 阅读</span></span>
             </div>
           </div>
           <div class="prose prose-slate prose-sm max-w-none text-slate-700 leading-loose whitespace-pre-wrap">
              {{ selectedNewsItem.content }}
           </div>
        </div>
      </div>
    </div>

    <!-- Post Modal -->
    <div v-if="isPosting" class="absolute inset-0 z-50 flex items-end sm:items-center justify-center">
      <div class="absolute inset-0 bg-black/60 backdrop-blur-sm" @click="isPosting = false"></div>
      <div class="bg-white w-full sm:w-[90%] sm:rounded-[2rem] rounded-t-[2rem] p-6 relative z-10 shadow-2xl animate-slide-up">
         <div class="flex justify-between items-center mb-6">
            <h3 class="text-xl font-bold text-slate-800">向专家提问</h3>
            <button @click="isPosting = false" class="text-slate-400 font-bold px-2">✕</button>
         </div>
         <textarea v-model="postText" placeholder="请详细描述病虫害症状..." class="w-full h-32 bg-slate-50 rounded-xl p-4 text-sm mb-4 focus:outline-none focus:ring-2 focus:ring-green-500/20 resize-none"></textarea>
         <div class="mb-6">
            <div class="flex items-center justify-between mb-2"><span class="text-xs font-bold text-slate-500">上传照片 ({{ postImages.length }}/3)</span></div>
            <div class="flex space-x-3">
               <div v-for="(img, idx) in postImages" :key="idx" class="w-20 h-20 bg-slate-100 rounded-xl overflow-hidden relative border border-slate-200">
                  <div class="w-full h-full flex items-center justify-center text-slate-300">图{{idx+1}}</div>
                  <button @click="postImages.splice(idx, 1)" class="absolute top-0 right-0 bg-red-500 text-white w-5 h-5 flex items-center justify-center rounded-bl-lg text-xs">×</button>
               </div>
               <button v-if="postImages.length < 3" @click="handleImageUpload" class="w-20 h-20 border-2 border-dashed border-slate-300 rounded-xl flex flex-col items-center justify-center text-slate-400"><span>+</span></button>
            </div>
         </div>
         <button @click="submitPost" class="w-full bg-slate-900 text-white py-4 rounded-xl font-bold shadow-lg shadow-slate-900/20 active:scale-[0.98] transition-transform disabled:opacity-50" :disabled="!postText">提交问题</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes slide-up { from { transform: translateY(100%); } to { transform: translateY(0); } }
.animate-slide-up { animation: slide-up 0.3s cubic-bezier(0.16, 1, 0.3, 1); }
.list-move, .list-enter-active, .list-leave-active { transition: all 0.5s ease; }
.list-enter-from, .list-leave-to { opacity: 0; transform: translateY(30px); }
.list-leave-active { position: absolute; }
</style>
