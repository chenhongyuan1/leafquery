<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useFarmStore } from '../../stores/farmCloud'
import axios from 'axios'
import { cropPhenologyMap, getDefaultStage } from '../../constants/farmCatalog'

const router = useRouter()
const farmStore = useFarmStore()

const showAddModal = ref(false)
const selectedNewCrop = ref(null)
const selectedStageMode = ref('AUTO')
const selectedStage = ref('')
const selectedSowingDate = ref('')
const selectedTransplantDate = ref('')
const addEstimate = ref(null)
const addEstimateWarnings = ref([])
const isAddEstimating = ref(false)

const editingCrop = ref(null)
const editEstimate = ref(null)
const editEstimateWarnings = ref([])
const isEditEstimating = ref(false)

const locationQuery = ref('')
const searchResults = ref([])
const isSearching = ref(false)
const selectedLocation = ref(null)
const formError = ref('')
const isSaving = ref(false)

let addEstimateRequestId = 0
let editEstimateRequestId = 0

const availableCrops = computed(() => {
  const added = new Set(farmStore.crops.map(crop => crop.name))
  return farmStore.cropLibrary.filter(crop => !added.has(crop.name))
})

const coveredAreaCount = computed(() => {
  return new Set(farmStore.crops.map(crop => crop.region).filter(Boolean)).size
})

const currentCropName = computed(() => {
  return selectedNewCrop.value?.name || editingCrop.value?.name || ''
})

const availableStages = computed(() => {
  if (!currentCropName.value) return []
  return cropPhenologyMap[currentCropName.value] || [getDefaultStage(currentCropName.value)]
})

const showTransplantDate = computed(() => currentCropName.value === '水稻')

const canSubmitAdd = computed(() => {
  if (!selectedNewCrop.value || !selectedLocation.value || isSaving.value) return false
  if (selectedStageMode.value === 'MANUAL') return Boolean(selectedStage.value)
  return Boolean(addEstimate.value?.estimatedStage) && !isAddEstimating.value
})

const canSubmitEdit = computed(() => {
  if (!editingCrop.value || !selectedLocation.value || isSaving.value) return false
  if (editingCrop.value.stageMode === 'MANUAL') return Boolean(editingCrop.value.stage)
  return Boolean(editEstimate.value?.estimatedStage) && !isEditEstimating.value
})

const resetFormError = () => {
  formError.value = ''
}

const clearLocationState = () => {
  locationQuery.value = ''
  searchResults.value = []
  selectedLocation.value = null
}

const clearAddEstimate = () => {
  addEstimateRequestId += 1
  addEstimate.value = null
  addEstimateWarnings.value = []
  isAddEstimating.value = false
}

const clearEditEstimate = () => {
  editEstimateRequestId += 1
  editEstimate.value = null
  editEstimateWarnings.value = []
  isEditEstimating.value = false
}

const closeAddModal = () => {
  showAddModal.value = false
  selectedNewCrop.value = null
  selectedStageMode.value = 'AUTO'
  selectedStage.value = ''
  selectedSowingDate.value = ''
  selectedTransplantDate.value = ''
  clearLocationState()
  clearAddEstimate()
  resetFormError()
}

const closeEditModal = () => {
  editingCrop.value = null
  clearLocationState()
  clearEditEstimate()
  resetFormError()
}

const getTodayString = () => {
  const now = new Date()
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60000)
  return local.toISOString().slice(0, 10)
}

const formatEstimateConfidence = (estimate) => {
  if (estimate?.confidence == null) return ''
  return `${Math.round(Number(estimate.confidence) * 100)}%`
}

const getEstimateStageText = (estimate, isEstimating) => {
  if (isEstimating) return '正在判断...'
  return estimate?.estimatedStage || '等待补全条件'
}

const getEstimateReasonText = (estimate) => {
  return estimate?.reason || '系统会根据地区、关键日期和近 7 天均温估算当前阶段。'
}

const getStageLabel = (crop) => {
  return crop.stage || crop.estimatedStage || '待判断'
}

const buildEstimatePayload = ({ cropName, location, sowingDate, transplantDate }) => {
  if (!cropName || !location) {
    return null
  }

  return {
    cropName,
    province: location.province || '',
    region: location.region || '',
    locationId: String(location.id ?? location.locationId ?? ''),
    sowingDate: sowingDate || null,
    transplantDate: cropName === '水稻' ? (transplantDate || null) : null,
    targetDate: getTodayString()
  }
}

