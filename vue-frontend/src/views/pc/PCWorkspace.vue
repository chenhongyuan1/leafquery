<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import axios from 'axios'
import { useFarmStore } from '../../stores/farmCloud'
import {
  formatConfidencePercent,
  getPrimaryDisplayName,
  getSceneMeta,
  normalizeClassNames,
  normalizeDetectionSummary
} from '../../utils/detectionPresentation'

// ===== 类别选择 =====
const categoryOptions = [
  { key: 'rice',   label: '水稻', value: '水稻', icon: '🌾', group: 'disease' },
  { key: 'corn',   label: '玉米', value: '玉米', icon: '🌽', group: 'disease' },
  { key: 'wheat',  label: '小麦', value: '小麦', icon: '🌿', group: 'disease' },
  { key: 'other',  label: '其他', value: '其他', icon: '🍃', group: 'disease' },
  { key: 'pest',   label: '虫害', value: '虫害', icon: '🐛', group: 'pest'    },
]
const selectedCategories = ref([])
const toggleCategory = (value) => {
  const idx = selectedCategories.value.indexOf(value)
  if (idx >= 0) {
    selectedCategories.value.splice(idx, 1)
  } else {
    selectedCategories.value.push(value)
  }
}
const hasSelection = computed(() => selectedCategories.value.length > 0)

// ===== State =====
const isDragging = ref(false)
const isScanning = ref(false)
const errorMsg = ref('')
const previewUrl = ref(null)
const selectionConfirmed = ref(false)
const confirmSelection = () => {
  if (hasSelection.value) {
    selectionConfirmed.value = true
  }
}
const fileInput = ref(null)

const isResultReady = ref(false)
const detectionResult = ref(null)
const historyItems = ref(JSON.parse(localStorage.getItem('leafquery_history') || '[]'))
const yoloUsed = ref(false)
const reviewRequired = ref(false)

// ==== 动画阶段 ====
const analysisStage = ref('')  // '' → 'yolo' → 'review' → 'done'

// ==== Chat State ====
const messages = ref([])
const userInput = ref('')
const isSending = ref(false)
const chatContainer = ref(null)
const farmStore = useFarmStore()

// ====== Chat Image logic ======
const imageInputRef = ref(null)
const pendingImage = ref('')
const pendingImageBase64 = ref('')

const triggerImageUpload = () => {
  imageInputRef.value?.click()
}

const onImageSelected = (event) => {
  const file = event.target.files?.[0]
  if (file) {
    if (file.size > 10 * 1024 * 1024) {
      errorMsg.value = '图片大小不能超过10MB'
      return
    }
    const reader = new FileReader()
    reader.onload = (e) => {
      pendingImage.value = URL.createObjectURL(file)
      pendingImageBase64.value = e.target.result.split(',')[1]
    }
    reader.readAsDataURL(file)
  }
}

const removePendingImage = () => {
  pendingImage.value = ''
  pendingImageBase64.value = ''
  if (imageInputRef.value) {
    imageInputRef.value.value = ''
  }
}

const sceneBadgeClassMap = {
  single: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300',
  multi: 'bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300',
  uncertain: 'bg-orange-100 text-orange-700 dark:bg-orange-500/15 dark:text-orange-200',
  empty: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300'
}

const summaryItems = computed(() => normalizeDetectionSummary(detectionResult.value?.detectedSummary || []))
const detectedClassNames = computed(() => normalizeClassNames(
  detectionResult.value?.classNamesZh || [],
  detectionResult.value?.detectedSummary || []
))
const sceneMeta = computed(() => getSceneMeta(detectionResult.value?.sceneType))
const sceneBadgeClasses = computed(() => sceneBadgeClassMap[detectionResult.value?.sceneType] || sceneBadgeClassMap.single)
const primaryDisplayName = computed(() => getPrimaryDisplayName(detectionResult.value || {}))
const diagnosisContextName = computed(() => {
  if (!detectionResult.value) return ''
  if (detectionResult.value.sceneType === 'multi') {
    return detectedClassNames.value.join('、') || primaryDisplayName.value
  }
  return primaryDisplayName.value
})
const formatConfidence = (value, digits = 1) => formatConfidencePercent(value, digits)

onMounted(async () => {
  await farmStore.initialize()
  fetchHistory()
})

