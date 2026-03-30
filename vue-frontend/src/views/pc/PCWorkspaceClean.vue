<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import axios from 'axios'
import { useFarmStore } from '../../stores/farmCloud'
import DiagnosisSelectionPanel from '../../components/mobile/DiagnosisSelectionPanel.vue'
import {
  formatConfidencePercent,
  getPrimaryDisplayName,
  getSceneMeta,
  normalizeClassNames,
  normalizeDetectionSummary
} from '../../utils/detectionPresentationClean'
import {
  buildFilteredTargetOptions,
  createEmptyDiagnosisResult,
  hasAnySelection,
  normalizeDiagnosisResponse
} from '../../utils/diagnosisSelection'

const farmStore = useFarmStore()

const isDragging = ref(false)
const isScanning = ref(false)
const isDiagnosing = ref(false)
const errorMsg = ref('')
const previewUrl = ref('')
const fileInput = ref(null)
const chatContainer = ref(null)

const isResultReady = ref(false)
const detectionResult = ref(createEmptyDiagnosisResult())
const historyItems = ref([])
const messages = ref([])
const userInput = ref('')
const isSending = ref(false)

const selectedCropNames = ref([])
const selectedTargetNames = ref([])

const sceneBadgeClassMap = {
  single: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300',
  multi: 'bg-amber-100 text-amber-700 dark:bg-amber-500/15 dark:text-amber-300',
  uncertain: 'bg-orange-100 text-orange-700 dark:bg-orange-500/15 dark:text-orange-200',
  empty: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300'
}

const summaryItems = computed(() => normalizeDetectionSummary(detectionResult.value.detectedSummary))
const detectedClassNames = computed(() => normalizeClassNames(detectionResult.value.classNamesZh, detectionResult.value.detectedSummary))
const sceneMeta = computed(() => getSceneMeta(detectionResult.value.sceneType))
const sceneBadgeClasses = computed(() => sceneBadgeClassMap[detectionResult.value.sceneType] || sceneBadgeClassMap.single)
const primaryDisplayName = computed(() => getPrimaryDisplayName(detectionResult.value))
const diagnosisContextName = computed(() => {
  if (detectionResult.value.sceneType === 'multi') {
    return detectedClassNames.value.join('、') || primaryDisplayName.value
  }
  return primaryDisplayName.value
})
const classCountDisplay = computed(() => detectionResult.value.classCount || summaryItems.value.length)
const targetCountDisplay = computed(() => {
  if (detectionResult.value.targetCount) return detectionResult.value.targetCount
  return summaryItems.value.reduce((total, item) => total + item.count, 0)
})
const cropOptions = computed(() => detectionResult.value.selectionOptions.cropOptions || [])
const targetOptions = computed(() => buildFilteredTargetOptions(
  detectionResult.value.selectionOptions.targetOptions || [],
  selectedCropNames.value
))
const selectionReady = computed(() => hasAnySelection(selectedCropNames.value, selectedTargetNames.value))
const resultHint = computed(() => {
  if (detectionResult.value.finalized) {
    return '最终诊断已生成，可以继续围绕这次识别结果追问。'
  }
  if (detectionResult.value.reviewRequired && detectionResult.value.reviewReason) {
    return detectionResult.value.reviewReason
  }
  return '请先补充作物或病虫害对象，再生成最终诊断。'
})

const formatConfidence = (value, digits = 1) => formatConfidencePercent(value, digits)

onMounted(async () => {
  await farmStore.initialize()
  await fetchHistory()
})

const getCurrentUser = () => {
  try {
    const userStr = localStorage.getItem('user')
    return userStr ? JSON.parse(userStr) : null
  } catch (error) {
    console.error('Failed to parse current user', error)
    return null
  }
}

const getActiveCropContext = () => ({
  cropId: farmStore.activeCrop?.id || null,
  cropName: farmStore.activeCrop?.name || '',
  locationId: farmStore.activeCrop?.locationId || '101010100',
  city: farmStore.activeCrop?.city || '',
  region: farmStore.activeCrop?.region || ''
})

