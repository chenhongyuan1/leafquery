<script setup>
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import NewsCard from '../../components/mobile/NewsCard.vue'
import QnACard from '../../components/mobile/QnACard.vue'
import { useFavoritesStore } from '../../stores/favorites'

const API_BASE = '/api/discovery'

const favStore = useFavoritesStore()

const categories = ['推荐资讯', '知识图鉴', '问答交流']
const activeCategory = ref(0)
const searchQuery = ref('')
const loading = ref(false)
const uploadLoading = ref(false)

const newsData = ref([])
const plantsData = ref([])
const knowledgeData = ref([])
const qnaData = ref([])

const currentUser = ref(null)
const selectedPlant = ref(null)
const selectedNewsItem = ref(null)
const selectedLibraryItem = ref(null)
const selectedQnaItem = ref(null)

const isPosting = ref(false)
const postText = ref('')
const postImages = ref([])
const fileInput = ref(null)

const getStoredUser = () => {
  try {
    const raw = localStorage.getItem('user')
    return raw ? JSON.parse(raw) : null
  } catch (error) {
    console.error('Failed to parse current user', error)
    return null
  }
}

const safeParseJSON = (value) => {
  if (!value) return []
  try {
    return JSON.parse(value)
  } catch {
    return [value]
  }
}

const formatTime = (value) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)

  const now = new Date()
  const diffMinutes = Math.floor((now - date) / 60000)
  if (diffMinutes < 1) return '刚刚'
  if (diffMinutes < 60) return `${diffMinutes} 分钟前`
  const diffHours = Math.floor(diffMinutes / 60)
  if (diffHours < 24) return `${diffHours} 小时前`
  const diffDays = Math.floor(diffHours / 24)
  if (diffDays < 30) return `${diffDays} 天前`
  return date.toLocaleDateString('zh-CN')
}

const getQnaHeadline = (item) => {
  const text = (item?.content || '').replace(/\s+/g, ' ').trim()
  if (!text) return '未命名提问'
  return text.length > 30 ? `${text.slice(0, 30)}...` : text
}

const getQnaExcerpt = (item) => {
  const text = (item?.content || '').replace(/\s+/g, ' ').trim()
  if (!text) return '发布者暂未填写问题描述。'
  return text.length > 120 ? `${text.slice(0, 120)}...` : text
}

const getConditionColor = (conditionType) => {
  switch (conditionType) {
    case '健康':
      return 'bg-emerald-500'
    case '真菌病害':
      return 'bg-orange-500'
    case '病毒病害':
      return 'bg-amber-500'
    case '细菌病害':
      return 'bg-yellow-600'
    case '缺素/生理障碍':
      return 'bg-blue-500'
    case '虫害':
      return 'bg-rose-500'
    default:
      return 'bg-slate-500'
  }
}

const fetchNews = async () => {
  try {
    const { data } = await axios.get(`${API_BASE}/news`)
    newsData.value = (data.data || []).map(item => ({
      ...item,
      type: 'news',
      id: item.newsId,
      image: item.imageUrl || item.coverUrl || '',
      date: formatTime(item.createdAt)
    }))
  } catch (error) {
    console.error('Failed to fetch news', error)
  }
}

const fetchPlants = async () => {
  try {
    const { data } = await axios.get(`${API_BASE}/plants`)
    plantsData.value = (data.data || []).map(item => ({
      ...item,
      id: `plant_${item.plantId}`,
      name: item.name,
      desc: item.description
    }))
  } catch (error) {
    console.error('Failed to fetch plants', error)
  }
}

const fetchKnowledge = async (plantId) => {
  loading.value = true
  try {
    const url = plantId ? `${API_BASE}/knowledge?plantId=${plantId}` : `${API_BASE}/knowledge`
    const { data } = await axios.get(url)
    knowledgeData.value = (data.data || []).map(item => ({
      ...item,
      type: 'library',
      id: item.knowledgeId,
      plant: item.plantName,
      title: item.title,
      image: item.imageUrl,
      desc: item.description,
      prevention: safeParseJSON(item.prevention)
    }))
  } catch (error) {
    console.error('Failed to fetch knowledge list', error)
  } finally {
    loading.value = false
  }
}