const fetchHistory = async () => {
  try {
    const userStr = localStorage.getItem('user')
    if (userStr) {
      const user = JSON.parse(userStr)
      if (user && user.userId) {
        const response = await axios.get(`/api/record/list?userId=${user.userId}`)
        if (response.data?.code === 200) {
          farmStore.syncIdentificationHistory(response.data.data || [])
          historyItems.value = response.data.data.map(item => ({
            id: item.id,
            name: item.pestName,
            confidence: item.confidence,
            time: item.createTime,
            imageUrl: item.imageUrl,
            locationId: item.locationId,
            city: item.city,
            region: item.region
          }))
          localStorage.setItem('leafquery_history', JSON.stringify(historyItems.value))
        }
      }
    }
  } catch (e) {
    console.error('Failed to load history', e)
  }
}

const getCurrentUser = () => {
  try {
    const userStr = localStorage.getItem('user')
    return userStr ? JSON.parse(userStr) : null
  } catch (error) {
    console.error('Failed to parse current user', error)
    return null
  }
}

const getLocationId = () => farmStore.activeCrop?.locationId || '101010100'

const persistRecognitionRecord = async (result) => {
  const newRecord = {
    id: Date.now(),
    name: result.pestName || primaryDisplayName.value,
    confidence: result.confidence || result.primaryConfidence || 0,
    time: Date.now(),
    imageUrl: result.imageUrl
  }
  historyItems.value.unshift(newRecord)
  if (historyItems.value.length > 100) historyItems.value.pop()
  localStorage.setItem('leafquery_history', JSON.stringify(historyItems.value))

  const user = getCurrentUser()
  if (!user?.userId) return

  await axios.post('/api/record/add', {
    userId: user.userId,
    pestName: result.pestName || primaryDisplayName.value,
    confidence: result.confidence || result.primaryConfidence || 0,
    locationId: getLocationId(),
    imageUrl: result.imageUrl
  })
}

// ===== Drag & Drop Logic =====
const triggerUpload = () => {
  if (!hasSelection.value) {
    errorMsg.value = '请先选择至少一个检测类别'
    return
  }
  fileInput.value?.click()
}

const onDragOver = (e) => {
  e.preventDefault()
  isDragging.value = true
}
const onDragLeave = (e) => {
  e.preventDefault()
  isDragging.value = false
}
const onDrop = (e) => {
  e.preventDefault()
  isDragging.value = false
  if (!hasSelection.value) {
    errorMsg.value = '请先选择至少一个检测类别'
    return
  }
  const file = e.dataTransfer?.files?.[0]
  if (file && file.type.startsWith('image/')) {
    processFile(file)
  } else {
    errorMsg.value = '请上传图片文件'
  }
}
const onFileSelected = (e) => {
  const file = e.target.files?.[0]
  if (file) processFile(file)
}

const processFile = async (file) => {
  previewUrl.value = URL.createObjectURL(file)
  isScanning.value = true
  errorMsg.value = ''
  isResultReady.value = false
  analysisStage.value = 'yolo'

  try {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('categories', selectedCategories.value.join(','))
    formData.append('locationId', getLocationId())

    const response = await axios.post('/api/pest/identify', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000  // 包含 Dify 调用，可能较慢
    })

    if (response.data.report_error) {
      console.warn('Diagnostic report generation failed:', response.data.report_error)
    }

    const predictionRaw = response.data.prediction || {}
    const savedImageUrl = response.data.image_url || previewUrl.value
    yoloUsed.value = response.data.yolo_used !== false
    reviewRequired.value = response.data.review_required === true
    const reportError = response.data.report_error || ''
    const reviewResult = response.data.review_result || ''

    let finalPestName = predictionRaw.pest_name || predictionRaw.pestName ||
                        predictionRaw.primary_target_zh || predictionRaw.primaryTargetZh || '未识别'
    if (String(finalPestName).toLowerCase() === 'unknown' || finalPestName === '未识别') {
      finalPestName = '未能识别出具体病虫害'
    }

    detectionResult.value = {
      pestName: finalPestName,
      confidence: predictionRaw.confidence || predictionRaw.primary_confidence || predictionRaw.primaryConfidence || 0,
      primaryTargetZh: predictionRaw.primary_target_zh || predictionRaw.primaryTargetZh || finalPestName,
      primaryConfidence: predictionRaw.primary_confidence || predictionRaw.primaryConfidence || predictionRaw.confidence || 0,
      sceneType: predictionRaw.scene_type || predictionRaw.sceneType || (yoloUsed.value ? 'single' : 'empty'),
      classNamesZh: predictionRaw.class_names_zh || predictionRaw.classNamesZh || [],
      detectedSummary: predictionRaw.detected_summary || predictionRaw.detectedSummary || [],
      report: '',  // 阶段 3 按需生成
      reviewResult: reviewResult,
      imageUrl: savedImageUrl,
      predictionJson: JSON.stringify(predictionRaw),
      userCategories: selectedCategories.value.join(',')
    }

    // 动画阶段推进
    if (reviewRequired.value) {
      analysisStage.value = 'review'
      await new Promise(r => setTimeout(r, 800))
    }
    analysisStage.value = 'done'

    // 复核失败提示
    messages.value = []
    if (reportError) {
      errorMsg.value = 'YOLO 识别完成，但 AI 复核失败'
    }

    farmStore.addIdentification({
      pestName: detectionResult.value.pestName,
      confidence: detectionResult.value.confidence,
      locationId: getLocationId(),
      imageUrl: detectionResult.value.imageUrl
    })

    isResultReady.value = true
    try {
      await persistRecognitionRecord(detectionResult.value)
    } catch (recordError) {
      console.error('Failed to persist PC recognition record', recordError)
    }
    await fetchHistory()
  } catch (err) {
    errorMsg.value = '识别失败，请检查网络或服务配置'
    console.error(err)
    analysisStage.value = ''
  } finally {
    isScanning.value = false
  }
}