const runAddEstimate = async () => {
  if (!showAddModal.value || selectedStageMode.value !== 'AUTO') {
    clearAddEstimate()
    return
  }

  const payload = buildEstimatePayload({
    cropName: selectedNewCrop.value?.name || '',
    location: selectedLocation.value,
    sowingDate: selectedSowingDate.value,
    transplantDate: selectedTransplantDate.value
  })

  if (!payload) {
    clearAddEstimate()
    return
  }

  const requestId = ++addEstimateRequestId
  isAddEstimating.value = true
  resetFormError()

  try {
    const result = await farmStore.estimatePhenology(payload)
    if (requestId !== addEstimateRequestId) return
    addEstimate.value = result
    addEstimateWarnings.value = Array.isArray(result?.warnings) ? result.warnings.filter(Boolean) : []
  } catch (error) {
    if (requestId !== addEstimateRequestId) return
    console.error('Failed to estimate phenology for add modal', error)
    addEstimate.value = null
    addEstimateWarnings.value = []
    formError.value = '系统暂时无法自动判断物候期，请稍后再试。'
  } finally {
    if (requestId === addEstimateRequestId) {
      isAddEstimating.value = false
    }
  }
}

const runEditEstimate = async () => {
  if (!editingCrop.value || editingCrop.value.stageMode !== 'AUTO') {
    clearEditEstimate()
    return
  }

  const payload = buildEstimatePayload({
    cropName: editingCrop.value.name,
    location: selectedLocation.value,
    sowingDate: editingCrop.value.sowingDate,
    transplantDate: editingCrop.value.transplantDate
  })

  if (!payload) {
    clearEditEstimate()
    return
  }

  const requestId = ++editEstimateRequestId
  isEditEstimating.value = true
  resetFormError()
  try {
    const result = await farmStore.estimatePhenology(payload)
    if (requestId !== editEstimateRequestId) return
    editEstimate.value = result
    editEstimateWarnings.value = Array.isArray(result?.warnings) ? result.warnings.filter(Boolean) : []
  } catch (error) {
    if (requestId !== editEstimateRequestId) return
    console.error('Failed to estimate phenology for edit modal', error)
    editEstimate.value = null
    editEstimateWarnings.value = []
    formError.value = '系统暂时无法自动判断物候期，请稍后再试。'
  } finally {
    if (requestId === editEstimateRequestId) {
      isEditEstimating.value = false
    }
  }
}

const openAddModal = () => {
  closeEditModal()
  showAddModal.value = true
}

const selectNewCrop = (crop) => {
  selectedNewCrop.value = crop
  selectedStage.value = getDefaultStage(crop.name)
  if (crop.name !== '水稻') {
    selectedTransplantDate.value = ''
  }
  resetFormError()
}

const openEdit = (crop) => {
  closeAddModal()
  editingCrop.value = {
    ...crop,
    stageMode: crop.stageMode || 'MANUAL',
    stage: crop.stage || crop.estimatedStage || getDefaultStage(crop.name),
    sowingDate: crop.sowingDate || '',
    transplantDate: crop.transplantDate || ''
  }
  selectedLocation.value = {
    id: crop.locationId,
    province: crop.province,
    city: crop.city,
    region: crop.region
  }
  locationQuery.value = [crop.province, crop.city].filter(Boolean).join(' ')
  searchResults.value = []
  editEstimate.value = crop.estimatedStage
    ? {
        supported: true,
        estimatedStage: crop.estimatedStage,
        confidence: crop.stageConfidence,
        reason: crop.stageReason,
        warnings: []
      }
    : null
  editEstimateWarnings.value = []
  resetFormError()
}

const confirmAdd = async () => {
  if (!selectedNewCrop.value || !selectedLocation.value) {
    formError.value = '请先选择作物和农场所在地。'
    return
  }

  if (selectedStageMode.value === 'AUTO') {
    if (!addEstimate.value?.estimatedStage) {
      formError.value = addEstimate.value?.reason || '当前条件不足以自动判断物候期，请补充关键日期或改为手动设置。'
      return
    }
  } else if (!selectedStage.value) {
    formError.value = '手动模式下请选择当前物候期。'
    return
  }

  isSaving.value = true
  resetFormError()

  try {
    const created = await farmStore.addCrop(selectedNewCrop.value.name, selectedLocation.value, {
      stageMode: selectedStageMode.value,
      stage: selectedStageMode.value === 'MANUAL' ? selectedStage.value : '',
      sowingDate: selectedSowingDate.value || null,
      transplantDate: showTransplantDate.value ? (selectedTransplantDate.value || null) : null,
      estimatedStage: addEstimate.value?.estimatedStage || '',
      stageConfidence: addEstimate.value?.confidence ?? null,
      stageReason: addEstimate.value?.reason || '',
      stageEvaluatedAt: addEstimate.value ? new Date().toISOString() : null
    })

    if (!created) {
      formError.value = farmStore.syncError || '保存作物失败，请稍后重试。'
      return
    }

    closeAddModal()
  } catch (error) {
    console.error('Failed to add crop', error)
    formError.value = farmStore.syncError || '保存作物失败，请稍后重试。'
  } finally {
    isSaving.value = false
  }
}