const fetchHistory = async () => {
  try {
    const user = getCurrentUser()
    if (!user?.userId) return

    const response = await axios.get(`/api/record/list?userId=${user.userId}`)
    if (response.data?.code === 200) {
      const records = response.data.data || []
      farmStore.syncIdentificationHistory(records)
      historyItems.value = records.map(item => ({
        id: item.id,
        name: item.pestName,
        confidence: item.confidence,
        time: item.createTime,
        imageUrl: item.imageUrl,
        cropName: item.cropName
      }))
    }
  } catch (error) {
    console.error('Failed to load history', error)
  }
}

const persistRecognitionRecord = async result => {
  const user = getCurrentUser()
  if (!user?.userId) return

  const cropContext = getActiveCropContext()
  await axios.post('/api/record/add', {
    userId: user.userId,
    cropId: cropContext.cropId,
    cropName: cropContext.cropName,
    pestName: result.pestName,
    confidence: result.confidence,
    locationId: cropContext.locationId,
    city: cropContext.city,
    region: cropContext.region,
    imageUrl: result.imageUrl
  })
}

const triggerUpload = () => {
  fileInput.value?.click()
}

const resetConversation = () => {
  messages.value = []
  userInput.value = ''
}

const setDefaultSelections = result => {
  const activeCropName = farmStore.activeCrop?.name
  const cropLabels = cropOptions.value.map(option => option.label)
  selectedCropNames.value = activeCropName && cropLabels.includes(activeCropName) ? [activeCropName] : []
  selectedTargetNames.value = []

  const detectedLabelSet = new Set(detectedClassNames.value)
  const keptTargets = (result.selectionOptions.targetOptions || [])
    .filter(option => detectedLabelSet.has(option.label) && option.source === 'detected')
    .map(option => option.label)
  if (!selectedCropNames.value.length && keptTargets.length === 1) {
    selectedTargetNames.value = keptTargets
  }
}

const processFile = async file => {
  previewUrl.value = URL.createObjectURL(file)
  isScanning.value = true
  errorMsg.value = ''
  isResultReady.value = false
  resetConversation()

  try {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('locationId', getActiveCropContext().locationId)

    const response = await axios.post('/api/pest/detect', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 30000
    })

    detectionResult.value = normalizeDiagnosisResponse(response.data, previewUrl.value)
    isResultReady.value = true
    setDefaultSelections(detectionResult.value)
  } catch (error) {
    errorMsg.value = '识别失败，请检查网络或稍后再试'
    console.error('PC detect failed', error)
  } finally {
    isScanning.value = false
  }
}

const onDragOver = event => {
  event.preventDefault()
  isDragging.value = true
}

const onDragLeave = event => {
  event.preventDefault()
  isDragging.value = false
}

const onDrop = event => {
  event.preventDefault()
  isDragging.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file && file.type.startsWith('image/')) {
    processFile(file)
  } else {
    errorMsg.value = '请上传图片文件'
  }
}

const onFileSelected = event => {
  const file = event.target.files?.[0]
  if (file) {
    processFile(file)
  }
  event.target.value = ''
}

