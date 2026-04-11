<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useFavoritesStore } from '../../stores/favoritesCloud'
import NewsCard from '../../components/mobile/NewsCard.vue'
import QnACard from '../../components/mobile/QnACard.vue'
import axios from 'axios'
import { AUTH_CHANGE_EVENT, getStoredUser, requireLoggedInUser } from '../../utils/accountSecurity'

const route = useRoute()

const API_BASE = '/api/discovery'

const favStore = useFavoritesStore()

// Categories
const categories = ['推荐', '知识库', '问答圈']
const activeCategory = ref(0)
const searchQuery = ref('')
const selectedPlant = ref(null)
const isPosting = ref(false)
const selectedLibraryItem = ref(null)
const selectedNewsItem = ref(null)
const selectedQnaItem = ref(null)

// Reactive data stores (populated from API)
const newsData = ref([])
const plantsData = ref([])
const knowledgeData = ref([])
const qnaData = ref([])
const likedPostIds = ref(new Set())
const loading = ref(false)

// Q&A Post State
const postText = ref('')
const postImages = ref([])
const fileInput = ref(null)
const currentUser = ref(null)

const syncCurrentUser = () => {
  currentUser.value = getStoredUser()
  return currentUser.value
}

const requireDiscoveryUser = (message) => {
  const user = requireLoggedInUser(message)
  if (user?.userId) {
    currentUser.value = user
  }
  return user
}

const hydrateDiscoverySession = async () => {
  const user = syncCurrentUser()
  await favStore.loadFavorites(user?.userId)
}

const handleAuthChange = () => {
  hydrateDiscoverySession()
}

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
        ...c,
        user: c.userName,
        userAvatar: c.userAvatar || '',
        content: c.content,
        time: formatTime(c.createdAt)
      }))
    }))
  } catch (e) { console.error('获取问答数据失败', e) }
}

const fetchLikedPostIds = async () => {
  try {
    const user = currentUser.value
    if (!user?.userId) return
    const { data } = await axios.get(`${API_BASE}/qna/liked?userId=${user.userId}`)
    if (data.code === 200 && Array.isArray(data.data)) {
      likedPostIds.value = new Set(data.data)
    }
  } catch (e) { console.error('获取点赞状态失败', e) }
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
  window.addEventListener(AUTH_CHANGE_EVENT, handleAuthChange)
  await hydrateDiscoverySession()

  // 从收藏页跳转过来时，自动切换到对应 tab
  const tabParam = Number(route.query.tab)
  if (!isNaN(tabParam) && tabParam >= 0 && tabParam < categories.length) {
    activeCategory.value = tabParam
  }

  loading.value = true
  await Promise.all([fetchNews(), fetchPlants(), fetchQna(), fetchLikedPostIds()])
  loading.value = false
})