// ====== 阶段 3：按需生成诊断报告 ======
const isGeneratingReport = ref(false)
const generateReport = async () => {
  if (isGeneratingReport.value) return
  isGeneratingReport.value = true
  errorMsg.value = ''

  try {
    const params = new URLSearchParams()
    params.append('imageUrl', detectionResult.value.imageUrl || '')
    params.append('categories', detectionResult.value.userCategories || selectedCategories.value.join(','))
    params.append('locationId', getLocationId())
    if (detectionResult.value.predictionJson) {
      params.append('predictionJson', detectionResult.value.predictionJson)
    }
    if (detectionResult.value.reviewResult) {
      params.append('reviewResult', detectionResult.value.reviewResult)
    }

    const response = await axios.post('/api/pest/diagnose', params, { timeout: 120000 })
    const report = response.data.report || ''

    detectionResult.value.report = report
    messages.value = report
      ? [{ role: 'assistant', content: report }]
      : [{ role: 'assistant', content: '诊断报告生成失败，请稍后重试。' }]

    scrollToBottom()
  } catch (err) {
    console.error('Report generation failed', err)
    errorMsg.value = '诊断报告生成失败：' + (err.response?.data?.error || '请检查网络')
  } finally {
    isGeneratingReport.value = false
  }
}

// ==== Chat Logic ====
const sendMessage = async () => {
  if (!(userInput.value.trim() || pendingImage.value) || isSending.value) return
  const text = userInput.value.trim()
  userInput.value = ''

  const base64Str = pendingImageBase64.value
  const displayUrl = pendingImage.value
  
  pendingImage.value = ''
  pendingImageBase64.value = ''

  messages.value.push({ role: 'user', content: text, imageBase64: displayUrl, rawBase64: base64Str })
  scrollToBottom()

  isSending.value = true
  try {
    const response = await axios.post('/api/ai/chat', {
      pestName: diagnosisContextName.value,
      messages: messages.value.map(m => ({ role: m.role, content: m.content, imageBase64: m.rawBase64 }))
    })
    messages.value.push({ role: 'assistant', content: response.data.reply })
  } catch (err) {
    errorMsg.value = '发送失败，请稍后重试'
  } finally {
    isSending.value = false
    scrollToBottom()
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  })
}