const finalizeDiagnosis = async () => {
  if (!selectionReady.value || isDiagnosing.value || !detectionResult.value.imageToken) return

  isDiagnosing.value = true
  errorMsg.value = ''
  resetConversation()

  try {
    const response = await axios.post('/api/pest/diagnose', {
      image_token: detectionResult.value.imageToken,
      prediction_snapshot: {
        pest_name: detectionResult.value.pestName,
        confidence: detectionResult.value.confidence,
        scene_type: detectionResult.value.sceneType,
        primary_target: detectionResult.value.primaryTarget,
        primary_target_zh: detectionResult.value.primaryTargetZh,
        primary_confidence: detectionResult.value.primaryConfidence,
        class_count: detectionResult.value.classCount,
        target_count: detectionResult.value.targetCount,
        class_names_zh: detectionResult.value.classNamesZh,
        detected_summary: detectionResult.value.detectedSummary
      },
      selected_crop_names: selectedCropNames.value,
      selected_target_names: selectedTargetNames.value,
      location_id: getActiveCropContext().locationId
    })

    detectionResult.value = normalizeDiagnosisResponse(response.data, detectionResult.value.imageUrl || previewUrl.value)
    messages.value = detectionResult.value.report
      ? [{ role: 'assistant', content: detectionResult.value.report }]
      : [{ role: 'assistant', content: '已完成最终诊断，但报告内容暂时为空。' }]

    farmStore.addIdentification({
      pestName: detectionResult.value.pestName,
      confidence: detectionResult.value.confidence,
      cropId: getActiveCropContext().cropId,
      cropName: getActiveCropContext().cropName,
      locationId: getActiveCropContext().locationId,
      city: getActiveCropContext().city,
      region: getActiveCropContext().region,
      imageUrl: detectionResult.value.imageUrl
    })

    await persistRecognitionRecord(detectionResult.value)
    await fetchHistory()
    scrollToBottom()
  } catch (error) {
    errorMsg.value = error.response?.data?.error || '生成最终诊断失败，请稍后再试'
    console.error('PC diagnose failed', error)
  } finally {
    isDiagnosing.value = false
  }
}

