<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'
import NewsCard from '../../components/mobile/NewsCard.vue'
import QnACard from '../../components/mobile/QnACard.vue'
import { useFavoritesStore } from '../../stores/favorites'

const favStore = useFavoritesStore()
const API_BASE = '/api/discovery'

const categories = ['推荐资讯', '知识图鉴', '问答圈']
const activeCategory = ref(0)
const searchQuery = ref('')
const loading = ref(false)

const newsData = ref([])
const plantsData = ref([])
const knowledgeData = ref([])
const qnaData = ref([])

const selectedPlant = ref(null)
const selectedNewsItem = ref(null)
const selectedLibraryItem = ref(null)
const selectedQnaItem = ref(null)

onMounted(async () => {
  loading.value = true
  await Promise.all([fetchNews(), fetchPlants(), fetchQna()])
  loading.value = false
})

const fetchNews = async () => {
  try {
    const { data } = await axios.get(`${API_BASE}/news`)
    newsData.value = (data.data || []).map(item => ({ ...item, type:'news', id: item.newsId, date: formatTime(item.createdAt) }))
  } catch(e) {}
}
const fetchPlants = async () => {
  try {
    const { data } = await axios.get(`${API_BASE}/plants`)
    plantsData.value = (data.data || []).map(item => ({ ...item, id: 'p'+item.plantId, name: item.name, desc: item.description }))
  } catch(e) {}
}
const fetchKnowledge = async (plantId) => {
  loading.value = true
  try {
    const url = plantId ? `${API_BASE}/knowledge?plantId=${plantId}` : `${API_BASE}/knowledge`
    const { data } = await axios.get(url)
    knowledgeData.value = (data.data || []).map(item => ({
      ...item, type: 'library', id: item.knowledgeId, title: item.title, image: item.imageUrl, prevention: safeParseJSON(item.prevention)
    }))
    selectedPlant.value = plantId
  } catch(e) {}
  loading.value = false
}
const fetchQna = async () => {
  try {
    const { data } = await axios.get(`${API_BASE}/qna`)
    qnaData.value = (data.data || []).map(item => ({
      ...item, type:'qna', id: item.postId, user: item.userName, time: formatTime(item.createdAt), images: safeParseJSON(item.images), expertReply: item.expertReply ? { expertName: item.expertName, content: item.expertReply } : null
    }))
  } catch(e) {}
}

const safeParseJSON = (str) => { if (!str) return []; try{ return JSON.parse(str) }catch{ return [str] } }
const formatTime = (ts) => {
  if (!ts) return ''
  const d = new Date(ts)
  return `${d.getFullYear()}-${(d.getMonth()+1).toString().padStart(2,'0')}-${d.getDate().toString().padStart(2,'0')}`
}

const currentItems = computed(() => {
  let items = []
  if (activeCategory.value === 0) items = newsData.value
  else if (activeCategory.value === 1) items = selectedPlant.value ? knowledgeData.value : plantsData.value
  else if (activeCategory.value === 2) items = qnaData.value

  if (searchQuery.value) {
    const q = searchQuery.value.toLowerCase()
    items = items.filter(i => (i.title||i.name||i.content||'').toLowerCase().includes(q))
  }
  return items
})

const getConditionColor = (type) => {
  switch(type) {
    case '健康': return 'bg-emerald-500'
    case '真菌病害': return 'bg-orange-500'
    case '病毒病害': return 'bg-amber-500'
    case '虫害': return 'bg-rose-500'
    default: return 'bg-slate-500'
  }
}
</script>

