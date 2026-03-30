<script setup>
import { computed, reactive, ref, watch } from 'vue'
import axios from 'axios'
import { useFarmStore } from '../../stores/farmCloud'
import { cropPhenologyMap, getDefaultStage } from '../../constants/farmCatalog'

const props = defineProps({
  title: {
    type: String,
    default: '我的农场'
  },
  subtitle: {
    type: String,
    default: '作物、地区与物候期会同步到云端，用于识别记录和风险预测联动。'
  },
  compact: {
    type: Boolean,
    default: false
  }
})

const farmStore = useFarmStore()

const isModalOpen = ref(false)
const isSubmitting = ref(false)
const isSearching = ref(false)
const searchResults = ref([])
const formError = ref('')
const editingCropId = ref(null)
const isEstimating = ref(false)
const estimatePreview = ref(null)
const estimateWarnings = ref([])

const form = reactive({
  cropName: '',
  stageMode: 'AUTO',
  stage: '',
  sowingDate: '',
  transplantDate: '',
  locationQuery: '',
  selectedLocation: null
})

const syncModeLabel = computed(() => {
  switch (farmStore.syncMode) {
    case 'cloud':
      return '云端同步'
    case 'cloud-fallback':
      return '云端兜底'
    default:
      return '本地模式'
  }
})

const syncModeClass = computed(() => {
  if (farmStore.syncMode === 'cloud') {
    return 'bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-300'
  }
  if (farmStore.syncMode === 'cloud-fallback') {
    return 'bg-amber-50 text-amber-600 dark:bg-amber-500/10 dark:text-amber-300'
  }
  return 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300'
})

const coveredAreaCount = computed(() => {
  return new Set(farmStore.crops.map(crop => crop.region || crop.city || crop.name)).size
})

const availableCrops = computed(() => {
  const added = new Set(farmStore.crops.map(crop => crop.name))
  return farmStore.cropLibrary.filter(crop => !added.has(crop.name))
})

const isEditing = computed(() => editingCropId.value !== null)
const isAutoMode = computed(() => form.stageMode === 'AUTO')
const showTransplantDate = computed(() => form.cropName === '水稻')

const availableStages = computed(() => {
  if (!form.cropName) return []
  return cropPhenologyMap[form.cropName] || [getDefaultStage(form.cropName)]
})

const estimateConfidenceText = computed(() => {
  const confidence = estimatePreview.value?.confidence
  if (confidence == null) return ''
  return `${Math.round(confidence * 100)}%`
})

const resetForm = () => {
  editingCropId.value = null
  form.cropName = ''
  form.stageMode = 'AUTO'
  form.stage = ''
  form.sowingDate = ''
  form.transplantDate = ''
  form.locationQuery = ''
  form.selectedLocation = null
  formError.value = ''
  searchResults.value = []
  estimatePreview.value = null
  estimateWarnings.value = []
}

const openCreateModal = () => {
  resetForm()
  const firstCrop = availableCrops.value[0]
  form.cropName = firstCrop?.name || ''
  form.stage = firstCrop ? getDefaultStage(firstCrop.name) : ''
  form.stageMode = 'AUTO'
  isModalOpen.value = true
}

const openEditModal = (crop) => {
  resetForm()
  editingCropId.value = crop.id
  form.cropName = crop.name
  form.stageMode = crop.stageMode || 'MANUAL'
  form.stage = crop.stage || getDefaultStage(crop.name)
  form.sowingDate = crop.sowingDate || ''
  form.transplantDate = crop.transplantDate || ''
  form.locationQuery = [crop.province, crop.city].filter(Boolean).join(' ')
  form.selectedLocation = {
    id: crop.locationId,
    province: crop.province,
    city: crop.city,
    region: crop.region
  }
  estimatePreview.value = crop.estimatedStage
    ? {
        supported: true,
        estimatedStage: crop.estimatedStage,
        confidence: crop.stageConfidence,
        reason: crop.stageReason,
        warnings: []
      }
    : null
  isModalOpen.value = true
}

const closeModal = () => {
  isModalOpen.value = false
  resetForm()
}

const handleCropChange = (cropName) => {
  form.cropName = cropName
  form.stage = getDefaultStage(cropName)
  if (cropName !== '水稻') {
    form.transplantDate = ''
  }
  estimatePreview.value = null
  estimateWarnings.value = []
}