const fetchQna = async () => {
  try {
    const { data } = await axios.get(`${API_BASE}/qna`)
    qnaData.value = (data.data || []).map(item => ({
      ...item,
      type: 'qna',
      id: item.postId,
      time: formatTime(item.createdAt),
      user: item.userName,
      userAvatar: item.userAvatar || '',
      images: safeParseJSON(item.images),
      expertReply: item.expertReply
        ? { expertName: item.expertName || '专家', content: item.expertReply }
        : null,
      comments: (item.comments || []).map(comment => ({
        ...comment,
        user: comment.userName,
        userAvatar: comment.userAvatar || '',
        time: formatTime(comment.createdAt)
      }))
    }))
  } catch (error) {
    console.error('Failed to fetch qna list', error)
  }
}

const hydrateUser = async () => {
  currentUser.value = getStoredUser()
  if (currentUser.value?.userId) {
    await favStore.loadFavorites(currentUser.value.userId)
  }
}

onMounted(async () => {
  await hydrateUser()
  loading.value = true
  await Promise.all([fetchNews(), fetchPlants(), fetchQna()])
  loading.value = false
})

const currentItems = computed(() => {
  let items = []

  if (activeCategory.value === 0) {
    items = newsData.value
  } else if (activeCategory.value === 1) {
    items = selectedPlant.value ? knowledgeData.value : plantsData.value
  } else {
    items = qnaData.value
  }

  if (!searchQuery.value.trim()) return items

  const keyword = searchQuery.value.trim().toLowerCase()
  return items.filter(item => {
    const text = [
      item.title,
      item.name,
      item.desc,
      item.content,
      item.tag,
      item.user
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
    return text.includes(keyword)
  })
})

const selectPlant = async (plant) => {
  selectedPlant.value = plant
  await fetchKnowledge(plant.plantId)
}

const openPostModal = async () => {
  await hydrateUser()
  if (!currentUser.value?.userId) {
    alert('请先登录后再提问。')
    return
  }
  isPosting.value = true
}

const handleImageUpload = () => {
  if (postImages.value.length < 3) {
    fileInput.value?.click()
  }
}

const onFileSelection = async (event) => {
  const files = Array.from(event.target.files || [])
  if (!files.length) return

  if (files.length + postImages.value.length > 3) {
    alert('最多上传 3 张图片。')
    event.target.value = ''
    return
  }

  uploadLoading.value = true
  try {
    for (const file of files) {
      const formData = new FormData()
      formData.append('file', file)
      const response = await axios.post(`${API_BASE}/upload`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      if (response.data?.code === 200 && response.data.data) {
        postImages.value.push(response.data.data)
      }
    }
  } catch (error) {
    console.error('Failed to upload qna images', error)
    alert('图片上传失败，请稍后重试。')
  } finally {
    uploadLoading.value = false
    event.target.value = ''
  }
}

const submitPost = async () => {
  if (!currentUser.value?.userId || !postText.value.trim()) return

  try {
    await axios.post(`${API_BASE}/qna`, {
      userId: currentUser.value.userId,
      content: postText.value.trim(),
      images: JSON.stringify(postImages.value)
    })
    postText.value = ''
    postImages.value = []
    isPosting.value = false
    activeCategory.value = 2
    await fetchQna()
  } catch (error) {
    console.error('Failed to submit qna post', error)
    alert('提问提交失败，请稍后重试。')
  }
}

const deletePost = async (postId) => {
  if (!confirm('确定要删除这条提问吗？')) return

  try {
    const response = await axios.delete(`${API_BASE}/qna/${postId}`)
    if (response.data?.code === 200) {
      if (selectedQnaItem.value?.id === postId) {
        selectedQnaItem.value = null
      }
      await fetchQna()
    }
  } catch (error) {
    console.error('Failed to delete qna post', error)
    alert('删除失败，请稍后重试。')
  }
}
</script>

<template>
  <div class="flex h-full flex-col bg-slate-50/50 dark:bg-slate-900/50">
    <div class="shrink-0 border-b border-slate-100 bg-white px-8 pb-4 pt-8 dark:border-slate-800 dark:bg-slate-900">
      <div class="mb-6 flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
        <div>
          <h2 class="text-3xl font-black tracking-tight text-slate-800 dark:text-slate-100">知识图谱</h2>
        </div>

        <div class="flex flex-col gap-3 sm:flex-row">
          <div class="relative w-full sm:w-80">
            <input
              v-model="searchQuery"
              type="text"
              placeholder="搜索资讯、图鉴、问答..."
              class="w-full rounded-full border border-slate-200 bg-slate-50 py-2.5 pl-10 pr-4 text-sm text-slate-800 outline-none transition focus:border-emerald-300 focus:ring-2 focus:ring-emerald-400/20 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100"
            />
            <svg class="absolute left-3.5 top-3 h-5 w-5 text-slate-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          </div>

          <button
            v-if="activeCategory === 2"
            class="inline-flex items-center justify-center rounded-2xl bg-slate-900 px-5 py-2.5 text-sm font-bold text-white shadow-lg shadow-slate-900/10 transition hover:bg-black dark:bg-emerald-500 dark:text-slate-950 dark:hover:bg-emerald-400"
            @click="openPostModal"
          >
            发布提问
          </button>
        </div>
      </div>

      <div class="flex gap-2">
        <button
          v-for="(category, index) in categories"
          :key="category"
          class="rounded-xl px-5 py-2.5 text-sm font-bold transition-all"
          :class="activeCategory === index
            ? 'bg-slate-900 text-white shadow-md dark:bg-emerald-500 dark:text-slate-950'
            : 'border border-slate-200 bg-white text-slate-600 hover:bg-slate-100 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300 dark:hover:bg-slate-800'"
          @click="activeCategory = index; selectedPlant = null"
        >
          {{ category }}
        </button>
      </div>
    </div>

    <div class="custom-scrollbar relative flex-1 overflow-y-auto p-8">
      <div
        v-if="loading"
        class="absolute inset-0 z-10 flex items-center justify-center bg-white/70 text-sm font-bold text-emerald-500 backdrop-blur-sm dark:bg-slate-950/70"
      >
        正在加载数据...
      </div>

      <div v-if="activeCategory === 1 && !selectedPlant" class="grid grid-cols-2 gap-6 md:grid-cols-4 2xl:grid-cols-6">
        <button
          v-for="plant in currentItems"
          :key="plant.id"
          class="group rounded-[1.75rem] border border-slate-200 bg-white p-5 text-left shadow-sm transition hover:-translate-y-1 hover:border-emerald-300 hover:shadow-lg dark:border-slate-800 dark:bg-slate-900"
          @click="selectPlant(plant)"
        >
          <div class="mb-4 flex h-20 w-20 items-center justify-center rounded-full bg-emerald-50 text-4xl transition group-hover:scale-110 dark:bg-emerald-500/10">
            {{ plant.enName === 'corn' ? '🌽' : plant.enName === 'apple' ? '🍎' : plant.enName === 'grape' ? '🍇' : plant.enName === 'potato' ? '🥔' : '🌿' }}
          </div>
          <div class="text-lg font-black text-slate-800 dark:text-slate-100">{{ plant.name }}</div>
          <div class="mt-1 line-clamp-2 text-sm text-slate-500 dark:text-slate-400">{{ plant.desc }}</div>
        </button>
      </div>

      <div v-else-if="activeCategory === 1 && selectedPlant" class="space-y-6">
        <div class="flex items-center gap-3">
          <button
            class="inline-flex items-center rounded-2xl border border-slate-200 bg-white px-4 py-2 text-sm font-bold text-slate-600 transition hover:border-emerald-300 hover:text-emerald-600 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300 dark:hover:border-emerald-500/50 dark:hover:text-emerald-300"
            @click="selectedPlant = null"
          >
            返回作物列表
          </button>
          <div class="text-sm font-medium text-slate-500 dark:text-slate-400">
            当前作物：<span class="font-bold text-slate-700 dark:text-slate-200">{{ selectedPlant.name }}</span>
          </div>
        </div>

        <div class="grid grid-cols-1 gap-6 md:grid-cols-2 xl:grid-cols-3 2xl:grid-cols-4">
          <article
            v-for="item in currentItems"
            :key="item.id"
            class="group relative cursor-pointer overflow-hidden rounded-[1.75rem] border border-slate-100 bg-white shadow-sm transition hover:-translate-y-1 hover:shadow-xl dark:border-slate-800 dark:bg-slate-900"
            @click="selectedLibraryItem = item"
          >
            <button
              class="absolute right-3 top-3 z-10 flex h-9 w-9 items-center justify-center rounded-full bg-white/90 text-slate-500 shadow-sm backdrop-blur transition hover:scale-105 dark:bg-slate-950/90"
              @click.stop="favStore.toggleFavorite(item)"
            >
              <span class="text-base">{{ favStore.isFavorite(item) ? '★' : '☆' }}</span>
            </button>

            <div class="relative h-48 overflow-hidden bg-slate-100 dark:bg-slate-800">
              <img
                v-if="item.image"
                :src="item.image"
                :alt="item.title"
                class="h-full w-full object-cover transition duration-500 group-hover:scale-110"
              />
              <div class="absolute left-3 top-3 rounded-lg px-2 py-1 text-[11px] font-black tracking-wide text-white" :class="getConditionColor(item.conditionType)">
                {{ item.conditionType }}
              </div>
            </div>

            <div class="p-5">
              <h3 class="text-lg font-black leading-tight text-slate-800 transition group-hover:text-emerald-600 dark:text-slate-100 dark:group-hover:text-emerald-300">
                {{ item.title }}
              </h3>
              <p class="mt-3 line-clamp-2 text-sm text-slate-500 dark:text-slate-400">
                {{ item.prevention?.[0] || item.desc || '暂无图鉴摘要' }}
              </p>
            </div>
          </article>
        </div>
      </div>

      <div v-else-if="activeCategory === 2" class="grid grid-cols-1 gap-4 xl:grid-cols-2 2xl:grid-cols-3">
        <article
          v-for="item in currentItems"
          :key="item.id"
          class="pc-forum-thread group flex h-full cursor-pointer flex-col overflow-hidden rounded-[1.4rem] border border-slate-200/80 bg-white p-4 shadow-sm transition hover:-translate-y-0.5 hover:border-emerald-200 hover:shadow-lg dark:border-slate-800 dark:bg-slate-900 dark:hover:border-emerald-500/40"
          @click="selectedQnaItem = item"
        >
          <div class="flex gap-4">
            <div class="flex h-11 w-11 shrink-0 items-center justify-center overflow-hidden rounded-full bg-slate-100 text-sm font-black text-white ring-1 ring-slate-200/80 dark:bg-slate-800 dark:ring-slate-700">
              <img v-if="item.userAvatar" :src="item.userAvatar" class="h-full w-full object-cover" />
              <div v-else class="flex h-full w-full items-center justify-center bg-gradient-to-br from-emerald-400 via-sky-400 to-indigo-500">
                {{ item.user?.charAt(0)?.toUpperCase() || '?' }}
              </div>
            </div>

            <div class="flex min-w-0 flex-1 flex-col">
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0">
                  <div class="flex flex-wrap items-center gap-1.5 text-[10px] font-semibold uppercase tracking-[0.14em] text-slate-500 dark:text-slate-400">
                    <span class="pc-forum-board-pill">问答交流</span>
                    <span class="normal-case tracking-normal">{{ item.user || '匿名用户' }}</span>
                    <span class="h-1 w-1 rounded-full bg-slate-300 dark:bg-slate-600"></span>
                    <span class="normal-case tracking-normal">{{ item.time }}</span>
                  </div>

                  <h3 class="mt-2.5 text-lg font-black leading-tight text-slate-900 dark:text-slate-100">
                    {{ getQnaHeadline(item) }}
                  </h3>
                </div>

                <div class="flex shrink-0 items-center gap-1.5">
                  <button
                    class="flex h-8 w-8 items-center justify-center rounded-full bg-slate-100 text-slate-500 transition hover:scale-105 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700"
                    @click.stop="favStore.toggleFavorite(item)"
                  >
                    <span class="text-base">{{ favStore.isFavorite(item) ? '★' : '☆' }}</span>
                  </button>
                  <button
                    v-if="item.userId === currentUser?.userId"
                    class="flex h-8 w-8 items-center justify-center rounded-full bg-red-50 text-red-400 transition hover:bg-red-100 hover:text-red-500 dark:bg-red-500/10 dark:text-red-300 dark:hover:bg-red-500/20"
                    @click.stop="deletePost(item.id)"
                  >
                    🗑️
                  </button>
                </div>
              </div>

              <p class="mt-3 line-clamp-3 text-[13px] leading-6 text-slate-600 dark:text-slate-300">
                {{ getQnaExcerpt(item) }}
              </p>

              <div v-if="item.images?.length" class="mt-3 flex gap-2 overflow-hidden">
                <div
                  v-for="(image, index) in item.images.slice(0, 3)"
                  :key="`${item.id}_${index}`"
                  class="h-16 w-16 overflow-hidden rounded-xl bg-slate-100 ring-1 ring-slate-200/80 dark:bg-slate-800 dark:ring-slate-700"
                >
                  <img :src="image" class="h-full w-full object-cover" />
                </div>
              </div>

              <div class="mt-3.5 flex flex-wrap gap-1.5">
                <span class="pc-forum-chip" :class="item.expertReply ? 'pc-forum-chip--reply' : 'pc-forum-chip--wait'">
                  {{ item.expertReply ? '专家已回复' : '等待专家回复' }}
                </span>
                <span v-if="item.images?.length" class="pc-forum-chip">
                  {{ item.images.length }} 张附图
                </span>
                <span class="pc-forum-chip">
                  {{ item.comments.length }} 条评论
                </span>
                <span class="pc-forum-chip">
                  {{ item.likes || 0 }} 次点赞
                </span>
              </div>

              <div class="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-3 text-[13px] text-slate-500 dark:border-slate-800 dark:text-slate-400">
                <div class="flex flex-wrap items-center gap-3">
                  <span>{{ item.expertReply ? '帖子有新回复' : '帖子仍在讨论中' }}</span>
                  <span v-if="item.comments.length > 0">最近互动 {{ item.comments[0]?.time }}</span>
                </div>
                <div class="flex items-center gap-3 font-semibold">
                  <span>💬 {{ item.comments.length }}</span>
                  <span>👍 {{ item.likes || 0 }}</span>
                  <span class="text-slate-700 dark:text-slate-200">查看详情 →</span>
                </div>
              </div>
            </div>
          </div>
        </article>
      </div>

      <div v-else class="grid grid-cols-1 gap-6 xl:grid-cols-2 2xl:grid-cols-3">
        <article
          v-for="item in currentItems"
          :key="item.id"
          class="group relative"
        >
          <button
            class="absolute right-3 top-3 z-10 flex h-9 w-9 items-center justify-center rounded-full bg-white/90 text-slate-500 shadow-sm backdrop-blur transition hover:scale-105 dark:bg-slate-950/90"
            @click.stop="favStore.toggleFavorite(item)"
          >
            <span class="text-base">{{ favStore.isFavorite(item) ? '★' : '☆' }}</span>
          </button>

          <div class="cursor-pointer transition hover:-translate-y-1 hover:shadow-lg" @click="selectedNewsItem = item">
            <NewsCard v-bind="item" />
          </div>
        </article>
      </div>

      <div v-if="!loading && currentItems.length === 0" class="py-24 text-center">
        <div class="text-5xl text-slate-300">📭</div>
        <div class="mt-4 text-lg font-bold text-slate-500 dark:text-slate-400">没有匹配的内容</div>
      </div>
    </div>

    <div v-if="selectedLibraryItem" class="fixed inset-0 z-50 flex items-center justify-center p-8">
      <div class="absolute inset-0 bg-slate-900/60 backdrop-blur-sm" @click="selectedLibraryItem = null"></div>
      <div class="relative z-10 flex max-h-[88vh] w-full max-w-4xl flex-col overflow-hidden rounded-[2rem] bg-white shadow-2xl dark:bg-slate-900">
        <div class="relative h-72 overflow-hidden bg-slate-100 dark:bg-slate-800">
          <img v-if="selectedLibraryItem.image" :src="selectedLibraryItem.image" :alt="selectedLibraryItem.title" class="h-full w-full object-cover" />
          <button class="absolute right-4 top-4 flex h-10 w-10 items-center justify-center rounded-full bg-black/40 text-white backdrop-blur" @click="selectedLibraryItem = null">
            ×
          </button>
          <div class="absolute bottom-0 inset-x-0 h-40 bg-gradient-to-t from-black/80 to-transparent"></div>
          <div class="absolute bottom-8 left-8">
            <div class="mb-3 flex gap-2">
              <span class="rounded-lg px-3 py-1 text-xs font-black text-white" :class="getConditionColor(selectedLibraryItem.conditionType)">
                {{ selectedLibraryItem.conditionType }}
              </span>
              <span class="rounded-lg bg-black/50 px-3 py-1 text-xs font-bold text-white backdrop-blur">
                {{ selectedLibraryItem.plant || '图鉴' }}
              </span>
            </div>
            <h3 class="text-3xl font-black text-white">{{ selectedLibraryItem.title }}</h3>
          </div>
        </div>

        <div class="custom-scrollbar flex-1 overflow-y-auto p-8">
          <div class="space-y-8">
            <section>
              <h4 class="mb-3 text-lg font-bold text-slate-800 dark:text-slate-100">症状特征</h4>
              <p class="leading-relaxed text-slate-600 dark:text-slate-300">{{ selectedLibraryItem.desc || '暂无详细描述。' }}</p>
            </section>

            <section>
              <h4 class="mb-3 text-lg font-bold text-slate-800 dark:text-slate-100">防治建议</h4>
              <div class="space-y-3">
                <div
                  v-for="(method, index) in (selectedLibraryItem.prevention?.length ? selectedLibraryItem.prevention : ['暂无防治建议'])"
                  :key="index"
                  class="rounded-2xl border border-slate-100 bg-slate-50 px-4 py-3 text-sm text-slate-700 dark:border-slate-800 dark:bg-slate-950/50 dark:text-slate-300"
                >
                  {{ method }}
                </div>
              </div>
            </section>
          </div>
        </div>

        <div class="flex items-center justify-between border-t border-slate-100 px-8 py-5 dark:border-slate-800">
          <button
            class="rounded-2xl px-5 py-3 text-sm font-bold transition"
            :class="favStore.isFavorite(selectedLibraryItem)
              ? 'bg-amber-50 text-amber-500 dark:bg-amber-500/10'
              : 'bg-slate-100 text-slate-600 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-200 dark:hover:bg-slate-700'"
            @click="favStore.toggleFavorite(selectedLibraryItem)"
          >
            {{ favStore.isFavorite(selectedLibraryItem) ? '已收藏图鉴' : '收藏图鉴' }}
          </button>
        </div>
      </div>
    </div>

    <div v-if="selectedNewsItem" class="fixed inset-0 z-50 flex items-center justify-center p-8">
      <div class="absolute inset-0 bg-slate-900/60 backdrop-blur-sm" @click="selectedNewsItem = null"></div>
      <div class="relative z-10 flex max-h-[88vh] w-full max-w-3xl flex-col overflow-hidden rounded-[2rem] bg-white shadow-2xl dark:bg-slate-900">
        <div class="flex items-center justify-between border-b border-slate-100 px-6 py-4 dark:border-slate-800">
          <button class="flex h-9 w-9 items-center justify-center rounded-full bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-200" @click="selectedNewsItem = null">
            ×
          </button>
          <div class="font-bold text-slate-800 dark:text-slate-100">资讯详情</div>
          <button class="flex h-9 w-9 items-center justify-center rounded-full bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-200" @click="favStore.toggleFavorite(selectedNewsItem)">
            {{ favStore.isFavorite(selectedNewsItem) ? '★' : '☆' }}
          </button>
        </div>

        <div class="custom-scrollbar flex-1 overflow-y-auto px-8 py-8">
          <div class="mx-auto max-w-2xl">
            <div class="mb-6 text-center">
              <div class="mb-4 inline-flex rounded-full bg-blue-100 px-3 py-1 text-xs font-bold text-blue-600 dark:bg-blue-500/10 dark:text-blue-300">
                {{ selectedNewsItem.tag || '资讯' }}
              </div>
              <h3 class="text-3xl font-black leading-tight text-slate-900 dark:text-slate-100">{{ selectedNewsItem.title }}</h3>
              <div class="mt-4 flex items-center justify-center gap-3 text-xs font-medium text-slate-400">
                <span>{{ selectedNewsItem.date }}</span>
                <span>·</span>
                <span>{{ selectedNewsItem.views || 0 }} 阅读</span>
              </div>
            </div>

            <div class="prose prose-slate max-w-none whitespace-pre-wrap text-slate-700 dark:prose-invert dark:text-slate-300">
              {{ selectedNewsItem.content || '暂无正文内容。' }}
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="isPosting" class="fixed inset-0 z-50 flex items-center justify-center p-8">
      <div class="absolute inset-0 bg-slate-900/60 backdrop-blur-sm" @click="isPosting = false"></div>
      <div class="pc-knowledge-dialog relative z-10 w-full max-w-2xl rounded-[2rem] bg-white p-8 shadow-2xl dark:bg-slate-900">
        <div class="mb-6 flex items-center justify-between">
          <div>
            <h3 class="text-2xl font-black text-slate-800 dark:text-slate-100">发布提问</h3>
            <p class="mt-1 text-sm text-slate-500 dark:text-slate-400">走 `/api/discovery/qna` 与 `/api/discovery/upload`。</p>
          </div>
          <button class="text-2xl text-slate-400" @click="isPosting = false">×</button>
        </div>

        <textarea
          v-model="postText"
          class="h-36 w-full rounded-[1.5rem] border border-slate-200 bg-slate-50 p-4 text-sm text-slate-800 outline-none transition focus:border-emerald-300 focus:ring-2 focus:ring-emerald-400/20 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100"
          placeholder="详细描述病害症状、发生时间、处理措施等..."
        ></textarea>

        <div class="mt-5">
          <div class="mb-3 text-sm font-bold text-slate-700 dark:text-slate-200">图片附件（{{ postImages.length }}/3）</div>
          <div class="flex flex-wrap gap-4">
            <div
              v-for="(image, index) in postImages"
              :key="image"
              class="relative h-24 w-24 overflow-hidden rounded-2xl border border-slate-200 bg-slate-100 dark:border-slate-700 dark:bg-slate-800"
            >
              <img :src="image" class="h-full w-full object-cover" />
              <button class="absolute right-1 top-1 flex h-6 w-6 items-center justify-center rounded-full bg-black/60 text-xs text-white" @click="postImages.splice(index, 1)">
                ×
              </button>
            </div>

            <button
              v-if="postImages.length < 3"
              class="flex h-24 w-24 items-center justify-center rounded-2xl border-2 border-dashed border-slate-300 text-sm font-bold text-slate-400 transition hover:border-emerald-300 hover:text-emerald-500 dark:border-slate-700"
              @click="handleImageUpload"
            >
              {{ uploadLoading ? '上传中' : '添加图片' }}
            </button>
          </div>
          <input ref="fileInput" type="file" multiple accept="image/*" class="hidden" @change="onFileSelection" />
        </div>

        <div class="mt-8 flex justify-end">
          <button
            class="rounded-2xl bg-slate-900 px-6 py-3 text-sm font-bold text-white shadow-lg shadow-slate-900/10 transition hover:bg-black disabled:cursor-not-allowed disabled:bg-slate-300 dark:bg-emerald-500 dark:text-slate-950 dark:hover:bg-emerald-400"
            :disabled="!postText.trim() || uploadLoading"
            @click="submitPost"
          >
            提交提问
          </button>
        </div>
      </div>
    </div>

    <div v-if="selectedQnaItem" class="fixed inset-0 z-50 flex items-center justify-center p-8">
      <div class="absolute inset-0 bg-slate-900/60 backdrop-blur-sm" @click="selectedQnaItem = null"></div>
      <div class="pc-knowledge-dialog relative z-10 flex max-h-[88vh] w-full max-w-3xl flex-col overflow-hidden rounded-[2rem] bg-slate-50 shadow-2xl dark:bg-slate-950">
        <div class="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-4 dark:border-slate-800 dark:bg-slate-900">
          <button class="flex h-9 w-9 items-center justify-center rounded-full bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-200" @click="selectedQnaItem = null">
            ×
          </button>
          <div class="font-bold text-slate-800 dark:text-slate-100">问答详情</div>
          <div class="w-9"></div>
        </div>

        <div class="custom-scrollbar flex-1 overflow-y-auto p-6">
          <QnACard
            v-bind="selectedQnaItem"
            :currentUserId="currentUser?.userId"
            class="mb-0 border-none shadow-none"
            @delete="deletePost(selectedQnaItem.id)"
            @refresh="fetchQna"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.pc-forum-thread {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.98));
  border-color: rgba(226, 232, 240, 0.88);
  box-shadow: 0 14px 34px rgba(15, 23, 42, 0.07);
}