const confirmEdit = async () => {
  if (!editingCrop.value || !selectedLocation.value) {
    formError.value = '请先确认农场所在地。'
    return
  }

  if (editingCrop.value.stageMode === 'AUTO') {
    if (!editEstimate.value?.estimatedStage) {
      formError.value = editEstimate.value?.reason || '当前条件不足以自动判断物候期，请补充关键日期或改为手动设置。'
      return
    }
  } else if (!editingCrop.value.stage) {
    formError.value = '手动模式下请选择当前物候期。'
    return
  }

  isSaving.value = true
  resetFormError()

  try {
    await farmStore.updateCrop(editingCrop.value.id, {
      stageMode: editingCrop.value.stageMode,
      stage: editingCrop.value.stageMode === 'MANUAL' ? editingCrop.value.stage : '',
      sowingDate: editingCrop.value.sowingDate || null,
      transplantDate: editingCrop.value.name === '水稻' ? (editingCrop.value.transplantDate || null) : null,
      estimatedStage: editEstimate.value?.estimatedStage || '',
      stageConfidence: editEstimate.value?.confidence ?? null,
      stageReason: editEstimate.value?.reason || '',
      stageEvaluatedAt: editEstimate.value ? new Date().toISOString() : null,
      city: selectedLocation.value.city,
      province: selectedLocation.value.province,
      region: selectedLocation.value.region,
      locationId: selectedLocation.value.id
    })

    closeEditModal()
  } catch (error) {
    console.error('Failed to update crop', error)
    formError.value = farmStore.syncError || '保存修改失败，请稍后重试。'
  } finally {
    isSaving.value = false
  }
}

const deleteCrop = async (cropId) => {
  isSaving.value = true
  resetFormError()

  try {
    await farmStore.removeCrop(cropId)
    closeEditModal()
  } catch (error) {
    console.error('Failed to delete crop', error)
    formError.value = farmStore.syncError || '删除作物失败，请稍后重试。'
  } finally {
    isSaving.value = false
  }
}

const handleSearchLocation = async () => {
  const query = locationQuery.value.trim()
  if (!query) return

  isSearching.value = true
  searchResults.value = []
  resetFormError()

  try {
    const response = await axios.get(`/api/location/search?query=${encodeURIComponent(query)}`)
    searchResults.value = response.data || []
  } catch (error) {
    console.error('Failed to search location', error)
    formError.value = '地区搜索失败，请稍后再试。'
  } finally {
    isSearching.value = false
  }
}

const selectLocation = (location) => {
  selectedLocation.value = location
  locationQuery.value = [location.province, location.city].filter(Boolean).join(' ')
  searchResults.value = []
  resetFormError()
}

watch(
  () => selectedStageMode.value,
  (mode) => {
    if (mode === 'MANUAL' && !selectedStage.value && selectedNewCrop.value) {
      selectedStage.value = getDefaultStage(selectedNewCrop.value.name)
    }
    if (mode !== 'AUTO') {
      clearAddEstimate()
    }
    resetFormError()
  }
)

watch(
  () => editingCrop.value?.stageMode,
  (mode) => {
    if (mode === 'MANUAL' && editingCrop.value && !editingCrop.value.stage) {
      editingCrop.value.stage = editingCrop.value.estimatedStage || getDefaultStage(editingCrop.value.name)
    }
    if (mode && mode !== 'AUTO') {
      clearEditEstimate()
    }
    resetFormError()
  }
)
watch(
  () => [
    showAddModal.value,
    selectedNewCrop.value?.name,
    selectedStageMode.value,
    selectedSowingDate.value,
    selectedTransplantDate.value,
    selectedLocation.value?.id,
    selectedLocation.value?.province,
    selectedLocation.value?.region
  ],
  () => {
    if (!showAddModal.value || selectedStageMode.value !== 'AUTO') {
      clearAddEstimate()
      return
    }
    void runAddEstimate()
  }
)

watch(
  () => [
    editingCrop.value?.id,
    editingCrop.value?.name,
    editingCrop.value?.stageMode,
    editingCrop.value?.sowingDate,
    editingCrop.value?.transplantDate,
    selectedLocation.value?.id,
    selectedLocation.value?.province,
    selectedLocation.value?.region
  ],
  () => {
    if (!editingCrop.value || editingCrop.value.stageMode !== 'AUTO') {
      clearEditEstimate()
      return
    }
    void runEditEstimate()
  }
)

onMounted(() => {
  farmStore.initialize()
})
</script>