const searchLocation = async () => {
  const query = form.locationQuery.trim()
  if (!query) return

  isSearching.value = true
  formError.value = ''
  searchResults.value = []

  try {
    const response = await axios.get(`/api/location/search?query=${encodeURIComponent(query)}`)
    searchResults.value = response.data || []
    if (!searchResults.value.length) {
      formError.value = '未找到匹配地区，请尝试更换城市名称。'
    }
  } catch (error) {
    console.error('Failed to search location', error)
    formError.value = '地区搜索失败，请稍后重试。'
  } finally {
    isSearching.value = false
  }
}

const selectLocation = (location) => {
  form.selectedLocation = location
  form.locationQuery = [location.province, location.city].filter(Boolean).join(' ')
  searchResults.value = []
}

const buildEstimatePayload = () => {
  if (!form.cropName || !form.selectedLocation) {
    return null
  }
  return {
    cropName: form.cropName,
    province: form.selectedLocation.province || '',
    region: form.selectedLocation.region || '',
    locationId: String(form.selectedLocation.id || ''),
    sowingDate: form.sowingDate || null,
    transplantDate: showTransplantDate.value ? (form.transplantDate || null) : null,
    targetDate: new Date().toISOString().split('T')[0]
  }
}

const runEstimate = async () => {
  if (!isAutoMode.value) {
    estimatePreview.value = null
    estimateWarnings.value = []
    return
  }

  const payload = buildEstimatePayload()
  if (!payload) {
    estimatePreview.value = null
    estimateWarnings.value = []
    return
  }

  isEstimating.value = true
  formError.value = ''
  try {
    const result = await farmStore.estimatePhenology(payload)
    estimatePreview.value = result
    estimateWarnings.value = result?.warnings || []
  } catch (error) {
    console.error('Failed to estimate phenology', error)
    estimatePreview.value = null
    estimateWarnings.value = []
    formError.value = '系统暂时无法自动判断物候期，请稍后再试。'
  } finally {
    isEstimating.value = false
  }
}

watch(
  () => [form.cropName, form.stageMode, form.sowingDate, form.transplantDate, form.selectedLocation?.id, form.selectedLocation?.province, form.selectedLocation?.region],
  () => {
    runEstimate()
  }
)

watch(
  () => form.stageMode,
  (mode) => {
    if (mode === 'MANUAL' && !form.stage) {
      form.stage = availableStages.value[0] || ''
    }
    if (mode === 'AUTO') {
      runEstimate()
    }
  }
)

const saveCrop = async () => {
  if (!form.cropName || !form.selectedLocation) {
    formError.value = '请先选择作物和地区。'
    return
  }

  if (isAutoMode.value) {
    if (!estimatePreview.value?.estimatedStage) {
      formError.value = estimatePreview.value?.reason || '当前条件不足以自动判断物候期，请补充关键日期或切换为手动设置。'
      return
    }
  } else if (!form.stage) {
    formError.value = '手动模式下必须选择物候期。'
    return
  }

  isSubmitting.value = true
  formError.value = ''

  const payload = {
    stageMode: form.stageMode,
    stage: isAutoMode.value ? '' : form.stage,
    sowingDate: form.sowingDate || null,
    transplantDate: showTransplantDate.value ? (form.transplantDate || null) : null,
    estimatedStage: estimatePreview.value?.estimatedStage || '',
    stageConfidence: estimatePreview.value?.confidence ?? null,
    stageReason: estimatePreview.value?.reason || '',
    stageEvaluatedAt: estimatePreview.value ? new Date().toISOString() : null,
    province: form.selectedLocation.province,
    city: form.selectedLocation.city,
    region: form.selectedLocation.region,
    locationId: String(form.selectedLocation.id)
  }

  try {
    if (isEditing.value) {
      await farmStore.updateCrop(editingCropId.value, payload)
    } else {
      await farmStore.addCrop(form.cropName, form.selectedLocation, payload)
    }
    closeModal()
  } catch (error) {
    console.error('Failed to save crop', error)
    formError.value = farmStore.syncError || '保存作物失败，请稍后重试。'
  } finally {
    isSubmitting.value = false
  }
}