<template>
  <div class="h-full flex flex-col bg-slate-50/50 dark:bg-slate-900/50">
    <!-- Header Area -->
    <div class="px-8 pt-8 pb-4 shrink-0 border-b border-slate-100 dark:border-slate-800 bg-white dark:bg-slate-900">
      <div class="flex justify-between items-end mb-6">
        <div>
          <h2 class="text-3xl font-black text-slate-800 dark:text-slate-100 tracking-tight">知识图谱</h2>
          <p class="text-slate-500 dark:text-slate-400 mt-1 font-medium">农业病虫害资讯与系统化防御指南</p>
        </div>
        <div class="relative w-80">
          <input v-model="searchQuery" type="text" placeholder="搜索资讯、病害、问答..." 
                 class="w-full pl-10 pr-4 py-2.5 bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-800 dark:text-slate-100 rounded-full focus:ring-2 focus:ring-green-400 focus:outline-none shadow-inner" />
          <svg class="w-5 h-5 absolute left-3.5 top-3 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"/></svg>
        </div>
      </div>
      
      <!-- Category Tabs -->
      <div class="flex space-x-2">
        <button v-for="(cat, idx) in categories" :key="cat" @click="activeCategory = idx; selectedPlant = null;"
                class="px-5 py-2.5 rounded-xl font-bold transition-all"
                :class="activeCategory === idx ? 'bg-slate-900 text-white shadow-md' : 'bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:bg-slate-800 border border-slate-200 dark:border-slate-700'">
          {{ cat }}
        </button>
      </div>
    </div>

    <!-- Main Content Area -->
    <div class="flex-1 overflow-y-auto p-8 custom-scrollbar relative">
      <div v-if="loading" class="absolute inset-0 flex items-center justify-center bg-white dark:bg-slate-900/50 backdrop-blur-sm z-10 text-green-500 font-bold">
         数据加载中...
      </div>
      
      <!-- Plants Grid (Knowledge Root) -->
      <div v-if="activeCategory === 1 && !selectedPlant" class="grid grid-cols-2 md:grid-cols-4 xl:grid-cols-6 gap-6">
         <div v-for="plant in currentItems" :key="plant.id" @click="fetchKnowledge(plant.plantId)"
              class="bg-white dark:bg-slate-900 rounded-2xl border border-slate-200 dark:border-slate-700 shadow-sm hover:shadow-xl hover:border-green-300 transition-all p-5 text-center cursor-pointer group">
            <div class="w-20 h-20 mx-auto bg-green-50 rounded-full flex items-center justify-center text-4xl mb-4 group-hover:scale-110 transition-transform">
               {{ plant.enName === 'corn' ? '🌽' : plant.enName === 'apple' ? '🍎' : plant.enName === 'grape' ? '🍇' : plant.enName === 'potato' ? '🥔' : '🪴' }}
            </div>
            <h3 class="font-bold text-slate-800 dark:text-slate-100 text-lg">{{ plant.name }}</h3>
            <p class="text-xs text-slate-400 mt-1 line-clamp-1 truncate">{{ plant.desc }}</p>
         </div>
      </div>

      <!-- Knowledge Lib Items -->
      <div v-else-if="activeCategory === 1 && selectedPlant" class="relative">
         <button @click="selectedPlant = null" class="mb-4 px-4 py-2 bg-slate-100 dark:bg-slate-800 hover:bg-slate-200 dark:bg-slate-700 rounded-lg text-slate-600 dark:text-slate-300 font-bold text-sm tracking-wide flex items-center"><span class="mr-2">←</span> 返回作物列表</button>
         <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
            <div v-for="item in currentItems" :key="item.id" @click="selectedLibraryItem = item"
                 class="bg-white dark:bg-slate-900 rounded-[1.5rem] border border-slate-100 dark:border-slate-800 shadow-sm hover:shadow-xl transition-all overflow-hidden cursor-pointer group flex flex-col">
               <div class="h-48 bg-slate-100 dark:bg-slate-800 overflow-hidden relative">
                 <img v-if="item.image" :src="item.image" class="w-full h-full object-cover group-hover:scale-110 transition-transform duration-500" />
                 <div class="absolute top-3 right-3 text-[10px] font-black tracking-widest px-2 py-1 rounded-md text-white shadow-sm" :class="getConditionColor(item.conditionType)">{{ item.conditionType }}</div>
               </div>
               <div class="p-5 flex-1 flex flex-col">
                 <h4 class="font-bold text-slate-800 dark:text-slate-100 text-lg mb-2 leading-tight group-hover:text-green-600 transition-colors">{{ item.title }}</h4>
                 <p class="text-sm text-slate-500 dark:text-slate-400 line-clamp-2 mt-auto">{{ item.prevention?.[0] || '暂无防御指南' }}</p>
               </div>
            </div>
         </div>
      </div>

      <!-- News & QnA Masonry (Columns) -->
      <div v-else class="columns-1 md:columns-2 lg:columns-3 xl:columns-4 gap-6 space-y-6">
         <!-- Force break inside avoid -->
         <div v-for="item in currentItems" :key="item.id" class="break-inside-avoid">
             <!-- Using the existing mobile components but they automatically adapt to the column width -->
             <NewsCard v-if="activeCategory === 0" :item="item" @click="selectedNewsItem = item" class="mb-6 hover:shadow-xl transition-shadow cursor-pointer border border-slate-100 dark:border-slate-800" />
             <QnACard v-else-if="activeCategory === 2" :item="item" @click="selectedQnaItem = item" class="mb-6 hover:shadow-xl transition-shadow cursor-pointer border border-slate-100 dark:border-slate-800" />
         </div>
      </div>

      <div v-if="currentItems.length === 0 && !loading" class="py-20 text-center text-slate-400 font-medium">
         没有找到相关内容
      </div>
    </div>

    <!-- ================= MODALS ================= -->
    <!-- Library Detail Modal -->
    <div v-if="selectedLibraryItem" class="fixed inset-0 z-50 flex items-center justify-center p-8">
       <div class="absolute inset-0 bg-slate-900/60 backdrop-blur-sm" @click="selectedLibraryItem = null"></div>
       <div class="bg-white dark:bg-slate-900 w-full max-w-4xl rounded-[2rem] shadow-2xl relative z-10 flex flex-col max-h-full overflow-hidden animate-fade-in-up">
          <div class="flex h-full min-h-0">
             <!-- Left Image -->
             <div class="w-2/5 shrink-0 bg-slate-100 dark:bg-slate-800 relative">
               <img v-if="selectedLibraryItem.image" :src="selectedLibraryItem.image" class="w-full h-full object-cover" />
               <div class="absolute top-4 left-4 text-xs font-black tracking-widest px-3 py-1.5 rounded-lg text-white shadow-sm" :class="getConditionColor(selectedLibraryItem.conditionType)">{{ selectedLibraryItem.conditionType }}</div>
             </div>
             <!-- Right Content -->
             <div class="flex-1 flex flex-col min-h-0">
                <div class="p-8 border-b border-slate-100 dark:border-slate-800 flex items-start justify-between">
                   <div>
                      <h2 class="text-3xl font-black text-slate-900 dark:text-slate-100 mb-2">{{ selectedLibraryItem.title }}</h2>
                      <div class="text-green-600 dark:text-green-400 font-bold bg-green-50 dark:bg-green-500/10 inline-block px-3 py-1 rounded-lg">{{ selectedLibraryItem.plant }}</div>
                   </div>
                   <button @click="selectedLibraryItem = null" class="w-10 h-10 bg-slate-100 dark:bg-slate-800 hover:bg-slate-200 dark:bg-slate-700 rounded-full flex items-center justify-center text-slate-500 dark:text-slate-400 font-bold transition-colors shrink-0">✕</button>
                </div>
                <div class="p-8 overflow-y-auto flex-1 custom-scrollbar space-y-8">
                   <div v-if="selectedLibraryItem.desc">
                      <h3 class="text-lg font-bold text-slate-800 dark:text-slate-100 mb-3 border-l-4 border-slate-400 pl-3">症状特征</h3>
                      <p class="text-slate-600 dark:text-slate-300 leading-relaxed">{{ selectedLibraryItem.desc }}</p>
                   </div>
                   <div v-if="selectedLibraryItem.prevention && selectedLibraryItem.prevention.length">
                      <h3 class="text-lg font-bold text-slate-800 dark:text-slate-100 mb-3 border-l-4 border-green-500 pl-3">防治建议</h3>
                      <ul class="space-y-3">
                         <li v-for="(p, i) in selectedLibraryItem.prevention" :key="i" class="flex items-start">
                            <span class="w-6 h-6 rounded bg-green-100 dark:bg-green-500/20 text-green-600 dark:text-green-400 flex items-center justify-center shrink-0 mr-3 font-bold text-xs mt-0.5">{{i+1}}</span>
                            <span class="text-slate-600 dark:text-slate-300 leading-relaxed">{{ p }}</span>
                         </li>
                      </ul>
                   </div>
                </div>
             </div>
          </div>
       </div>
    </div>
  </div>
</template>

<style scoped>
.custom-scrollbar::-webkit-scrollbar { width: 6px; }
.custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
.custom-scrollbar::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }
.custom-scrollbar::-webkit-scrollbar-thumb:hover { background: #94a3b8; }

@keyframes fadeInUp { from { opacity: 0; transform: translateY(20px); } to { opacity: 1; transform: translateY(0); } }
.animate-fade-in-up { animation: fadeInUp 0.3s cubic-bezier(0.16, 1, 0.3, 1) forwards; }
</style>