<template>
  <div class="farm-page min-h-full bg-slate-50 pb-8 relative">
    <div class="bg-gradient-to-br from-green-600 to-emerald-700 px-6 pt-4 pb-12 rounded-b-[2.5rem] shadow-xl shadow-green-900/20 relative overflow-hidden">
      <div class="absolute -top-10 -right-10 w-40 h-40 bg-white/10 rounded-full blur-2xl"></div>
      <div class="absolute bottom-0 left-0 w-32 h-32 bg-black/10 rounded-full -ml-10 -mb-10 blur-xl"></div>

      <div class="relative z-10">
        <div class="flex items-center mb-6">
          <button @click="router.back()" class="w-10 h-10 bg-white/20 backdrop-blur-md rounded-xl flex items-center justify-center text-white mr-4 active:scale-95 transition-transform">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <div>
            <h1 class="text-2xl font-bold text-white tracking-tight">我的农场</h1>
            <p class="text-green-100 text-xs font-medium mt-0.5">管理作物档案，联动物候判断与趋势预测</p>
          </div>
        </div>

        <div class="flex space-x-4">
          <div class="flex-1 bg-white/15 backdrop-blur-md rounded-2xl p-4 border border-white/10">
            <div class="text-2xl font-black text-white">{{ farmStore.crops.length }}</div>
            <div class="text-green-100 text-xs font-bold mt-1">种植作物</div>
          </div>
          <div class="flex-1 bg-white/15 backdrop-blur-md rounded-2xl p-4 border border-white/10">
            <div class="text-2xl font-black text-white">{{ farmStore.identificationHistory.length }}</div>
            <div class="text-green-100 text-xs font-bold mt-1">识别记录</div>
          </div>
          <div class="flex-1 bg-white/15 backdrop-blur-md rounded-2xl p-4 border border-white/10">
            <div class="text-2xl font-black text-white">{{ coveredAreaCount }}</div>
            <div class="text-green-100 text-xs font-bold mt-1">覆盖区域</div>
          </div>
        </div>
      </div>
    </div>

    <div class="px-6 -mt-6 relative z-10">
      <div class="space-y-4">
        <div
          v-for="crop in farmStore.crops"
          :key="crop.id"
          class="bg-white rounded-[1.5rem] p-5 shadow-[0_4px_20px_rgb(0,0,0,0.04)] border border-slate-100 active:scale-[0.98] transition-all group cursor-pointer"
          @click="openEdit(crop)"
        >
          <div class="flex items-start justify-between gap-3">
            <div class="flex items-center space-x-4 min-w-0">
              <div class="w-14 h-14 rounded-2xl flex items-center justify-center text-3xl shadow-sm shrink-0" :class="farmStore.activeCropId === crop.id ? 'bg-green-50 ring-2 ring-green-500' : 'bg-slate-50'">
                {{ crop.icon }}
              </div>
              <div class="min-w-0">
                <h3 class="font-bold text-slate-800 text-lg flex items-center space-x-2">
                  <span>{{ crop.name }}</span>
                  <span v-if="farmStore.activeCropId === crop.id" class="text-[10px] font-bold bg-green-100 text-green-600 px-2 py-0.5 rounded-full">当前</span>
                </h3>
                <div class="flex items-center space-x-3 mt-1.5">
                  <span class="text-xs font-medium text-slate-400 bg-slate-50 px-2 py-0.5 rounded-lg">📍 {{ crop.city }} ({{ crop.region }})</span>
                </div>
                <div class="flex flex-wrap gap-2 mt-2">
                  <span
                    class="text-[10px] font-bold px-2 py-1 rounded-lg border"
                    :class="crop.stageMode === 'AUTO' ? 'bg-sky-50 text-sky-600 border-sky-100' : 'bg-slate-100 text-slate-600 border-slate-200'"
                  >
                    {{ crop.stageMode === 'AUTO' ? '自动' : '手动' }}
                  </span>
                  <span class="text-[10px] font-bold px-2 py-1 rounded-lg border border-emerald-100 bg-emerald-50 text-emerald-700">
                    当前阶段 · {{ getStageLabel(crop) }}
                  </span>
                </div>
              </div>
            </div>
            <button
              @click.stop="farmStore.setActiveCrop(crop.id)"
              class="px-3 py-1.5 rounded-xl text-xs font-bold transition-all shrink-0"
              :class="farmStore.activeCropId === crop.id ? 'bg-green-500 text-white shadow-lg shadow-green-500/30' : 'bg-slate-100 text-slate-500 hover:bg-green-50 hover:text-green-600'"
            >
              {{ farmStore.activeCropId === crop.id ? '已选中' : '设为当前' }}
            </button>
          </div>

          <div class="mt-4 pt-3 border-t border-slate-50">
            <div class="text-[10px] font-bold text-slate-400 mb-2 uppercase tracking-wider">重点病害</div>
            <div class="flex flex-wrap gap-2">
              <span
                v-for="disease in crop.diseases"
                :key="disease"
                class="text-[10px] font-medium text-orange-600 bg-orange-50 px-2 py-1 rounded-lg border border-orange-100/50"
              >
                {{ disease }}
              </span>
            </div>
          </div>
        </div>

        <button
          @click="openAddModal"
          :disabled="availableCrops.length === 0"
          class="w-full bg-white rounded-[1.5rem] p-6 shadow-[0_4px_20px_rgb(0,0,0,0.04)] border-2 border-dashed border-slate-200 flex flex-col items-center justify-center space-y-2 hover:border-green-400 hover:bg-green-50/30 transition-all active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <div class="w-12 h-12 bg-green-50 rounded-full flex items-center justify-center text-green-500 text-2xl font-bold">+</div>
          <span class="text-sm font-bold text-slate-500">添加作物</span>
          <span class="text-[10px] text-slate-400">{{ availableCrops.length }} 种可选</span>
        </button>
      </div>
    </div>

    <div v-if="farmStore.crops.length === 0" class="text-center mt-8 px-6 relative z-10">
      <div class="bg-white rounded-[2rem] p-10 shadow-sm border border-slate-100">
        <div class="text-5xl mb-4">🌱</div>
        <h3 class="font-bold text-slate-800 text-lg mb-2">还没有添加作物</h3>
        <p class="text-sm text-slate-400 mb-6">绑定农场所在地后，就可以联动物候判断和趋势预测</p>
        <button @click="openAddModal" class="bg-green-500 text-white px-6 py-3 rounded-xl font-bold shadow-lg shadow-green-500/30 active:scale-95 transition-transform">
          立即添加
        </button>
      </div>
    </div>

    <div v-if="showAddModal" class="fixed inset-0 z-50 flex items-end justify-center">
      <div class="absolute inset-0 bg-slate-900/60 backdrop-blur-sm" @click="closeAddModal"></div>
      <div class="bg-white w-full rounded-t-[2rem] p-6 relative z-10 shadow-2xl animate-slide-up max-h-[90%] flex flex-col">
        <div class="w-12 h-1.5 bg-slate-100 rounded-full mx-auto mb-6 flex-shrink-0"></div>
        <h3 class="text-xl font-bold text-slate-800 mb-6 flex-shrink-0">添加作物</h3>

        <div class="flex-1 overflow-y-auto overflow-x-hidden p-1 min-h-0">
          <label class="text-sm font-bold text-slate-700 mb-3 block">选择作物</label>
          <div class="grid grid-cols-3 gap-3 mb-6">
            <button
              v-for="crop in availableCrops"
              :key="crop.name"
              @click="selectNewCrop(crop)"
              class="flex flex-col items-center py-4 rounded-2xl border-2 transition-all active:scale-95"
              :class="selectedNewCrop?.name === crop.name ? 'border-green-500 bg-green-50 shadow-lg shadow-green-500/20' : 'border-slate-100 bg-white'"
            >
              <span class="text-3xl mb-2">{{ crop.icon }}</span>
              <span class="text-xs font-bold" :class="selectedNewCrop?.name === crop.name ? 'text-green-700' : 'text-slate-600'">{{ crop.name }}</span>
            </button>
          </div>

          <div v-if="selectedNewCrop" class="mb-6 space-y-4">
            <div>
              <label class="text-sm font-bold text-slate-700 mb-2 block">物候来源</label>
              <select v-model="selectedStageMode" class="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-medium outline-none">
                <option value="AUTO">自动判断</option>
                <option value="MANUAL">手动设置</option>
              </select>
            </div>

            <div>
              <label class="text-sm font-bold text-slate-700 mb-2 block">播种日期</label>
              <input v-model="selectedSowingDate" type="date" class="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-medium outline-none">
            </div>

            <div v-if="showTransplantDate">
              <label class="text-sm font-bold text-slate-700 mb-2 block">移栽日期</label>
              <input v-model="selectedTransplantDate" type="date" class="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-medium outline-none">
            </div>

            <div v-if="selectedStageMode === 'MANUAL'">
              <label class="text-sm font-bold text-slate-700 mb-2 block">手动物候期</label>
              <select v-model="selectedStage" class="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-medium outline-none">
                <option value="" disabled>请选择物候期</option>
                <option v-for="stage in availableStages" :key="stage" :value="stage">{{ stage }}</option>
              </select>
            </div>
          </div>

          <div class="mb-6">
            <label class="text-sm font-bold text-slate-700 mb-3 block">农场所在地</label>
            <div class="flex space-x-2">
              <input
                v-model="locationQuery"
                @keyup.enter="handleSearchLocation"
                placeholder="输入城市名称，例如北京、朝阳"
                class="flex-1 px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-medium focus:border-green-500 focus:ring-2 focus:ring-green-100 transition-all outline-none"
              >
              <button
                @click="handleSearchLocation"
                class="px-5 bg-slate-100 text-slate-600 rounded-xl text-sm font-bold hover:bg-slate-200 active:scale-95 transition-all outline-none"
              >
                搜索
              </button>
            </div>

            <div v-if="isSearching" class="text-xs text-slate-400 mt-3 text-center">正在搜索...</div>

            <div v-if="searchResults.length > 0" class="mt-3 bg-white border border-slate-100 shadow-lg rounded-xl overflow-hidden max-h-48 overflow-y-auto">
              <button
                v-for="loc in searchResults"
                :key="loc.id"
                @click="selectLocation(loc)"
                class="w-full text-left px-4 py-3 border-b border-slate-50 last:border-0 hover:bg-slate-50 active:bg-slate-100 transition-colors flex justify-between items-center"
              >
                <div>
                  <div class="text-sm font-bold text-slate-800">{{ loc.province }} {{ loc.city }}</div>
                  <div class="text-[10px] text-slate-400 mt-0.5">地区编码: {{ loc.id }}</div>
                </div>
                <span class="text-[10px] font-bold text-blue-500 bg-blue-50 px-2 py-1 rounded-md">{{ loc.region }}</span>
              </button>
            </div>

            <div v-if="selectedLocation && searchResults.length === 0" class="mt-4 p-4 bg-green-50 border border-green-100 rounded-xl flex items-center justify-between">
              <div>
                <div class="text-xs font-bold text-green-700 mb-0.5">已选择地理位置</div>
                <div class="text-sm font-bold text-slate-800">{{ selectedLocation.province }} {{ selectedLocation.city }}</div>
              </div>
              <div class="flex flex-col items-end">
                <span class="text-[10px] font-bold bg-white text-green-600 px-2 py-1 rounded shadow-sm">{{ selectedLocation.region }}</span>
              </div>
            </div>
          </div>

          <div v-if="selectedNewCrop && selectedStageMode === 'AUTO'" class="mb-6 p-4 rounded-2xl border border-sky-100 bg-sky-50">
            <div class="flex items-start justify-between gap-3">
              <div>
                <div class="text-xs font-bold uppercase tracking-[0.16em] text-sky-600">自动物候判断</div>
                <div class="mt-2 text-lg font-black text-slate-900">{{ getEstimateStageText(addEstimate, isAddEstimating) }}</div>
                <div class="mt-1 text-sm text-slate-500">{{ getEstimateReasonText(addEstimate) }}</div>
              </div>
              <div v-if="formatEstimateConfidence(addEstimate)" class="rounded-full bg-white px-3 py-1 text-xs font-bold text-sky-600 shadow-sm">
                {{ formatEstimateConfidence(addEstimate) }}
              </div>
            </div>
            <ul v-if="addEstimateWarnings.length" class="mt-3 space-y-1 text-xs text-amber-700">
              <li v-for="warning in addEstimateWarnings" :key="warning">- {{ warning }}</li>
            </ul>
          </div>

          <div v-if="formError" class="mb-4 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600">
            {{ formError }}
          </div>
        </div>

        <button
          @click="confirmAdd"
          :disabled="!canSubmitAdd"
          class="w-full bg-slate-900 text-white py-4 rounded-xl font-bold shadow-lg shadow-slate-900/20 active:scale-[0.98] transition-transform disabled:opacity-50 disabled:cursor-not-allowed mt-4 flex-shrink-0"
        >
          {{ isSaving ? '保存中...' : '确认添加' }}
        </button>
      </div>
    </div>

    <div v-if="editingCrop" class="fixed inset-0 z-50 flex items-end justify-center">
      <div class="absolute inset-0 bg-slate-900/60 backdrop-blur-sm" @click="closeEditModal"></div>
      <div class="bg-white w-full rounded-t-[2rem] p-6 relative z-10 shadow-2xl animate-slide-up flex flex-col max-h-[90%]">
        <div class="w-12 h-1.5 bg-slate-100 rounded-full mx-auto mb-6 flex-shrink-0"></div>

        <div class="flex-1 overflow-y-auto p-1 min-h-0">
          <div class="flex items-center space-x-4 mb-6">
            <div class="w-14 h-14 bg-green-50 rounded-2xl flex items-center justify-center text-3xl">{{ editingCrop.icon }}</div>
            <div>
              <h3 class="text-xl font-bold text-slate-800">{{ editingCrop.name }}</h3>
              <p class="text-xs text-slate-400 font-medium">修改物候设置和地理位置</p>
            </div>
          </div>

          <div class="space-y-4 mb-6">
            <div>
              <label class="text-sm font-bold text-slate-700 mb-2 block">物候来源</label>
              <select v-model="editingCrop.stageMode" class="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-medium outline-none">
                <option value="AUTO">自动判断</option>
                <option value="MANUAL">手动设置</option>
              </select>
            </div>

            <div>
              <label class="text-sm font-bold text-slate-700 mb-2 block">播种日期</label>
              <input v-model="editingCrop.sowingDate" type="date" class="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-medium outline-none">
            </div>

            <div v-if="showTransplantDate">
              <label class="text-sm font-bold text-slate-700 mb-2 block">移栽日期</label>
              <input v-model="editingCrop.transplantDate" type="date" class="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-medium outline-none">
            </div>

            <div v-if="editingCrop.stageMode === 'MANUAL'">
              <label class="text-sm font-bold text-slate-700 mb-2 block">手动物候期</label>
              <select v-model="editingCrop.stage" class="w-full px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-medium outline-none">
                <option value="" disabled>请选择物候期</option>
                <option v-for="stage in availableStages" :key="stage" :value="stage">{{ stage }}</option>
              </select>
            </div>
          </div>

          <div class="mb-6">
            <label class="text-sm font-bold text-slate-700 mb-3 block">重新选择所在地</label>
            <div class="flex space-x-2">
              <input
                v-model="locationQuery"
                @keyup.enter="handleSearchLocation"
                placeholder="输入城市名称，例如北京"
                class="flex-1 px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-medium focus:border-green-500 focus:ring-2 focus:ring-green-100 transition-all outline-none"
              >
              <button
                @click="handleSearchLocation"
                class="px-5 bg-slate-100 text-slate-600 rounded-xl text-sm font-bold hover:bg-slate-200 active:scale-95 transition-all outline-none"
              >
                搜索
              </button>
            </div>

            <div v-if="isSearching" class="text-xs text-slate-400 mt-3 text-center">正在搜索...</div>

            <div v-if="searchResults.length > 0" class="mt-3 bg-white border border-slate-100 shadow-lg rounded-xl overflow-hidden max-h-48 overflow-y-auto">
              <button
                v-for="loc in searchResults"
                :key="loc.id"
                @click="selectLocation(loc)"
                class="w-full text-left px-4 py-3 border-b border-slate-50 last:border-0 hover:bg-slate-50 active:bg-slate-100 transition-colors flex justify-between items-center"
              >
                <div>
                  <div class="text-sm font-bold text-slate-800">{{ loc.province }} {{ loc.city }}</div>
                  <div class="text-[10px] text-slate-400 mt-0.5">地区编码: {{ loc.id }}</div>
                </div>
                <span class="text-[10px] font-bold text-blue-500 bg-blue-50 px-2 py-1 rounded-md">{{ loc.region }}</span>
              </button>
            </div>

            <div v-if="selectedLocation && searchResults.length === 0" class="mt-4 p-4 bg-green-50 border border-green-100 rounded-xl flex items-center justify-between">
              <div>
                <div class="text-xs font-bold text-green-700 mb-0.5">当前地理位置</div>
                <div class="text-sm font-bold text-slate-800">{{ selectedLocation.province }} {{ selectedLocation.city }}</div>
              </div>
              <div class="flex flex-col items-end">
                <span class="text-[10px] font-bold bg-white text-green-600 px-2 py-1 rounded shadow-sm">{{ selectedLocation.region }}</span>
              </div>
            </div>
          </div>

          <div v-if="editingCrop.stageMode === 'AUTO'" class="mb-6 p-4 rounded-2xl border border-sky-100 bg-sky-50">
            <div class="flex items-start justify-between gap-3">
              <div>
                <div class="text-xs font-bold uppercase tracking-[0.16em] text-sky-600">自动物候判断</div>
                <div class="mt-2 text-lg font-black text-slate-900">{{ getEstimateStageText(editEstimate, isEditEstimating) }}</div>
                <div class="mt-1 text-sm text-slate-500">{{ getEstimateReasonText(editEstimate) }}</div>
              </div>
              <div v-if="formatEstimateConfidence(editEstimate)" class="rounded-full bg-white px-3 py-1 text-xs font-bold text-sky-600 shadow-sm">
                {{ formatEstimateConfidence(editEstimate) }}
              </div>
            </div>
            <ul v-if="editEstimateWarnings.length" class="mt-3 space-y-1 text-xs text-amber-700">
              <li v-for="warning in editEstimateWarnings" :key="warning">- {{ warning }}</li>
            </ul>
          </div>

          <div v-if="formError" class="mb-4 rounded-2xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-600">
            {{ formError }}
          </div>
        </div>

        <div class="flex flex-shrink-0 space-x-3 mt-4">
          <button
            @click="deleteCrop(editingCrop.id)"
            class="flex-shrink-0 bg-red-50 text-red-500 px-5 py-4 rounded-xl font-bold active:scale-95 transition-transform"
          >
            删除
          </button>
          <button
            @click="confirmEdit"
            :disabled="!canSubmitEdit"
            class="flex-1 bg-slate-900 text-white py-4 rounded-xl font-bold shadow-lg shadow-slate-900/20 active:scale-[0.98] transition-transform disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {{ isSaving ? '保存中...' : '保存修改' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes slide-up { from { transform: translateY(100%); } to { transform: translateY(0); } }