// ==== Helpers ====
const renderMarkdown = (text) => {
  if (!text) return ''
  let html = text
  html = html.replace(/### (.*)/g, '<h4 class="font-bold text-slate-800 dark:text-slate-100 mt-4 mb-2 text-md">$1</h4>')
  html = html.replace(/## (.*)/g, '<h3 class="font-bold text-slate-900 dark:text-slate-100 mt-6 mb-3 text-lg border-b border-slate-200 dark:border-slate-700 pb-2 flex items-center"><span class="w-1.5 h-5 bg-green-500 rounded-full mr-2"></span>$1</h3>')
  html = html.replace(/\*\*(.*?)\*\*/g, '<span class="font-bold text-slate-800 dark:text-slate-100">$1</span>')
  html = html.replace(/- (.*)/g, '<li class="ml-4 list-disc marker:text-green-500 mb-1">$1</li>')
  html = html.replace(/\n\n/g, '<br/>')
  return html
}

</script>

<template>
  <div class="p-8 h-full flex flex-col">
    <!-- Header -->
    <div class="mb-6">
      <h2 class="text-3xl font-black text-slate-800 dark:text-slate-100 tracking-tight">智能诊断工作台</h2>
      <p class="text-slate-500 dark:text-slate-400 mt-1 font-medium">利用核心 CV 引擎与 Dify 专家知识库，快速锁定作物病虫害。</p>
    </div>

    <!-- Workspace -->
    <div class="flex-1 flex gap-8 min-h-0">

      <!-- LEFT PANE: Category Selection + Upload + History -->
      <div class="w-1/2 flex flex-col gap-5">

        <!-- ★ Category Selection Panel ★ -->
        <div class="shrink-0 rounded-[2rem] border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 shadow-sm p-6 transition-all"
             :class="hasSelection ? 'ring-2 ring-emerald-400/40 border-emerald-300 dark:border-emerald-500/30' : ''">
          <div class="flex items-center gap-2 mb-4">
            <h3 class="font-bold text-slate-800 dark:text-slate-100 text-base">选择检测范围</h3>
            <span v-if="!hasSelection" class="ml-auto text-xs font-bold text-amber-500 dark:text-amber-300 animate-pulse">← 请至少选一项</span>
            <span v-else class="ml-auto text-xs font-bold text-emerald-500">已选 {{ selectedCategories.length }} 项</span>
          </div>

          <!-- 病害行 -->
          <div class="mb-3">
            <div class="text-[11px] font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider mb-2">作物与病害</div>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="opt in categoryOptions.filter(o => o.group === 'disease')"
                :key="opt.key"
                @click="toggleCategory(opt.value)"
                class="relative px-4 py-2.5 rounded-2xl border-2 text-sm font-bold transition-all duration-300 select-none flex items-center gap-1.5"
                :class="selectedCategories.includes(opt.value)
                  ? 'border-emerald-400 bg-emerald-50 text-emerald-700 shadow-md shadow-emerald-100 scale-[1.03] dark:bg-emerald-500/15 dark:text-emerald-200 dark:border-emerald-500/50 dark:shadow-emerald-500/10'
                  : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-50 hover:shadow-sm dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300 dark:hover:border-slate-600'"
              >
                <span class="mr-1.5">{{ opt.icon }}</span>{{ opt.label }}
                <span v-if="selectedCategories.includes(opt.value)" class="absolute -top-1.5 -right-1.5 w-5 h-5 bg-emerald-500 text-white rounded-full flex items-center justify-center text-[10px] shadow-sm">✓</span>
              </button>
            </div>
          </div>

          <!-- 虫害行 -->
          <div>
            <div class="text-[11px] font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider mb-2">虫害检测</div>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="opt in categoryOptions.filter(o => o.group === 'pest')"
                :key="opt.key"
                @click="toggleCategory(opt.value)"
                class="relative px-4 py-2.5 rounded-2xl border-2 text-sm font-bold transition-all duration-300 select-none flex items-center gap-1.5"
                :class="selectedCategories.includes(opt.value)
                  ? 'border-orange-400 bg-orange-50 text-orange-700 shadow-md shadow-orange-100 scale-[1.03] dark:bg-orange-500/15 dark:text-orange-200 dark:border-orange-500/50 dark:shadow-orange-500/10'
                  : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-50 hover:shadow-sm dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300 dark:hover:border-slate-600'"
              >
                <span class="mr-1.5">{{ opt.icon }}</span>{{ opt.label }}
                <span v-if="selectedCategories.includes(opt.value)" class="absolute -top-1.5 -right-1.5 w-5 h-5 bg-orange-500 text-white rounded-full flex items-center justify-center text-[10px] shadow-sm">✓</span>
              </button>
            </div>
          </div>
        </div>

        <!-- Dropzone -->
        <div
          @dragover="onDragOver"
          @dragleave="onDragLeave"
          @drop="onDrop"
          @click="triggerUpload"
          class="h-[220px] shrink-0 rounded-[2rem] border-2 border-dashed transition-all cursor-pointer flex flex-col items-center justify-center relative overflow-hidden group shadow-sm bg-white dark:bg-slate-900"
          :class="[
            !hasSelection ? 'opacity-50 cursor-not-allowed border-slate-300 dark:border-slate-700' :
            isDragging ? 'border-green-500 bg-green-50 shadow-md transform scale-[1.02] dark:bg-emerald-500/10' :
            'border-slate-300 dark:border-slate-600 hover:border-green-400 hover:shadow-md hover:bg-slate-50 dark:hover:bg-slate-800'
          ]"
        >
          <input type="file" ref="fileInput" accept="image/*" class="hidden" @change="onFileSelected" />

          <!-- 扫描中 — 多阶段动画 -->
          <template v-if="isScanning">
            <img v-if="previewUrl" :src="previewUrl" class="absolute inset-0 w-full h-full object-cover opacity-20 blur-[2px]" />
            <div class="relative z-10 flex flex-col items-center">
              <!-- YOLO 阶段 -->
              <div v-if="analysisStage === 'yolo'" class="flex flex-col items-center animate-fadeIn">
                <div class="w-16 h-16 relative mb-3">
                  <div class="absolute inset-0 border-4 border-emerald-200 rounded-full dark:border-emerald-800"></div>
                  <div class="absolute inset-0 border-4 border-emerald-500 rounded-full border-t-transparent animate-spin"></div>
                </div>
                <span class="text-base font-black text-emerald-700 dark:text-emerald-300">🔬 YOLO 模型检测中...</span>
                <span class="text-xs text-slate-400 mt-1">正在扫描图像中的病虫害特征</span>
              </div>

              <!-- Vision 复核阶段 -->
              <div v-else-if="analysisStage === 'review'" class="flex flex-col items-center animate-fadeIn">
                <div class="w-16 h-16 relative mb-3">
                  <div class="absolute inset-0 border-4 border-amber-200 rounded-full dark:border-amber-800"></div>
                  <div class="absolute inset-0 border-4 border-amber-500 rounded-full border-t-transparent animate-spin" style="animation-duration: 1.5s"></div>
                </div>
                <span class="text-base font-black text-amber-700 dark:text-amber-300">🧠 AI 视觉复核中...</span>
                <span class="text-xs text-slate-400 mt-1">大模型正在对图像进行深度分析</span>
              </div>

              <!-- 完成阶段 -->
              <div v-else-if="analysisStage === 'done'" class="flex flex-col items-center animate-fadeIn">
                <div class="w-16 h-16 bg-emerald-100 dark:bg-emerald-500/15 rounded-full flex items-center justify-center mb-3">
                  <span class="text-3xl">✅</span>
                </div>
                <span class="text-base font-black text-emerald-700 dark:text-emerald-300">分析完成</span>
              </div>
            </div>
          </template>

          <!-- 未扫描 — 上传提示 -->
          <template v-else>
            <div class="w-16 h-16 bg-slate-100 dark:bg-slate-800 rounded-full flex items-center justify-center mb-4 transition-transform group-hover:scale-110 shadow-sm border border-slate-200 dark:border-slate-700">
              <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-8 h-8 text-slate-400 dark:text-slate-500">
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 16.5V9.75m0 0 3 3m-3-3-3 3M6.75 19.5a4.5 4.5 0 0 1-1.41-8.775 5.25 5.25 0 0 1 10.233-2.33 3 3 0 0 1 3.758 3.848A3.752 3.752 0 0 1 18 19.5H6.75Z" />
              </svg>
            </div>
            <h3 class="text-lg font-bold text-slate-700 dark:text-slate-200 mb-1">拖拽或点击上传图片</h3>
            <p class="text-sm font-medium text-slate-400 dark:text-slate-500 max-w-[200px] text-center">支持多目标检测引擎解析</p>
          </template>
        </div>

        <div v-if="errorMsg" class="rounded-xl border border-red-100 bg-red-50 p-3 text-sm font-bold text-red-600 dark:border-red-500/20 dark:bg-red-500/10 dark:text-red-200">
           提示：{{ errorMsg }}
        </div>

        <!-- History Strip -->
        <div class="flex-1 bg-white dark:bg-slate-900 rounded-[2rem] border border-slate-100 dark:border-slate-800 shadow-sm p-6 flex flex-col min-h-0 overflow-hidden">
           <h3 class="font-bold text-slate-800 dark:text-slate-100 text-lg mb-4 flex items-center"><span class="mr-2">📋</span> 诊断记录</h3>
           <div class="flex-1 overflow-y-auto custom-scrollbar pr-2 grid grid-cols-2 xl:grid-cols-3 gap-3 content-start">
             <div v-if="historyItems.length === 0" class="col-span-full py-8 text-center text-sm text-slate-400 dark:text-slate-500">暂无记录，立刻上传第一张图片吧</div>

             <div v-for="item in historyItems" :key="item.id" class="p-4 rounded-2xl border border-slate-100 dark:border-slate-800 hover:border-green-300 hover:shadow-md transition-all flex items-center gap-3 group cursor-pointer">
               <div class="w-12 h-12 bg-slate-100 dark:bg-slate-800 rounded-xl overflow-hidden shrink-0">
                 <img v-if="item.imageUrl" :src="item.imageUrl" class="w-full h-full object-cover group-hover:scale-110 transition-transform" />
               </div>
               <div class="min-w-0">
                 <div class="font-bold text-slate-800 dark:text-slate-100 text-sm truncate w-full">{{ item.name }}</div>
                <div class="text-[11px] font-bold mt-0.5" :class="item.confidence > 0.8 ? 'text-green-500' : 'text-orange-500'">AI 置信度 {{ (item.confidence * 100).toFixed(0) }}%</div>
               </div>
             </div>
           </div>
        </div>
      </div>

      <!-- RIGHT PANE: Result & Chat -->
      <div class="w-1/2 bg-white dark:bg-slate-900 rounded-[2rem] border border-slate-100 dark:border-slate-800 shadow-sm flex flex-col overflow-hidden relative">
         <template v-if="!isResultReady">
            <div class="flex-1 flex flex-col items-center justify-center bg-slate-50/50 text-slate-300 dark:bg-slate-900/50 dark:text-slate-600">
               <div class="text-6xl mb-6 grayscale opacity-30 font-black">AI</div>
               <h3 class="mb-2 text-xl font-bold text-slate-600 dark:text-slate-200">工作台等待接入</h3>
               <p class="text-sm text-slate-400 dark:text-slate-500">选择检测范围并上传图片即可激活 Dify 大模型专家流</p>
            </div>
         </template>

         <template v-else>
            <!-- Result Header -->
            <div class="shrink-0 p-6 border-b border-slate-100 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/50">

              <!-- YOLO 检测结果面板 -->
              <div class="flex items-center justify-between">
                <div class="flex items-center gap-4">
                  <div class="w-14 h-14 rounded-2xl overflow-hidden shadow-md">
                    <img v-if="detectionResult.imageUrl" :src="detectionResult.imageUrl" class="w-full h-full object-cover" />
                  </div>
                  <div>
                    <div class="text-xs font-bold text-slate-400 mb-1 uppercase tracking-wider flex items-center gap-1.5">
                      <span v-if="yoloUsed" class="text-emerald-500">🔬 YOLO 检测</span>
                      <span v-else class="text-amber-500">🧠 Vision AI 诊断</span>
                    </div>
                    <div class="text-2xl font-black text-slate-800 dark:text-slate-100 leading-none">{{ primaryDisplayName }}</div>
                    <div class="mt-2 flex flex-wrap items-center gap-2 text-[11px] font-bold">
                      <span class="rounded-full px-3 py-1" :class="sceneBadgeClasses">{{ sceneMeta.label }}</span>
                      <span v-if="reviewRequired && detectionResult?.reviewResult" class="rounded-full bg-emerald-100 px-3 py-1 text-emerald-600 dark:bg-emerald-500/15 dark:text-emerald-300">
                        🧠 已复核
                      </span>
                      <span v-else-if="reviewRequired && !detectionResult?.reviewResult" class="rounded-full bg-amber-100 px-3 py-1 text-amber-600 dark:bg-amber-500/15 dark:text-amber-300">
                        ⏳ 待复核
                      </span>
                      <span v-else-if="!reviewRequired && yoloUsed" class="rounded-full bg-slate-100 px-3 py-1 text-slate-500 dark:bg-slate-700 dark:text-slate-400">
                        无需复核
                      </span>
                    </div>
                  </div>
                </div>
                <div class="text-right" v-if="detectionResult.confidence > 0">
                  <div class="text-xs font-bold text-slate-400 mb-1">AI 置信度</div>
                  <div class="text-3xl font-black text-green-500">{{ formatConfidence(detectionResult.confidence, 1) }}</div>
                </div>
              </div>

              <!-- YOLO 检测详情（仅 YOLO 路径展示） -->
              <div v-if="yoloUsed && summaryItems.length" class="mt-4 grid gap-2 xl:grid-cols-2">
                <div
                  v-for="item in summaryItems"
                  :key="item.id"
                  class="rounded-xl border border-slate-200 bg-slate-50/90 px-4 py-3 dark:border-slate-700 dark:bg-slate-800/70 flex items-center justify-between"
                >
                  <div>
                    <div class="font-bold text-sm text-slate-800 dark:text-slate-100">{{ item.nameZh }}</div>
                    <div class="text-[11px] text-slate-400 dark:text-slate-500">检测 {{ item.count }} 处</div>
                  </div>
                  <div class="text-xs font-bold text-emerald-600 dark:text-emerald-300">
                    {{ formatConfidence(item.maxConfidence, 1) }}
                  </div>
                </div>
              </div>

              <!-- 复核结论卡片（阶段 2 结果） -->
              <div v-if="detectionResult?.reviewResult" class="mt-4 rounded-xl border border-emerald-200 bg-emerald-50/80 dark:border-emerald-500/20 dark:bg-emerald-500/5 p-4">
                <div class="flex items-center gap-2 mb-2">
                  <span class="text-base">🧠</span>
                  <span class="text-sm font-bold text-emerald-800 dark:text-emerald-200">AI 视觉复核结论</span>
                </div>
                <div class="text-sm text-slate-700 dark:text-slate-300 leading-relaxed whitespace-pre-line">{{ detectionResult.reviewResult }}</div>
              </div>

              <!-- 待复核提示（Dify 复核失败时） -->
              <div v-else-if="reviewRequired" class="mt-4 rounded-xl border border-amber-200 bg-amber-50/80 dark:border-amber-500/20 dark:bg-amber-500/5 px-4 py-3 flex items-center gap-2">
                <span class="text-base">⏳</span>
                <span class="text-xs font-bold text-amber-700 dark:text-amber-300">AI 复核暂时不可用，请根据 YOLO 检测结果自行判断</span>
              </div>

              <!-- 阶段 3：生成诊断报告按钮 -->
              <button 
                v-if="!detectionResult?.report"
                @click="generateReport" 
                :disabled="isGeneratingReport"
                class="mt-4 w-full py-3 rounded-xl font-bold text-sm transition-all active:scale-[0.98] flex items-center justify-center gap-2"
                :class="reviewRequired 
                  ? 'bg-amber-500 text-white shadow-lg shadow-amber-500/20 hover:bg-amber-600 animate-pulse-subtle' 
                  : 'bg-slate-900 dark:bg-emerald-600 text-white shadow-lg shadow-slate-900/20 hover:bg-slate-800 dark:hover:bg-emerald-500'"
              >
                <template v-if="isGeneratingReport">
                  <svg class="animate-spin w-4 h-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path></svg>
                  <span>AI 诊断报告生成中...</span>
                </template>
                <template v-else>
                  <span>📋</span>
                  <span>生成 AI 诊断建议</span>
                  <span v-if="reviewRequired" class="text-xs opacity-80 ml-1">(推荐)</span>
                </template>
              </button>
            </div>

            <!-- Chat Area -->
            <div ref="chatContainer" class="custom-scrollbar flex-1 overflow-y-auto bg-slate-50/30 p-6 space-y-6 dark:bg-slate-950/[0.55]">
               <div v-for="(msg, index) in messages" :key="index" class="flex w-full" :class="msg.role === 'user' ? 'justify-end' : 'justify-start'">

                 <!-- AI Message -->
                 <div v-if="msg.role === 'assistant'" class="flex items-start space-x-3 max-w-[90%]">
                   <div class="w-8 h-8 rounded-xl bg-slate-900 text-white flex items-center justify-center text-xs shrink-0 shadow-md">AI</div>
                   <div class="rounded-2xl rounded-tl-sm border border-slate-200 bg-white px-6 py-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
                     <div class="markdown-body text-slate-700 dark:text-slate-200 text-sm leading-relaxed" v-html="renderMarkdown(msg.content)"></div>
                   </div>
                 </div>

                 <!-- User Message -->
                 <div v-else class="flex items-end space-x-2 max-w-[80%]">
                   <div class="bg-green-500 text-white rounded-2xl rounded-br-sm shadow-md overflow-hidden">
                     <div class="p-1">
                       <img v-if="msg.imageBase64" :src="msg.imageBase64" class="w-full max-w-[200px] object-cover rounded-xl" :class="msg.content ? 'mb-2' : ''" />
                       <div v-if="msg.content" :class="msg.imageBase64 ? 'px-3 pb-2 pt-1' : 'px-4 py-2'">
                         <p class="text-sm leading-relaxed whitespace-pre-wrap">{{ msg.content }}</p>
                       </div>
                     </div>
                   </div>
                 </div>
               </div>

               <!-- Sending Animation -->
               <div v-if="isSending" class="flex items-start space-x-3 max-w-[90%]">
                 <div class="w-8 h-8 rounded-xl bg-slate-900 text-white flex items-center justify-center text-xs shrink-0 shadow-md">AI</div>
                 <div class="flex space-x-1 rounded-2xl rounded-tl-sm border border-slate-200 bg-white px-6 py-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
                    <span class="h-2 w-2 animate-bounce rounded-full bg-slate-300 dark:bg-slate-600" style="animation-delay:0ms"></span>
                    <span class="h-2 w-2 animate-bounce rounded-full bg-slate-300 dark:bg-slate-600" style="animation-delay:150ms"></span>
                    <span class="h-2 w-2 animate-bounce rounded-full bg-slate-300 dark:bg-slate-600" style="animation-delay:300ms"></span>
                 </div>
               </div>
            </div>

            <!-- Input Area -->
            <div class="shrink-0 p-4 bg-white dark:bg-slate-900 border-t border-slate-100 dark:border-slate-800 relative flex flex-col">
               <div v-if="pendingImage" class="mb-3 relative w-16 h-16 ml-2">
                 <img :src="pendingImage" class="w-full h-full object-cover rounded-xl border border-slate-200 dark:border-slate-700 shadow-sm" />
                 <button @click="removePendingImage" class="absolute -top-2 -right-2 bg-red-500 text-white rounded-full w-5 h-5 flex items-center justify-center text-xs pb-[1px] hover:bg-red-600 shadow-md">×</button>
               </div>
               <form @submit.prevent="sendMessage" class="relative flex items-center">
                 <input type="file" accept="image/*" class="hidden" ref="imageInputRef" @change="onImageSelected" />
                 <button type="button" @click="triggerImageUpload" class="absolute left-3 w-8 h-8 flex items-center justify-center text-slate-400 hover:text-emerald-500 transition-colors z-10" :disabled="isSending">
                   <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
                     <path stroke-linecap="round" stroke-linejoin="round" d="m2.25 15.75 5.159-5.159a2.25 2.25 0 0 1 3.182 0l5.159 5.159m-1.5-1.5 1.409-1.409a2.25 2.25 0 0 1 3.182 0l2.909 2.909m-18 3.75h16.5a1.5 1.5 0 0 0 1.5-1.5V6a1.5 1.5 0 0 0-1.5-1.5H3.75A1.5 1.5 0 0 0 2.25 6v12a1.5 1.5 0 0 0 1.5 1.5Zm10.5-11.25h.008v.008h-.008V8.25Zm.375 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Z" />
                   </svg>
                 </button>
                 <input v-model="userInput" type="text" placeholder="就这次诊断结果向大模型提问..." :disabled="isSending"
                        class="w-full bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-800 dark:text-slate-100 rounded-2xl pl-[3.25rem] pr-14 py-4 font-medium focus:outline-none focus:border-green-400 focus:ring-4 focus:ring-green-400/10 transition-all" />
                 <button type="submit" :disabled="!(userInput.trim() || pendingImage) || isSending"
                         class="absolute right-2 top-2 bottom-2 aspect-square rounded-xl bg-slate-900 text-white shadow-md transition-all hover:bg-black disabled:bg-slate-300 disabled:text-slate-500 dark:bg-emerald-500 dark:text-slate-950 dark:hover:bg-emerald-400 dark:disabled:bg-slate-700 dark:disabled:text-slate-500">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-5 h-5 -rotate-45 ml-1 mb-1"><path d="M3.478 2.404a.75.75 0 00-.926.941l2.432 7.905H13.5a.75.75 0 010 1.5H4.984l-2.432 7.905a.75.75 0 00.926.94 60.519 60.519 0 0018.445-8.986.75.75 0 000-1.218A60.517 60.517 0 003.478 2.404z" /></svg>
                 </button>
               </form>
            </div>
         </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.custom-scrollbar::-webkit-scrollbar { width: 6px; }
.custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
.custom-scrollbar::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }
.custom-scrollbar::-webkit-scrollbar-thumb:hover { background: #94a3b8; }
:global(.dark .custom-scrollbar::-webkit-scrollbar-thumb) { background: #334155; }
:global(.dark .custom-scrollbar::-webkit-scrollbar-thumb:hover) { background: #475569; }

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
.animate-fadeIn {
  animation: fadeIn 0.4s ease-out;
}
</style>