const sendMessage = async () => {
  if (!detectionResult.value.finalized || !userInput.value.trim() || isSending.value) return

  const text = userInput.value.trim()
  userInput.value = ''
  messages.value.push({ role: 'user', content: text })
  scrollToBottom()

  isSending.value = true
  try {
    const response = await axios.post('/api/ai/chat', {
      pestName: diagnosisContextName.value,
      messages: messages.value.map(message => ({ role: message.role, content: message.content }))
    })
    messages.value.push({ role: 'assistant', content: response.data.reply })
  } catch (error) {
    errorMsg.value = '追问发送失败，请稍后重试'
    console.error('PC chat failed', error)
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

const renderMarkdown = text => {
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
    <div class="mb-6">
      <h2 class="text-3xl font-black text-slate-800 dark:text-slate-100 tracking-tight">智能诊断工作台</h2>
      <p class="text-slate-500 dark:text-slate-400 mt-1 font-medium">先做 YOLO 初判，再结合你的作物和病虫害选择生成最终诊断。</p>
    </div>

    <div class="flex-1 flex gap-8 min-h-0">
      <div class="w-1/2 flex flex-col gap-6">
        <div
          class="h-[300px] shrink-0 rounded-[2rem] border-2 border-dashed transition-all cursor-pointer flex flex-col items-center justify-center relative overflow-hidden group shadow-sm bg-white dark:bg-slate-900"
          :class="isDragging ? 'border-green-500 bg-green-50 shadow-md scale-[1.02] dark:bg-emerald-500/10' : 'border-slate-300 dark:border-slate-600 hover:border-green-400 hover:shadow-md hover:bg-slate-50 dark:hover:bg-slate-800'"
          @dragover="onDragOver"
          @dragleave="onDragLeave"
          @drop="onDrop"
          @click="triggerUpload"
        >
          <input ref="fileInput" type="file" accept="image/*" class="hidden" @change="onFileSelected" />

          <template v-if="!isScanning">
            <div class="w-20 h-20 rounded-full bg-slate-100 text-slate-400 shadow-sm transition-transform group-hover:scale-110 group-hover:bg-white group-hover:shadow group-hover:text-green-500 dark:bg-slate-800 dark:text-slate-500 dark:group-hover:bg-slate-700 dark:group-hover:text-emerald-300 flex items-center justify-center text-4xl mb-4">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-10 h-10">
                <path d="M12 3.75a.75.75 0 01.75.75v8.19l2.72-2.72a.75.75 0 111.06 1.06l-4 4a.75.75 0 01-1.06 0l-4-4a.75.75 0 111.06-1.06l2.72 2.72V4.5a.75.75 0 01.75-.75Z" />
                <path d="M3.75 15a.75.75 0 01.75.75v1.5A1.5 1.5 0 006 18.75h12a1.5 1.5 0 001.5-1.5v-1.5a.75.75 0 011.5 0v1.5A3 3 0 0118 20.25H6a3 3 0 01-3-3v-1.5a.75.75 0 01.75-.75Z" />
              </svg>
            </div>
            <h3 class="text-xl font-bold text-slate-700 dark:text-slate-200">拖拽或点击上传图片</h3>
            <p class="mt-2 text-sm font-medium text-slate-400 dark:text-slate-500">支持 JPG、PNG、WEBP 高清图片</p>
          </template>

          <template v-else>
            <img v-if="previewUrl" :src="previewUrl" class="absolute inset-0 w-full h-full object-cover opacity-30" />
            <div class="relative z-10 flex flex-col items-center">
              <div class="w-16 h-16 relative mb-4">
                <div class="absolute inset-0 border-4 border-green-100 rounded-full"></div>
                <div class="absolute inset-0 border-4 border-green-500 rounded-full border-t-transparent animate-spin"></div>
              </div>
              <span class="text-lg font-bold text-green-700 dark:text-emerald-300">正在进行初步识别...</span>
            </div>
          </template>
        </div>

        <div v-if="errorMsg" class="rounded-xl border border-red-100 bg-red-50 p-3 text-sm font-bold text-red-600 dark:border-red-500/20 dark:bg-red-500/10 dark:text-red-200">
          提示：{{ errorMsg }}
        </div>

        <div class="flex-1 bg-white dark:bg-slate-900 rounded-[2rem] border border-slate-100 dark:border-slate-800 shadow-sm p-6 flex flex-col min-h-0 overflow-hidden">
          <h3 class="font-bold text-slate-800 dark:text-slate-100 text-lg mb-4">诊断记录</h3>
          <div class="flex-1 overflow-y-auto custom-scrollbar pr-2 grid grid-cols-2 xl:grid-cols-3 gap-3 content-start">
            <div v-if="historyItems.length === 0" class="col-span-full py-8 text-center text-sm text-slate-400 dark:text-slate-500">
              暂无诊断记录，上传图片后会在这里显示。
            </div>

            <div
              v-for="item in historyItems"
              :key="item.id"
              class="p-4 rounded-2xl border border-slate-100 dark:border-slate-800 hover:border-green-300 hover:shadow-md transition-all flex items-center gap-3 group"
            >
              <div class="w-12 h-12 bg-slate-100 dark:bg-slate-800 rounded-xl overflow-hidden shrink-0">
                <img v-if="item.imageUrl" :src="item.imageUrl" class="w-full h-full object-cover group-hover:scale-110 transition-transform" />
              </div>
              <div class="min-w-0">
                <div class="font-bold text-slate-800 dark:text-slate-100 text-sm truncate">{{ item.name }}</div>
                <div class="text-[11px] font-bold mt-0.5" :class="item.confidence > 0.8 ? 'text-green-500' : 'text-orange-500'">
                  {{ formatConfidence(item.confidence, 0) }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="w-1/2 bg-white dark:bg-slate-900 rounded-[2rem] border border-slate-100 dark:border-slate-800 shadow-sm flex flex-col overflow-hidden relative">
        <template v-if="!isResultReady">
          <div class="flex-1 flex flex-col items-center justify-center bg-slate-50/50 text-slate-300 dark:bg-slate-900/50 dark:text-slate-600">
            <div class="text-6xl mb-6 opacity-30 font-black">AI</div>
            <h3 class="mb-2 text-xl font-bold text-slate-600 dark:text-slate-200">工作台等待接入图片</h3>
            <p class="text-sm text-slate-400 dark:text-slate-500">上传图片后会先展示初判结果，再由你确认作物和病虫害线索。</p>
          </div>
        </template>

        <template v-else>
          <div class="shrink-0 p-6 border-b border-slate-100 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-900/50 flex items-center justify-between">
            <div class="flex items-center gap-4">
              <div class="w-14 h-14 rounded-2xl overflow-hidden shadow-md">
                <img v-if="detectionResult.imageUrl" :src="detectionResult.imageUrl" class="w-full h-full object-cover" />
              </div>
              <div>
                <div class="text-xs font-bold text-slate-400 mb-1 uppercase tracking-wider">当前结果</div>
                <div class="text-2xl font-black text-slate-800 dark:text-slate-100 leading-none">{{ primaryDisplayName }}</div>
                <div class="mt-2 flex flex-wrap items-center gap-2 text-[11px] font-bold">
                  <span class="rounded-full px-3 py-1" :class="sceneBadgeClasses">{{ sceneMeta.label }}</span>
                  <span class="rounded-full bg-slate-100 px-3 py-1 text-slate-500 dark:bg-slate-800 dark:text-slate-300">类别 {{ classCountDisplay }}</span>
                  <span class="rounded-full bg-slate-100 px-3 py-1 text-slate-500 dark:bg-slate-800 dark:text-slate-300">目标 {{ targetCountDisplay }}</span>
                </div>
              </div>
            </div>
            <div class="text-right">
              <div class="text-xs font-bold text-slate-400 mb-1">主目标置信度</div>
              <div class="text-3xl font-black text-green-500">{{ formatConfidence(detectionResult.primaryConfidence || detectionResult.confidence, 1) }}</div>
            </div>
          </div>

          <div ref="chatContainer" class="custom-scrollbar flex-1 overflow-y-auto bg-slate-50/30 p-6 space-y-6 dark:bg-slate-950/[0.55]">
            <div class="rounded-[1.75rem] border border-slate-200/80 bg-white/90 p-5 shadow-sm dark:border-slate-700 dark:bg-slate-900/80">
              <div class="flex flex-col gap-4 lg:flex-row lg:items-start lg:justify-between">
                <div class="min-w-0">
                  <div class="text-sm font-bold text-slate-800 dark:text-slate-100">检测概览</div>
                  <p class="mt-1 text-sm text-slate-500 dark:text-slate-400">{{ sceneMeta.description }}</p>
                </div>
                <div class="shrink-0 rounded-2xl bg-slate-50 px-4 py-3 text-right dark:bg-slate-800/70">
                  <div class="text-[11px] font-bold uppercase tracking-wide text-slate-400">当前状态</div>
                  <div class="mt-1 text-sm font-bold text-slate-700 dark:text-slate-200">{{ resultHint }}</div>
                </div>
              </div>

              <div v-if="detectedClassNames.length" class="mt-4 flex flex-wrap gap-2">
                <span
                  v-for="className in detectedClassNames"
                  :key="className"
                  class="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-bold text-slate-600 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-200"
                >
                  {{ className }}
                </span>
              </div>

              <div v-if="summaryItems.length" class="mt-4 grid gap-3 xl:grid-cols-2">
                <div
                  v-for="item in summaryItems"
                  :key="item.id"
                  class="rounded-2xl border border-slate-200 bg-slate-50/90 p-4 dark:border-slate-700 dark:bg-slate-800/70"
                >
                  <div class="flex items-start justify-between gap-3">
                    <div class="min-w-0">
                      <div class="font-bold text-slate-800 dark:text-slate-100">{{ item.nameZh }}</div>
                      <div class="mt-1 text-xs font-medium text-slate-500 dark:text-slate-400">检测数量 {{ item.count }}</div>
                    </div>
                    <div class="rounded-full bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-600 dark:bg-emerald-500/15 dark:text-emerald-300">
                      最高 {{ formatConfidence(item.maxConfidence, 1) }}
                    </div>
                  </div>
                  <div class="mt-3 h-2 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
                    <div class="h-full rounded-full bg-gradient-to-r from-green-400 to-emerald-500" :style="{ width: `${Math.max(6, Math.min(item.maxConfidence * 100, 100))}%` }"></div>
                  </div>
                  <div class="mt-2 text-xs text-slate-400 dark:text-slate-500">平均置信度 {{ formatConfidence(item.avgConfidence, 1) }}</div>
                </div>
              </div>
            </div>

            <DiagnosisSelectionPanel
              v-if="!detectionResult.finalized"
              v-model:selected-crop-names="selectedCropNames"
              v-model:selected-target-names="selectedTargetNames"
              :crop-options="cropOptions"
              :target-options="targetOptions"
              :loading="isDiagnosing"
              :selection-conflict="detectionResult.selectionConflict"
              :selection-conflict-reason="detectionResult.selectionConflictReason"
              :review-required="detectionResult.reviewRequired"
              :review-reason="detectionResult.reviewReason"
              action-label="确认选择并生成最终诊断"
              @submit="finalizeDiagnosis"
            />

            <template v-else>
              <div v-for="(msg, index) in messages" :key="index" class="flex w-full" :class="msg.role === 'user' ? 'justify-end' : 'justify-start'">
                <div v-if="msg.role === 'assistant'" class="flex items-start space-x-3 max-w-[90%]">
                  <div class="w-8 h-8 rounded-xl bg-slate-900 text-white flex items-center justify-center text-xs shrink-0 shadow-md">AI</div>
                  <div class="rounded-2xl rounded-tl-sm border border-slate-200 bg-white px-6 py-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
                    <div class="markdown-body text-slate-700 dark:text-slate-200 text-sm leading-relaxed" v-html="renderMarkdown(msg.content)"></div>
                  </div>
                </div>

                <div v-else class="flex items-end space-x-2 max-w-[80%]">
                  <div class="bg-green-500 text-white rounded-2xl rounded-br-sm shadow-md px-5 py-3">
                    <p class="text-sm leading-relaxed whitespace-pre-wrap">{{ msg.content }}</p>
                  </div>
                </div>
              </div>

              <div v-if="isSending" class="flex items-start space-x-3 max-w-[90%]">
                <div class="w-8 h-8 rounded-xl bg-slate-900 text-white flex items-center justify-center text-xs shrink-0 shadow-md">AI</div>
                <div class="flex space-x-1 rounded-2xl rounded-tl-sm border border-slate-200 bg-white px-6 py-5 shadow-sm dark:border-slate-700 dark:bg-slate-900">
                  <span class="h-2 w-2 animate-bounce rounded-full bg-slate-300 dark:bg-slate-600" style="animation-delay:0ms"></span>
                  <span class="h-2 w-2 animate-bounce rounded-full bg-slate-300 dark:bg-slate-600" style="animation-delay:150ms"></span>
                  <span class="h-2 w-2 animate-bounce rounded-full bg-slate-300 dark:bg-slate-600" style="animation-delay:300ms"></span>
                </div>
              </div>
            </template>
          </div>

          <div class="shrink-0 p-4 bg-white dark:bg-slate-900 border-t border-slate-100 dark:border-slate-800">
            <form @submit.prevent="sendMessage" class="relative">
              <input
                v-model="userInput"
                type="text"
                placeholder="围绕这次诊断结果继续追问..."
                :disabled="isSending || !detectionResult.finalized"
                class="w-full bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 text-slate-800 dark:text-slate-100 rounded-2xl pl-5 pr-14 py-4 font-medium focus:outline-none focus:border-green-400 focus:ring-4 focus:ring-green-400/10 transition-all"
              />
              <button
                type="submit"
                :disabled="!detectionResult.finalized || !userInput.trim() || isSending"
                class="absolute right-2 top-2 bottom-2 aspect-square rounded-xl bg-slate-900 text-white shadow-md transition-all hover:bg-black disabled:bg-slate-300 disabled:text-slate-500 dark:bg-emerald-500 dark:text-slate-950 dark:hover:bg-emerald-400 dark:disabled:bg-slate-700 dark:disabled:text-slate-500"
              >
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
</style>