onUnmounted(() => {
  window.removeEventListener(AUTH_CHANGE_EVENT, handleAuthChange)
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
const openPostModal = () => {
  const user = requireDiscoveryUser('请先登录后再提问！')
  if (!user) return
  currentUser.value = user
  isPosting.value = true;
}

const handleFavoriteToggle = async (item) => {
  const user = requireDiscoveryUser('请先登录后再收藏。')
  if (!user) return

  await favStore.toggleFavorite(item)
}

const handleImageUpload = () => {
  if (postImages.value.length < 3) fileInput.value.click();
}

const onFileSelection = async (event) => {
  const files = Array.from(event.target.files);
  if (files.length + postImages.value.length > 3) {
    alert('最多只能上传3张图片！');
    return;
  }
  for (const file of files) {
    const formData = new FormData();
    formData.append('file', file);
    try {
      const res = await axios.post(`${API_BASE}/upload`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      if (res.data.code === 200) {
        postImages.value.push(res.data.data);
      }
    } catch (e) {
       console.error('图片上传失败', e);
    }
  }
  event.target.value = '';
}

const selectPlant = async (plant) => {
  selectedPlant.value = plant
  await fetchKnowledge(plant.plantId)
}

  const submitPost = async () => {
      if (!postText.value || !currentUser.value) return
      try {
        await axios.post(`${API_BASE}/qna`, {
          userId: currentUser.value.userId,
          content: postText.value,
          images: JSON.stringify(postImages.value)
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

const openQnaDetail = (item) => {
  if (item.type === 'qna') {
    selectedQnaItem.value = item
  }
}

const deletePost = async (postId) => {
  if (!confirm('确定要删除这条提问吗？')) return;
  try {
    const res = await axios.delete(`${API_BASE}/qna/${postId}`);
    if (res.data.code === 200) {
      await fetchQna();
    }
  } catch (e) { console.error('删除失败', e); }
}
</script>

<template>
  <div class="discovery-page bg-slate-50 dark:bg-slate-950 min-h-full pb-8 transition-colors">
    <!-- Header -->
    <div class="discovery-header bg-white dark:bg-slate-900/95 px-6 pt-4 pb-2 sticky top-0 z-40 shadow-sm dark:shadow-[0_12px_32px_rgba(2,6,23,0.35)] transition-colors">
      <div class="flex justify-between items-center mb-4 max-w-7xl mx-auto">
        <h1 class="text-3xl font-bold text-slate-900 tracking-tight">发现</h1>
        <button 
           v-if="activeCategory === 2"
           @click="openPostModal"
           class="bg-slate-900 dark:bg-emerald-400 dark:text-slate-950 text-white px-4 py-2 rounded-xl text-sm font-bold shadow-lg shadow-slate-900/20 dark:shadow-emerald-500/20 active:scale-95 transition-transform flex items-center space-x-1"
        >
           <span>✎</span>
           <span>提问</span>
        </button>
      </div>
      <div class="max-w-7xl mx-auto">
        <div class="discovery-search relative group mb-2">
          <input v-model="searchQuery" type="text" placeholder="搜索全站内容..." class="w-full bg-slate-50 text-slate-800 rounded-2xl py-3 pl-10 pr-4 text-sm font-medium focus:outline-none focus:ring-2 focus:ring-green-500/20 transition-all border border-transparent focus:border-green-100" />
          <span class="absolute left-3 top-1/2 -translate-y-1/2 text-slate-400">🔍</span>
        </div>
      </div>
    </div>

    <!-- Layout Container -->
    <div class="lg:flex lg:flex-row lg:items-start lg:gap-8 lg:px-6 lg:mt-6 max-w-7xl mx-auto">
      <!-- Tabs (Sidebar on PC) -->
      <div class="discovery-tabs bg-white/95 dark:bg-slate-900/90 backdrop-blur-md px-6 pb-2 lg:p-0 lg:bg-transparent sticky top-[130px] lg:top-[150px] z-30 border-b border-slate-50 dark:border-slate-800 lg:border-none transition-all duration-300 lg:w-48 lg:shrink-0 rounded-2xl">
        <div class="flex lg:flex-col space-x-6 lg:space-x-0 lg:space-y-3 overflow-x-auto scrollbar-hide py-2 lg:py-0">
          <button v-for="(cat, index) in categories" :key="index" @click="{ activeCategory = index; selectedPlant = null; }" class="flex-shrink-0 lg:w-full lg:text-left lg:px-4 lg:py-3 lg:rounded-xl text-sm font-bold transition-all relative pb-2 lg:pb-3 whitespace-nowrap lg:whitespace-normal" :class="activeCategory === index ? 'text-green-600 lg:bg-green-50 lg:scale-[1.02] scale-105' : 'text-slate-400 hover:text-slate-600 lg:hover:bg-slate-100'">
            {{ cat }}
            <div v-if="activeCategory === index" class="absolute bottom-0 left-1/2 -translate-x-1/2 w-4 h-1 bg-green-500 rounded-full lg:hidden"></div>
          </button>
        </div>
      </div>

      <!-- Feed Content -->
      <div class="px-4 lg:px-0 py-4 space-y-4 lg:space-y-0 mb-20 min-h-[400px] lg:flex-1">
        <!-- Loading State -->
        <div v-if="loading" class="discovery-loading text-center py-20 text-slate-400 dark:text-slate-500">
          <span class="text-4xl block mb-2 animate-spin">⏳</span>
          <p>加载中...</p>
        </div>

        <transition-group v-else name="list" tag="div" 
          class="space-y-4 lg:space-y-0 lg:grid lg:gap-6"
          :class="{
            'lg:grid-cols-2': activeCategory === 0 || activeCategory === 2,
            'lg:grid-cols-3': activeCategory === 1 && !selectedPlant,
            'lg:grid-cols-1': activeCategory === 1 && selectedPlant
          }"
        >
          <!-- Plant List View for Knowledge Base -->
          <template v-if="activeCategory === 1 && !selectedPlant" key="plant-list">
            <div 
               v-for="plant in currentItems" 
               :key="plant.id"
               @click="selectPlant(plant)"
               class="discovery-plant-card bg-white dark:bg-slate-900 rounded-2xl p-5 shadow-sm dark:shadow-[0_12px_36px_rgba(2,6,23,0.28)] border border-slate-100 dark:border-slate-800 active:scale-[0.98] lg:hover:shadow-md lg:hover:-translate-y-1 transition-all cursor-pointer relative"
            >
               <div class="flex justify-between items-start mb-3">
                 <div class="flex items-center space-x-2">
                   <h3 class="text-lg font-bold text-slate-900 dark:text-slate-100">{{ plant.name }}</h3>
                   <span class="text-sm text-slate-500 dark:text-slate-400">({{ plant.enName }})</span>
                 </div>
                 <span class="text-slate-400 font-bold">›</span>
               </div>
               <div class="flex items-center space-x-3 mb-3">
                  <span class="bg-green-100 dark:bg-emerald-500/10 text-green-600 dark:text-emerald-300 px-2 py-0.5 rounded text-xs font-medium">{{ plant.tag }}</span>
               </div>
               <p class="text-sm text-slate-600 dark:text-slate-300 leading-relaxed">{{ plant.desc }}</p>
            </div>
          </template>

          <!-- Detail Grid layout for Knowledge Base -->
          <div v-else-if="activeCategory === 1 && selectedPlant" key="plant-detail" class="lg:col-span-full">
            <div class="discovery-plant-detail-header flex items-center space-x-3 mb-4">
               <button @click="selectedPlant = null" class="w-8 h-8 flex items-center justify-center bg-slate-200 text-slate-600 rounded-full font-bold lg:hover:bg-slate-300 transition-colors">‹</button>
               <h2 class="text-xl font-bold text-slate-800">{{ selectedPlant.name }} 病虫害图鉴</h2>
            </div>
            <div class="grid grid-cols-2 lg:grid-cols-4 gap-4">
              <div 
                v-for="item in currentItems" 
                :key="item.id" 
                @click="openLibraryDetail(item)"
                class="discovery-library-card bg-white dark:bg-slate-900 rounded-2xl overflow-hidden shadow-sm dark:shadow-[0_12px_36px_rgba(2,6,23,0.28)] border border-slate-100 dark:border-slate-800 active:scale-95 lg:hover:shadow-lg lg:hover:-translate-y-1 transition-all cursor-pointer group relative"
              >
                 <!-- Favorite Button (Library Card) -->
                 <button 
                   @click.stop="handleFavoriteToggle(item)"
                   :disabled="favStore.isFavoriteSubmitting"
                   class="discovery-favorite-btn absolute top-2 right-2 z-10 w-8 h-8 rounded-full bg-white/80 dark:bg-slate-950/80 backdrop-blur-sm flex items-center justify-center shadow-sm active:scale-90 transition-transform hover:scale-110"
                 >
                   <span class="text-sm transition-transform duration-300" :class="favStore.isFavorite(item) ? 'scale-110 grayscale-0' : 'grayscale text-slate-300'">
                     {{ favStore.isFavorite(item) ? '⭐' : '☆' }}
                   </span>
                 </button>

                <div class="h-32 lg:h-40 bg-slate-200 relative overflow-hidden">
                   <div class="absolute inset-0 bg-gradient-to-t from-black/50 to-transparent z-[5]"></div>
                   <img :src="item.image" :alt="item.title" class="absolute inset-[0] w-full h-full object-cover group-hover:scale-110 transition-transform duration-500" />
                   <div class="absolute bottom-2 left-2 z-10 flex space-x-1">
                     <span class="text-white px-1.5 py-0.5 rounded text-[10px] font-medium shadow-sm" :class="getConditionColor(item.conditionType)">{{ item.conditionType }}</span>
                   </div>
                </div>
                <div class="p-3 lg:p-4">
                  <h3 class="font-bold text-slate-800 dark:text-slate-100 mb-1 lg:mb-2">{{ item.title }}</h3>
                  <p class="text-[10px] lg:text-xs text-slate-400 dark:text-slate-500 line-clamp-2">{{ item.desc }}</p>
                </div>
              </div>
            </div>
          </div>

          <!-- List layout for Recommendation & QnA -->
          <template v-else>
            <div v-for="(item, index) in currentItems" :key="item.id" class="relative group cursor-pointer lg:h-full lg:flex lg:flex-col" @click="item.type === 'news' ? selectedNewsItem = item : (item.type === 'qna' ? openQnaDetail(item) : null)">
               <!-- Favorite Button (News/QnA List Item) -->
               <button 
                  @click.stop="handleFavoriteToggle(item)"
                  :disabled="favStore.isFavoriteSubmitting"
                  class="discovery-favorite-btn absolute top-2 right-2 z-[5] w-8 h-8 rounded-full bg-white/50 dark:bg-slate-950/60 backdrop-blur-[2px] flex items-center justify-center active:scale-90 transition-transform hover:scale-110 hover:bg-white dark:hover:bg-slate-900 lg:opacity-0 lg:group-hover:opacity-100"
               >
                 <span class="text-sm transition-transform duration-300" :class="favStore.isFavorite(item) ? 'scale-110 grayscale-0' : 'grayscale text-slate-300'">
                   {{ favStore.isFavorite(item) ? '⭐' : '☆' }}
                 </span>
               </button>

              <QnACard v-if="item.type === 'qna'" v-bind="item" :currentUserId="currentUser?.userId" :likedPostIds="likedPostIds" @delete="deletePost(item.id)" class="lg:h-full lg:hover:shadow-md transition-shadow" />
              <NewsCard v-else v-bind="item" class="discovery-news-card h-32 lg:h-36 flex-row lg:hover:shadow-md transition-shadow" />
            </div>
          </template>
        </transition-group>
        
        <!-- Empty State -->
        <div v-if="!loading && currentItems.length === 0" class="discovery-empty text-center py-20 text-slate-400 dark:text-slate-500 lg:col-span-full">
           <span class="text-4xl block mb-2">🍃</span>
           <p>暂无相关内容</p>
        </div>
      </div>
    </div>

    <!-- Library Detail Modal -->
    <div v-if="selectedLibraryItem" class="fixed inset-0 z-50 flex items-end sm:items-center justify-center">
      <div class="absolute inset-0 bg-slate-900/60 backdrop-blur-sm" @click="selectedLibraryItem = null"></div>
      <div class="discovery-modal-panel discovery-library-modal bg-white dark:bg-slate-900 w-full sm:w-[90%] lg:w-full lg:max-w-4xl sm:rounded-[2rem] rounded-t-[2rem] h-[80%] sm:h-auto lg:h-[80vh] overflow-hidden relative z-10 shadow-2xl dark:shadow-[0_24px_70px_rgba(2,6,23,0.55)] animate-slide-up flex flex-col transition-colors">
        <div class="h-64 lg:h-80 bg-slate-200 relative overflow-hidden shrink-0">
          <button @click="selectedLibraryItem = null" class="absolute top-4 right-4 w-10 h-10 bg-black/40 hover:bg-black/60 backdrop-blur-md rounded-full text-white flex items-center justify-center font-bold z-20 transition-colors">✕</button>
          <img :src="selectedLibraryItem.image" :alt="selectedLibraryItem.title" class="absolute inset-0 w-full h-full object-cover" />
          <div class="absolute bottom-0 inset-x-0 h-40 bg-gradient-to-t from-black/80 to-transparent"></div>
          <div class="absolute bottom-6 left-6 lg:left-8">
            <div class="flex space-x-2 mb-3">
              <span class="text-white px-3 py-1.5 rounded-lg text-xs font-bold inline-block shadow-sm" :class="getConditionColor(selectedLibraryItem.conditionType)">{{ selectedLibraryItem.conditionType }}</span>
              <span v-if="selectedLibraryItem.plant !== '全部'" class="bg-black/50 backdrop-blur-md text-white px-3 py-1.5 rounded-lg text-xs font-bold inline-block">{{ selectedLibraryItem.plant }}</span>
            </div>
            <h2 class="text-3xl lg:text-4xl font-black text-white tracking-tight">{{ selectedLibraryItem.title }}</h2>
          </div>
        </div>
        <div class="discovery-modal-body custom-scrollbar flex-1 overflow-y-auto p-6 lg:p-8">
          <div class="max-w-3xl mx-auto">
            <h3 class="font-bold text-slate-800 text-lg mb-3 flex items-center"><span class="w-1.5 h-5 rounded-full mr-2" :class="getConditionColor(selectedLibraryItem.conditionType)"></span>{{ selectedLibraryItem.conditionType === '健康' ? '生长特征' : '症状特征' }}</h3>
            <p class="text-slate-600 text-[15px] leading-relaxed mb-8">{{ selectedLibraryItem.desc }}</p>
            <h3 class="font-bold text-slate-800 text-lg mb-3 flex items-center"><span class="w-1.5 h-5 rounded-full mr-2" :class="selectedLibraryItem.conditionType === '健康' ? 'bg-blue-500' : 'bg-orange-500'"></span>{{ selectedLibraryItem.conditionType === '健康' ? '养护建议' : '防治方法' }}</h3>
            <div class="space-y-3">
              <div v-for="(method, index) in (selectedLibraryItem.prevention || ['暂无数据。'])" :key="index" class="bg-slate-50 p-4 rounded-xl text-[15px] text-slate-700 border border-slate-100">{{ method }}</div>
            </div>
          </div>
        </div>
        <div class="p-4 lg:p-6 border-t border-slate-100 dark:border-slate-800 flex space-x-4 bg-white dark:bg-slate-900 shrink-0 transition-colors">
          <button 
             @click="handleFavoriteToggle(selectedLibraryItem)"
             :disabled="favStore.isFavoriteSubmitting"
             class="flex-1 lg:flex-none lg:w-48 py-3.5 rounded-xl font-bold text-[15px] transition-colors flex items-center justify-center space-x-2"
             :class="favStore.isFavorite(selectedLibraryItem) ? 'bg-amber-50 text-amber-500' : 'bg-slate-100 text-slate-600 lg:hover:bg-slate-200'"
          >
             <span>{{ favStore.isFavorite(selectedLibraryItem) ? '⭐ 已收藏' : '☆ 收藏图鉴' }}</span>
          </button>
          <button class="flex-1 py-3.5 rounded-xl bg-slate-900 text-white font-bold text-[15px] lg:hover:bg-black transition-colors shadow-lg shadow-slate-900/20 active:scale-[0.98]">立即识别</button>
        </div>
      </div>
    </div>

    <!-- News Detail Modal -->
    <div v-if="selectedNewsItem" class="fixed inset-0 z-50 flex items-end sm:items-center justify-center">
      <div class="absolute inset-0 bg-slate-900/60 backdrop-blur-sm transition-opacity" @click="selectedNewsItem = null"></div>
      <div class="discovery-modal-panel discovery-news-modal bg-white dark:bg-slate-900 w-full sm:w-[90%] lg:w-full lg:max-w-3xl sm:rounded-[2rem] rounded-t-[2rem] h-[85%] sm:h-auto lg:h-[85vh] overflow-hidden relative z-10 shadow-2xl dark:shadow-[0_24px_70px_rgba(2,6,23,0.55)] animate-slide-up flex flex-col transition-colors">
        <div class="discovery-modal-header px-6 py-4 border-b border-slate-100 dark:border-slate-800 flex justify-between items-center relative">
           <button @click="selectedNewsItem = null" class="w-8 h-8 flex items-center justify-center bg-slate-100 text-slate-600 rounded-full font-bold active:scale-90 transition-transform">✕</button>
           <span class="font-bold text-slate-800 absolute left-1/2 -translate-x-1/2">资讯详情</span>
           <button 
               @click.stop="handleFavoriteToggle(selectedNewsItem)"
               :disabled="favStore.isFavoriteSubmitting"
               class="w-8 h-8 rounded-full flex items-center justify-center active:scale-90 transition-transform"
             >
               <span class="text-xl transition-transform duration-300" :class="favStore.isFavorite(selectedNewsItem) ? 'scale-110 grayscale-0' : 'grayscale text-slate-300'">
                 {{ favStore.isFavorite(selectedNewsItem) ? '⭐' : '☆' }}
               </span>
           </button>
        </div>
         <div class="discovery-modal-body custom-scrollbar flex-1 overflow-y-auto p-6 lg:p-10">
           <div class="mb-6 lg:mb-8 text-center max-w-2xl mx-auto">
             <span class="bg-blue-100 text-blue-600 px-3 py-1.5 rounded-lg text-xs font-bold inline-block mb-4">{{ selectedNewsItem.tag }}</span>
             <h2 class="text-2xl lg:text-3xl font-black text-slate-900 leading-tight mb-4 tracking-tight">{{ selectedNewsItem.title }}</h2>
             <div class="flex items-center justify-center space-x-4 text-xs font-medium text-slate-400">
               <span>{{ selectedNewsItem.date }}</span>
               <span class="w-1 h-1 rounded-full bg-slate-300"></span>
               <span class="flex items-center space-x-1.5"><span>👁️</span><span>{{ selectedNewsItem.views }} 阅读</span></span>
             </div>
           </div>
            <div class="prose prose-slate prose-sm lg:prose-base max-w-2xl mx-auto text-slate-700 leading-loose whitespace-pre-wrap">
              {{ selectedNewsItem.content }}
           </div>
        </div>
      </div>
    </div>

    <!-- Post Modal -->
    <div v-if="isPosting" class="fixed inset-0 z-50 flex items-end sm:items-center justify-center">
      <div class="absolute inset-0 bg-black/60 backdrop-blur-sm" @click="isPosting = false"></div>
      <div class="discovery-modal-panel discovery-post-modal bg-white dark:bg-slate-900 w-full sm:w-[90%] lg:w-full lg:max-w-2xl sm:rounded-[2rem] rounded-t-[2rem] p-6 lg:p-8 pb-24 lg:pb-8 relative z-10 shadow-2xl dark:shadow-[0_24px_70px_rgba(2,6,23,0.55)] animate-slide-up transition-colors">
         <div class="flex justify-between items-center mb-6">
            <h3 class="text-xl lg:text-2xl font-black text-slate-800">向专家提问</h3>
            <button @click="isPosting = false" class="text-slate-400 hover:text-slate-600 font-bold px-2 text-xl transition-colors">✕</button>
         </div>
         <textarea v-model="postText" placeholder="请详细描述病虫害症状、发生时间、作物品种等信息..." class="w-full h-32 lg:h-40 bg-slate-50 border border-slate-100 rounded-xl p-4 text-[15px] mb-4 focus:outline-none focus:ring-2 focus:ring-green-500/20 focus:border-green-300 focus:bg-white transition-all resize-none"></textarea>
         <div class="mb-8">
            <div class="flex items-center justify-between mb-3"><span class="text-sm font-bold text-slate-700">上传照片 ({{ postImages.length }}/3)</span></div>
            <div class="flex space-x-4">
               <div v-for="(img, idx) in postImages" :key="idx" class="discovery-upload-tile w-24 h-24 bg-slate-100 dark:bg-slate-800 rounded-xl overflow-hidden relative border border-slate-200 dark:border-slate-700 group">
                  <img :src="img" class="w-full h-full object-cover" />
                  <button @click="postImages.splice(idx, 1)" class="absolute top-1 right-1 bg-red-500/80 hover:bg-red-500 active:scale-90 transition-all text-white w-6 h-6 flex items-center justify-center rounded-full shadow-sm text-sm z-10 opacity-100 lg:opacity-0 lg:group-hover:opacity-100">×</button>
               </div>
               <button v-if="postImages.length < 3" @click="handleImageUpload" class="discovery-upload-trigger w-24 h-24 border-2 border-dashed border-slate-300 dark:border-slate-700 hover:border-green-400 dark:hover:border-emerald-400 hover:bg-green-50 dark:hover:bg-emerald-500/10 transition-colors rounded-xl flex flex-col items-center justify-center text-slate-400 dark:text-slate-500 hover:text-green-500 dark:hover:text-emerald-300"><span class="text-2xl">+</span></button>
               <input type="file" ref="fileInput" accept="image/*" multiple class="hidden" @change="onFileSelection" />
            </div>
         </div>
         <button @click="submitPost" class="w-full bg-slate-900 text-white py-4 rounded-xl font-bold text-[15px] shadow-lg shadow-slate-900/20 active:scale-[0.98] lg:hover:bg-black transition-all disabled:opacity-50 disabled:active:scale-100" :disabled="!postText">提交问题</button>
      </div>
    </div>

    <!-- QnA Detail Modal -->
    <div v-if="selectedQnaItem" class="fixed inset-0 z-50 flex items-end sm:items-center justify-center">
      <div class="absolute inset-0 bg-slate-900/60 backdrop-blur-[2px] transition-opacity" @click="selectedQnaItem = null"></div>
      <div class="discovery-modal-panel discovery-qna-modal bg-slate-50 dark:bg-slate-950 w-full sm:w-[90%] lg:w-full lg:max-w-2xl sm:rounded-[2rem] rounded-t-[2rem] h-[85%] sm:h-auto lg:h-[85vh] lg:max-h-[800px] overflow-hidden relative z-10 shadow-2xl dark:shadow-[0_24px_70px_rgba(2,6,23,0.55)] animate-slide-up flex flex-col transition-colors">
        <div class="discovery-modal-header px-6 py-4 border-b border-slate-200 dark:border-slate-800 bg-white dark:bg-slate-900 flex justify-between items-center relative shrink-0 z-20 transition-colors">
           <button @click="selectedQnaItem = null" class="w-8 h-8 flex items-center justify-center bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-full font-bold active:scale-90 transition-all">✕</button>
           <span class="font-bold text-slate-800 absolute left-1/2 -translate-x-1/2">互动详情</span>
        </div>
        <div class="flex-1 overflow-y-auto p-4 sm:p-6 pb-24 lg:pb-6 custom-scrollbar">
            <QnACard v-bind="selectedQnaItem" :currentUserId="currentUser?.userId" :likedPostIds="likedPostIds" @delete="deletePost(selectedQnaItem.id)" class="shadow-none border-none mb-4" />
        </div>
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
.custom-scrollbar::-webkit-scrollbar { width: 6px; }
.custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
.custom-scrollbar::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 10px; }
.custom-scrollbar::-webkit-scrollbar-thumb:hover { background: #94a3b8; }

:global(.dark .discovery-page) {
  color: #e2e8f0;
}

:global(.dark .discovery-header h1) {
  color: #f8fafc;
}

:global(.dark .discovery-search input) {
  background: #1e293b;
  border-color: #1e293b;
  color: #f8fafc;
}

:global(.dark .discovery-search input::placeholder) {
  color: #64748b;
}

:global(.dark .discovery-search span) {
  color: #64748b;
}

:global(.dark .discovery-tabs button.text-green-600) {
  background: rgba(16, 185, 129, 0.1);
  color: #6ee7b7;
}

:global(.dark .discovery-tabs button.text-slate-400) {
  color: #64748b;
}

:global(.dark .discovery-tabs button.text-slate-400:hover) {
  background: rgba(30, 41, 59, 0.7);
  color: #e2e8f0;
}

:global(.dark .discovery-plant-detail-header h2) {
  color: #f8fafc;
}

:global(.dark .discovery-plant-detail-header button) {
  background: #1e293b;
  color: #cbd5e1;
}

:global(.dark .discovery-library-card .bg-slate-200) {
  background: #1e293b;
}

:global(.dark .discovery-news-card) {
  background: #0f172a;
  border-color: #1e293b;
  box-shadow: 0 12px 36px rgba(2, 6, 23, 0.28);
}

:global(.dark .discovery-news-card > div:first-child) {
  background: #1e293b;
}

:global(.dark .discovery-news-card h3) {
  color: #e2e8f0;
}

:global(.dark .discovery-news-card > div:last-child > div) {
  color: #94a3b8;
}

:global(.dark .discovery-favorite-btn) {
  box-shadow: 0 10px 24px rgba(2, 6, 23, 0.35);
}

:global(.dark .discovery-modal-panel) {
  color: #e2e8f0;
}

:global(.dark .discovery-modal-header) {
  background: #0f172a;
}

:global(.dark .discovery-modal-header button) {
  background: #1e293b;
  color: #cbd5e1;
}

:global(.dark .discovery-modal-panel .text-slate-900),
:global(.dark .discovery-modal-panel .text-slate-800) {
  color: #f8fafc;
}

:global(.dark .discovery-modal-panel .text-slate-700),
:global(.dark .discovery-modal-panel .text-slate-600) {
  color: #cbd5e1;
}

:global(.dark .discovery-modal-panel .text-slate-500),
:global(.dark .discovery-modal-panel .text-slate-400) {
  color: #94a3b8;
}

:global(.dark .discovery-modal-panel .bg-slate-50) {
  background: rgba(15, 23, 42, 0.78);
}

:global(.dark .discovery-modal-panel .bg-slate-100),
:global(.dark .discovery-modal-panel .bg-slate-200) {
  background: #1e293b;
}

:global(.dark .discovery-modal-panel .border-slate-100),
:global(.dark .discovery-modal-panel .border-slate-200) {
  border-color: #1e293b;
}

:global(.dark .discovery-library-modal > div:last-child > button:first-child) {
  background: rgba(245, 158, 11, 0.12);
  color: #fbbf24;
}

:global(.dark .discovery-library-modal > div:last-child > button:last-child),
:global(.dark .discovery-post-modal > button:last-child) {
  background: #34d399;
  color: #020617;
  box-shadow: 0 18px 40px rgba(16, 185, 129, 0.18);
}

:global(.dark .discovery-news-modal .bg-blue-100) {
  background: rgba(59, 130, 246, 0.16);
  color: #93c5fd;
}

:global(.dark .discovery-news-modal .prose) {
  color: #cbd5e1;
}

:global(.dark .discovery-post-modal textarea) {
  background: #1e293b;
  border-color: #1e293b;
  color: #f8fafc;
}

:global(.dark .discovery-post-modal textarea::placeholder) {
  color: #64748b;
}

:global(.dark .custom-scrollbar::-webkit-scrollbar-thumb) {
  background: #334155;
}

:global(.dark .custom-scrollbar::-webkit-scrollbar-thumb:hover) {
  background: #475569;
}
</style>