.animate-slide-up { animation: slide-up 0.3s cubic-bezier(0.16, 1, 0.3, 1); }

:global(.dark) .farm-page {
  background:
    radial-gradient(circle at top, rgba(16, 185, 129, 0.18), transparent 34%),
    linear-gradient(180deg, #020617 0%, #0f172a 42%, #111827 100%);
}

:global(.dark) .farm-page :deep([class~='bg-white']) {
  background-color: rgba(15, 23, 42, 0.88) !important;
}

:global(.dark) .farm-page :deep([class~='bg-slate-50']),
:global(.dark) .farm-page :deep([class~='bg-slate-50/30']) {
  background-color: rgba(30, 41, 59, 0.78) !important;
}

:global(.dark) .farm-page :deep([class~='bg-slate-100']) {
  background-color: rgba(51, 65, 85, 0.82) !important;
}

:global(.dark) .farm-page :deep([class~='border-slate-50']),
:global(.dark) .farm-page :deep([class~='border-slate-100']),
:global(.dark) .farm-page :deep([class~='border-slate-200']) {
  border-color: rgba(71, 85, 105, 0.76) !important;
}

:global(.dark) .farm-page :deep([class~='text-slate-900']),
:global(.dark) .farm-page :deep([class~='text-slate-800']) {
  color: #f8fafc !important;
}

:global(.dark) .farm-page :deep([class~='text-slate-700']),
:global(.dark) .farm-page :deep([class~='text-slate-600']) {
  color: #e2e8f0 !important;
}

:global(.dark) .farm-page :deep([class~='text-slate-500']),
:global(.dark) .farm-page :deep([class~='text-slate-400']) {
  color: #94a3b8 !important;
}

:global(.dark) .farm-page :deep([class~='bg-green-50']),
:global(.dark) .farm-page :deep([class~='bg-green-50/30']),
:global(.dark) .farm-page :deep([class~='bg-green-50/50']) {
  background-color: rgba(16, 185, 129, 0.16) !important;
}

:global(.dark) .farm-page :deep([class~='border-green-100']) {
  border-color: rgba(52, 211, 153, 0.28) !important;
}

:global(.dark) .farm-page :deep([class~='bg-blue-50']) {
  background-color: rgba(59, 130, 246, 0.16) !important;
}

:global(.dark) .farm-page :deep([class~='border-blue-100']) {
  border-color: rgba(96, 165, 250, 0.28) !important;
}

:global(.dark) .farm-page :deep([class~='bg-sky-50']) {
  background-color: rgba(14, 165, 233, 0.16) !important;
}

:global(.dark) .farm-page :deep([class~='border-sky-100']) {
  border-color: rgba(56, 189, 248, 0.28) !important;
}

:global(.dark) .farm-page :deep([class~='bg-emerald-50']) {
  background-color: rgba(16, 185, 129, 0.16) !important;
}

:global(.dark) .farm-page :deep([class~='border-emerald-100']) {
  border-color: rgba(52, 211, 153, 0.28) !important;
}

:global(.dark) .farm-page :deep([class~='bg-rose-50']) {
  background-color: rgba(244, 63, 94, 0.16) !important;
}

:global(.dark) .farm-page :deep([class~='border-rose-200']) {
  border-color: rgba(251, 113, 133, 0.28) !important;
}

:global(.dark) .farm-page :deep([class~='bg-red-50']) {
  background-color: rgba(239, 68, 68, 0.16) !important;
}

:global(.dark) .farm-page :deep([class~='border-red-100']) {
  border-color: rgba(248, 113, 113, 0.28) !important;
}

:global(.dark) .farm-page :deep(input),
:global(.dark) .farm-page :deep(select) {
  color: #f8fafc;
}

:global(.dark) .farm-page :deep(input::placeholder) {
  color: #94a3b8;
}
</style>