const deleteCrop = async (crop) => {
  const confirmed = window.confirm(`确定删除 ${crop.name} 吗？`)
  if (!confirmed) return

  try {
    await farmStore.removeCrop(crop.id)
    if (editingCropId.value === crop.id) {
      closeModal()
    }
  } catch (error) {
    console.error('Failed to delete crop', error)
    formError.value = farmStore.syncError || '删除作物失败，请稍后重试。'
  }
}

const setCurrentCrop = async (cropId) => {
  try {
    await farmStore.setActiveCrop(cropId)
  } catch (error) {
    console.error('Failed to set active crop', error)
  }
}
</script>

<template>
  <section
    class="pc-farm-shell rounded-[2rem] border border-slate-100 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900"
    :class="compact ? 'p-5' : 'p-6'"
  >
    <div class="flex flex-col gap-5 xl:flex-row xl:items-start xl:justify-between">
      <div>
        <div class="flex flex-wrap items-center gap-3">
          <h3 class="text-2xl font-black text-slate-900 dark:text-slate-100">{{ title }}</h3>
          <span class="rounded-full px-3 py-1 text-xs font-bold" :class="syncModeClass">
            {{ syncModeLabel }}
          </span>
        </div>
        <p class="mt-2 max-w-3xl text-sm text-slate-500 dark:text-slate-400">{{ subtitle }}</p>
      </div>

      <button
        class="inline-flex items-center justify-center rounded-2xl bg-slate-900 px-5 py-3 text-sm font-bold text-white transition hover:bg-slate-800 dark:bg-emerald-400 dark:text-slate-950 dark:hover:bg-emerald-300"
        :disabled="!availableCrops.length"
        @click="openCreateModal"
      >
        {{ availableCrops.length ? '新增作物' : '作物已齐全' }}
      </button>
    </div>

    <div class="mt-5 grid grid-cols-1 gap-3 md:grid-cols-3">
      <article class="pc-farm-soft rounded-[1.5rem] border border-slate-100 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-950/50">
        <div class="text-xs font-bold uppercase tracking-[0.18em] text-slate-400">作物档案</div>
        <div class="mt-2.5 text-[1.7rem] font-black text-slate-900 dark:text-slate-100">{{ farmStore.crops.length }}</div>
      </article>
      <article class="pc-farm-soft rounded-[1.5rem] border border-slate-100 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-950/50">
        <div class="text-xs font-bold uppercase tracking-[0.18em] text-slate-400">覆盖区域</div>
        <div class="mt-2.5 text-[1.7rem] font-black text-slate-900 dark:text-slate-100">{{ coveredAreaCount }}</div>
      </article>
      <article class="pc-farm-soft rounded-[1.5rem] border border-slate-100 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-950/50">
        <div class="text-xs font-bold uppercase tracking-[0.18em] text-slate-400">当前作物</div>
        <div class="mt-2.5 text-[1.7rem] font-black text-slate-900 dark:text-slate-100">{{ farmStore.activeCrop?.name || '未设置' }}</div>
      </article>
    </div>

    <div
      v-if="farmStore.syncError"
      class="mt-5 rounded-3xl border border-amber-200 bg-amber-50 px-5 py-4 text-sm text-amber-700 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-200"
    >
      {{ farmStore.syncError }}
    </div>

    <div
      v-if="farmStore.crops.length === 0"
      class="pc-farm-soft mt-6 rounded-[2rem] border border-dashed border-slate-200 bg-slate-50 px-6 py-16 text-center dark:border-slate-700 dark:bg-slate-950/50"
    >
      <div class="text-4xl">🌱</div>
      <div class="mt-4 text-lg font-bold text-slate-800 dark:text-slate-100">还没有作物档案</div>
      <p class="mx-auto mt-2 max-w-xl text-sm text-slate-500 dark:text-slate-400">
        先补充作物、地区与物候期，识别记录和数据中心才会按作物联动展示。
      </p>
    </div>

    <div v-else class="mt-5 grid gap-3" :class="compact ? 'grid-cols-2 xl:grid-cols-3' : 'grid-cols-2 md:grid-cols-3 lg:grid-cols-4'">
      <article
        v-for="crop in farmStore.crops"
        :key="crop.id"
        class="pc-farm-card flex flex-col justify-between rounded-2xl border border-slate-100 bg-white p-3 shadow-sm transition dark:border-slate-800 dark:bg-slate-950/50"
      >
        <div>
          <div class="flex items-start justify-between gap-2">
            <h4 class="truncate text-base font-black leading-none text-slate-900 dark:text-slate-100">
              {{ crop.name }}
            </h4>
            <div class="flex shrink-0 items-center gap-1">
              <button
                class="whitespace-nowrap rounded border border-slate-200 px-1.5 py-1 text-[9px] font-bold text-slate-600 transition hover:border-slate-300 hover:text-slate-900 dark:border-slate-700 dark:text-slate-200 dark:hover:border-slate-600"
                @click="openEditModal(crop)"
              >
                编辑
              </button>
              <button
                class="whitespace-nowrap rounded border border-rose-200 px-1.5 py-1 text-[9px] font-bold text-rose-500 transition hover:bg-rose-50 dark:border-rose-500/30 dark:hover:bg-rose-500/10"
                @click="deleteCrop(crop)"
              >
                删除
              </button>
            </div>
          </div>
          <div class="mt-2.5 flex flex-col items-start gap-1.5">
            <span
              class="shrink-0 whitespace-nowrap rounded-full px-2 py-0.5 text-[10px] font-bold"
              :class="String(farmStore.activeCropId) === String(crop.id)
                ? 'bg-emerald-50 text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-300'
                : 'bg-slate-100 text-slate-500 dark:bg-slate-800 dark:text-slate-300'"
            >
              {{ String(farmStore.activeCropId) === String(crop.id) ? '当前作物' : '已建档' }}
            </span>
            <span class="line-clamp-2 text-[11px] leading-snug text-slate-500 dark:text-slate-400">
              {{ [crop.province, crop.city, crop.region].filter(Boolean).join(' · ') || '未设置地区' }}
            </span>
            <div class="flex flex-wrap gap-1">
              <span class="rounded-full bg-slate-100 px-2 py-0.5 text-[10px] font-bold text-slate-500 dark:bg-slate-800 dark:text-slate-300">
                {{ crop.stageMode === 'AUTO' ? '自动' : '手动' }}
              </span>
              <span class="rounded-full bg-emerald-50 px-2 py-0.5 text-[10px] font-bold text-emerald-600 dark:bg-emerald-500/10 dark:text-emerald-300">
                {{ crop.stage || crop.estimatedStage || '待判断' }}
              </span>
            </div>
          </div>
        </div>

        <div class="mt-3">
          <button
            class="w-full whitespace-nowrap rounded-lg px-2.5 py-1.5 text-[11px] font-bold transition"
            :class="String(farmStore.activeCropId) === String(crop.id)
              ? 'bg-emerald-500 text-white shadow-lg shadow-emerald-500/20'
              : 'bg-slate-900 text-white hover:bg-slate-800 dark:bg-emerald-400 dark:text-slate-950 dark:hover:bg-emerald-300'"
            @click="setCurrentCrop(crop.id)"
          >
            {{ String(farmStore.activeCropId) === String(crop.id) ? '已选为当前' : '设为当前' }}
          </button>
        </div>
      </article>
    </div>
  </section>

  <Transition
    enter-active-class="transition duration-200 ease-out"
    enter-from-class="opacity-0"
    enter-to-class="opacity-100"
    leave-active-class="transition duration-150 ease-in"
    leave-from-class="opacity-100"
    leave-to-class="opacity-0"
  >
    <div v-if="isModalOpen" class="fixed inset-0 z-50 flex items-center justify-center px-4">
      <div class="absolute inset-0 bg-slate-950/[0.55] backdrop-blur-sm" @click="closeModal"></div>
      <div class="pc-farm-modal relative z-10 w-full max-w-2xl rounded-[2rem] border border-slate-200 bg-white p-7 shadow-2xl dark:border-slate-700 dark:bg-slate-900">
        <div class="flex items-start justify-between gap-4">
          <div>
            <h4 class="text-2xl font-black text-slate-900 dark:text-slate-100">
              {{ isEditing ? '编辑作物档案' : '新增作物档案' }}
            </h4>
            <p class="mt-2 text-sm text-slate-500 dark:text-slate-400">
              支持自动判断或手动设置物候期，系统会按地区和关键日期生成当前阶段。
            </p>
          </div>
          <button class="rounded-full bg-slate-100 px-3 py-2 text-xs font-bold text-slate-500 dark:bg-slate-800 dark:text-slate-300" @click="closeModal">
            关闭
          </button>
        </div>

        <div class="mt-6 grid grid-cols-1 gap-5 md:grid-cols-2">
          <label class="block">
            <div class="mb-2 text-xs font-bold uppercase tracking-[0.16em] text-slate-400">作物</div>
            <select
              class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm font-medium outline-none transition focus:border-emerald-400 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
              :disabled="isEditing"
              :value="form.cropName"
              @change="handleCropChange($event.target.value)"
            >
              <option v-for="crop in (isEditing ? farmStore.crops : availableCrops)" :key="crop.name" :value="crop.name">
                {{ crop.icon }} {{ crop.name }}
              </option>
            </select>
          </label>

          <label class="block">
            <div class="mb-2 text-xs font-bold uppercase tracking-[0.16em] text-slate-400">物候来源</div>
            <select
              v-model="form.stageMode"
              class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm font-medium outline-none transition focus:border-emerald-400 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
            >
              <option value="AUTO">自动判断</option>
              <option value="MANUAL">手动设置</option>
            </select>
          </label>
        </div>

        <div class="mt-5 grid grid-cols-1 gap-5 md:grid-cols-2">
          <label class="block">
            <div class="mb-2 text-xs font-bold uppercase tracking-[0.16em] text-slate-400">播种日期</div>
            <input
              v-model="form.sowingDate"
              type="date"
              class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm font-medium outline-none transition focus:border-emerald-400 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
            >
          </label>

          <label v-if="showTransplantDate" class="block">
            <div class="mb-2 text-xs font-bold uppercase tracking-[0.16em] text-slate-400">移栽日期</div>
            <input
              v-model="form.transplantDate"
              type="date"
              class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm font-medium outline-none transition focus:border-emerald-400 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
            >
          </label>

          <label v-else class="block">
            <div class="mb-2 text-xs font-bold uppercase tracking-[0.16em] text-slate-400">当前生效物候期</div>
            <div class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm font-medium text-slate-500 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-300">
              {{ isAutoMode ? (estimatePreview?.estimatedStage || '等待系统判断') : (form.stage || '请选择物候期') }}
            </div>
          </label>
        </div>

        <label v-if="!isAutoMode" class="mt-5 block">
          <div class="mb-2 text-xs font-bold uppercase tracking-[0.16em] text-slate-400">手动物候期</div>
          <select
            v-model="form.stage"
            class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm font-medium outline-none transition focus:border-emerald-400 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
          >
            <option value="" disabled>请选择物候期</option>
            <option v-for="stage in availableStages" :key="stage" :value="stage">{{ stage }}</option>
          </select>
        </label>

        <div class="mt-5">
          <div class="mb-2 text-xs font-bold uppercase tracking-[0.16em] text-slate-400">农场所在地</div>
          <div class="flex gap-3">
            <input
              v-model="form.locationQuery"
              type="text"
              class="flex-1 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3 text-sm font-medium outline-none transition focus:border-emerald-400 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
              placeholder="输入城市名，例如 北京 或 朝阳"
              @keyup.enter="searchLocation"
            >
            <button
              class="rounded-2xl bg-slate-900 px-5 py-3 text-sm font-bold text-white transition hover:bg-slate-800 dark:bg-emerald-400 dark:text-slate-950 dark:hover:bg-emerald-300"
              @click="searchLocation"
            >
              搜索
            </button>
          </div>

          <div v-if="isSearching" class="mt-3 text-xs font-bold text-slate-400">正在检索地区...</div>

          <div v-if="searchResults.length" class="mt-3 overflow-hidden rounded-3xl border border-slate-100 bg-slate-50 dark:border-slate-800 dark:bg-slate-950/50">
            <button
              v-for="location in searchResults"
              :key="location.id"
              class="flex w-full items-center justify-between gap-4 border-b border-slate-100 px-4 py-3 text-left last:border-b-0 hover:bg-white dark:border-slate-800 dark:hover:bg-slate-900"
              @click="selectLocation(location)"
            >
              <div>
                <div class="text-sm font-bold text-slate-800 dark:text-slate-100">{{ location.province }} {{ location.city }}</div>
                <div class="mt-1 text-xs text-slate-400">区域编码 {{ location.id }}</div>
              </div>
              <span class="rounded-full bg-sky-50 px-3 py-1 text-xs font-bold text-sky-600 dark:bg-sky-500/10 dark:text-sky-300">
                {{ location.region }}
              </span>
            </button>
          </div>

          <div
            v-if="form.selectedLocation && !searchResults.length"
            class="mt-4 rounded-3xl border border-emerald-100 bg-emerald-50 px-4 py-4 dark:border-emerald-500/20 dark:bg-emerald-500/10"
          >
            <div class="text-xs font-bold uppercase tracking-[0.16em] text-emerald-600 dark:text-emerald-300">已选择位置</div>
            <div class="mt-2 text-base font-black text-slate-900 dark:text-slate-100">
              {{ form.selectedLocation.province }} {{ form.selectedLocation.city }}
            </div>
            <div class="mt-1 text-sm text-slate-500 dark:text-slate-400">
              {{ form.selectedLocation.region }} · {{ form.selectedLocation.id }}
            </div>
          </div>

          <div
            v-if="isAutoMode"
            class="mt-4 rounded-3xl border border-sky-100 bg-sky-50 px-4 py-4 dark:border-sky-500/20 dark:bg-sky-500/10"
          >
            <div class="flex items-center justify-between gap-3">
              <div>
                <div class="text-xs font-bold uppercase tracking-[0.16em] text-sky-600 dark:text-sky-300">自动物候判断</div>
                <div class="mt-2 text-base font-black text-slate-900 dark:text-slate-100">
                  {{ isEstimating ? '正在判断…' : (estimatePreview?.estimatedStage || '等待输入完整条件') }}
                </div>
                <div class="mt-1 text-sm text-slate-500 dark:text-slate-400">
                  {{ estimatePreview?.reason || '系统会根据地区、关键日期和近 7 天均温估算当前阶段。' }}
                </div>
              </div>
              <div v-if="estimateConfidenceText" class="rounded-full bg-white px-3 py-1 text-xs font-bold text-sky-600 shadow-sm dark:bg-slate-900 dark:text-sky-300">
                {{ estimateConfidenceText }}
              </div>
            </div>
            <ul v-if="estimateWarnings.length" class="mt-3 space-y-1 text-xs text-amber-700 dark:text-amber-200">
              <li v-for="warning in estimateWarnings" :key="warning">- {{ warning }}</li>
            </ul>
          </div>
        </div>

        <div
          v-if="formError"
          class="mt-5 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600 dark:border-rose-500/20 dark:bg-rose-500/10 dark:text-rose-200"
        >
          {{ formError }}
        </div>

        <div class="mt-6 flex justify-end gap-3">
          <button
            class="rounded-2xl border border-slate-200 px-5 py-3 text-sm font-bold text-slate-600 transition hover:bg-slate-50 dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800"
            @click="closeModal"
          >
            取消
          </button>
          <button
            class="rounded-2xl bg-slate-900 px-5 py-3 text-sm font-bold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-emerald-400 dark:text-slate-950 dark:hover:bg-emerald-300"
            :disabled="isSubmitting"
            @click="saveCrop"
          >
            {{ isSubmitting ? '保存中...' : (isEditing ? '保存修改' : '确认新增') }}
          </button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.pc-farm-shell {
  background: rgba(255, 255, 255, 0.96);
  border-color: rgba(226, 232, 240, 0.92);
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.08);
}

.pc-farm-card {
  background: rgba(255, 255, 255, 0.96);
  border-color: rgba(226, 232, 240, 0.92);
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.08);
}

.pc-farm-soft {
  background: rgba(248, 250, 252, 0.96);
  border-color: rgba(226, 232, 240, 0.9);
}

.pc-farm-modal {
  background: rgba(255, 255, 255, 0.98);
  border-color: rgba(226, 232, 240, 0.92);
}

:global(.dark .pc-farm-shell) {
  background: rgba(15, 23, 42, 0.92);
  border-color: rgba(51, 65, 85, 0.88);
  box-shadow: 0 22px 52px rgba(2, 6, 23, 0.3);
}

:global(.dark .pc-farm-card) {
  background: rgba(2, 6, 23, 0.55);
  border-color: rgba(51, 65, 85, 0.88);
  box-shadow: 0 20px 48px rgba(2, 6, 23, 0.28);
}

:global(.dark .pc-farm-soft) {
  background: rgba(15, 23, 42, 0.78);
  border-color: rgba(51, 65, 85, 0.82);
}

:global(.dark .pc-farm-modal) {
  background: rgba(15, 23, 42, 0.96);
  border-color: rgba(51, 65, 85, 0.88);
}
</style>