:global(.dark .pc-forum-thread) {
  background:
    linear-gradient(180deg, rgba(15, 23, 42, 0.96), rgba(2, 6, 23, 0.98)) !important;
  border-color: rgba(51, 65, 85, 0.88) !important;
  box-shadow: 0 18px 40px rgba(2, 6, 23, 0.28) !important;
}

.pc-knowledge-dialog {
  background: rgba(255, 255, 255, 0.98);
  border: 1px solid rgba(226, 232, 240, 0.92);
}

:global(.dark .pc-knowledge-dialog) {
  background: rgba(2, 6, 23, 0.96) !important;
  border-color: rgba(51, 65, 85, 0.88) !important;
}

.pc-forum-board-pill {
  display: inline-flex;
  align-items: center;
  border-radius: 9999px;
  padding: 0.3rem 0.66rem;
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.14), rgba(14, 165, 233, 0.14));
  color: #15803d;
}

:global(.dark .pc-forum-board-pill) {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.22), rgba(14, 165, 233, 0.2));
  color: #a7f3d0;
}

.pc-forum-chip {
  display: inline-flex;
  align-items: center;
  border-radius: 9999px;
  border: 1px solid rgba(226, 232, 240, 0.95);
  background: rgba(248, 250, 252, 0.92);
  padding: 0.34rem 0.68rem;
  font-size: 0.68rem;
  font-weight: 700;
  color: #64748b;
}

:global(.dark .pc-forum-chip) {
  border-color: rgba(51, 65, 85, 0.9);
  background: rgba(15, 23, 42, 0.88);
  color: #94a3b8;
}

.pc-forum-chip--reply {
  border-color: rgba(34, 197, 94, 0.18);
  background: rgba(34, 197, 94, 0.1);
  color: #15803d;
}

:global(.dark .pc-forum-chip--reply) {
  border-color: rgba(34, 197, 94, 0.24);
  background: rgba(34, 197, 94, 0.12);
  color: #86efac;
}

.pc-forum-chip--wait {
  border-color: rgba(245, 158, 11, 0.2);
  background: rgba(245, 158, 11, 0.1);
  color: #b45309;
}

:global(.dark .pc-forum-chip--wait) {
  border-color: rgba(251, 191, 36, 0.22);
  background: rgba(245, 158, 11, 0.12);
  color: #fcd34d;
}

.custom-scrollbar::-webkit-scrollbar {
  width: 6px;
}

.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 9999px;
}

.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

:global(.dark .custom-scrollbar::-webkit-scrollbar-thumb) {
  background: #334155;
}

:global(.dark .custom-scrollbar::-webkit-scrollbar-thumb:hover) {
  background: #475569;
}
</style>
