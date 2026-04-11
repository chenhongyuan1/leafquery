import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import axios from 'axios'
import { cropLibrary, getCropMeta } from '../constants/farmCatalog'

import {
  LOCAL_STORAGE_KEY,
  CLOUD_CACHE_KEY,
  HEALTHY_KEYWORDS,
  getFriendlySyncError,
  parseJson,
  getStoredUser,
  sanitizeLocation,
  normalizeStageMode
} from './farmUtils'

export const useFarmStore = defineStore('farm-cloud', () => {
  const crops = ref([])
  const activeCropId = ref(null)
  const identificationHistory = ref([])
  const syncMode = ref('local')
  const isSyncing = ref(false)
  const isInitialized = ref(false)
  const syncError = ref('')

  let initializePromise = null

  const persistState = (target = 'local') => {
    const key = target === 'cloud' ? CLOUD_CACHE_KEY : LOCAL_STORAGE_KEY
    localStorage.setItem(key, JSON.stringify({
      crops: crops.value,
      activeCropId: activeCropId.value,
      identificationHistory: identificationHistory.value
    }))
  }

  const normalizeHistoryRecord = (item = {}, index = 0) => ({
    id: item.id ?? Date.now() + index,
    pestName: item.pestName || item.name || '未知结果',
    confidence: Number(item.confidence || 0),
    time: item.createTime || item.time || new Date().toISOString(),
    cropId: item.cropId ?? item.crop?.id ?? activeCropId.value ?? null,
    cropName: item.cropName || item.crop?.name || '',
    locationId: String(item.locationId ?? item.crop?.locationId ?? ''),
    city: item.city || item.crop?.city || '',
    region: item.region || item.crop?.region || '',
    imageUrl: item.imageUrl || ''
  })

  const hydrateCrops = (items = [], target = null) => {
    const normalized = items.map((item) => {
      const cropId = Number(item.cropId ?? item.id ?? Date.now())
      const cropName = item.cropName ?? item.name ?? '未命名作物'
      const meta = getCropMeta(cropName)

      const location = sanitizeLocation(item)

      return {
        id: cropId,
        cropId,
        userId: item.userId ?? null,
        name: cropName,
        cropName,
        icon: item.icon || meta.icon,
        stage: item.stage || '',
        city: item.city || location.city || '',
        region: item.region || location.region || '',
        locationId: String(item.locationId ?? location.id ?? ''),
        province: item.province || location.province || '',
        sowingDate: item.sowingDate || null,
        transplantDate: item.transplantDate || null,
        stageMode: normalizeStageMode(item.stageMode, item.stage ? 'MANUAL' : 'AUTO'),
        estimatedStage: item.estimatedStage || '',
        stageConfidence: item.stageConfidence == null ? null : Number(item.stageConfidence),
        stageReason: item.stageReason || '',
        stageEvaluatedAt: item.stageEvaluatedAt || null,
        location,
        diseases: Array.isArray(item.diseases) && item.diseases.length ? item.diseases : meta.diseases,
        addedAt: item.addedAt || item.createdAt || new Date().toISOString(),
        createdAt: item.createdAt || item.addedAt || null,
        updatedAt: item.updatedAt || null,
        isActive: Boolean(item.isActive ?? item.active ?? false)
      }
    })

    crops.value = normalized

    const explicitActive = normalized.find(item => item.isActive)
    if (explicitActive) {
      activeCropId.value = explicitActive.id
    } else if (normalized.some(item => String(item.id) === String(activeCropId.value))) {
      activeCropId.value = activeCropId.value
    } else {
      activeCropId.value = normalized[0]?.id || null
    }

    if (target) {
      persistState(target)
    }
  }

  const hydrateHistory = (records = [], target = null) => {
    identificationHistory.value = records.map((item, index) => ({
      id: item.id ?? Date.now() + index,
      pestName: item.pestName || item.name || '未知结果',
      confidence: Number(item.confidence || 0),
      time: item.createTime || item.time || new Date().toISOString(),
      cropId: item.cropId ?? item.crop?.id ?? activeCropId.value ?? null,
      cropName: item.cropName || item.crop?.name || '',
      locationId: String(item.locationId ?? ''),
      city: item.city || '',
      region: item.region || '',
      imageUrl: item.imageUrl || ''
    }))

    if (target) {
      persistState(target)
    }
  }

  const loadLocalState = () => {
    const saved = parseJson(localStorage.getItem(LOCAL_STORAGE_KEY), {})
    hydrateCrops(saved.crops || [], null)
    hydrateHistory(saved.identificationHistory || [], null)
    activeCropId.value = saved.activeCropId || activeCropId.value
    syncMode.value = 'local'
  }

  const loadCloudCache = () => {
    const cached = parseJson(localStorage.getItem(CLOUD_CACHE_KEY), {})
    if ((cached.crops || []).length || (cached.identificationHistory || []).length) {
      hydrateCrops(cached.crops || [], null)
      hydrateHistory(cached.identificationHistory || [], null)
      activeCropId.value = cached.activeCropId || activeCropId.value
    }
  }

  const getCurrentPersistenceTarget = () => {
    return syncMode.value.startsWith('cloud') ? 'cloud' : 'local'
  }

  const setSyncError = (message) => {
    syncError.value = message
    if (message) {
      console.error(message)
    }
  }

  const loadRemoteCrops = async (userId) => {
    const response = await axios.get(`/api/farm/crops?userId=${userId}`)
    if (response.data?.code !== 200) {
      throw new Error(response.data?.message || 'Failed to load crops from cloud')
    }
    hydrateCrops(response.data.data || [], 'cloud')
  }

  const loadRemoteHistory = async (userId) => {
    const response = await axios.get(`/api/record/list?userId=${userId}`)
    if (response.data?.code !== 200) {
      throw new Error(response.data?.message || 'Failed to load records from cloud')
    }
    hydrateHistory(response.data.data || [], 'cloud')
  }

  const initialize = async ({ force = false } = {}) => {
    if (initializePromise && !force) {
      return initializePromise
    }

    initializePromise = (async () => {
      const user = getStoredUser()
      setSyncError('')

      if (!user?.userId) {
        loadLocalState()
        isInitialized.value = true
        return
      }

      loadCloudCache()
      syncMode.value = 'cloud'
      isSyncing.value = true

      try {
        await Promise.all([
          loadRemoteCrops(user.userId),
          loadRemoteHistory(user.userId)
        ])
        syncMode.value = 'cloud'
      } catch (error) {
        setSyncError(getFriendlySyncError(error, '农场云同步暂时不可用，已切换到本地模式。'))
        if (!crops.value.length && !identificationHistory.value.length) {
          loadLocalState()
        }
        syncMode.value = 'cloud-fallback'
      } finally {
        isSyncing.value = false
        isInitialized.value = true
        initializePromise = null
      }
    })()

    return initializePromise
  }

  const resetToLocal = () => {
    setSyncError('')
    loadLocalState()
    isInitialized.value = true
  }

  const applyLocalCropAdd = (cropName, locationData, options = {}, target = 'local') => {
    if (crops.value.some(crop => crop.name === cropName)) {
      return false
    }

    const meta = getCropMeta(cropName)
    const location = sanitizeLocation(locationData)
    const cropId = Date.now()
    const stageMode = normalizeStageMode(options.stageMode, 'AUTO')
    const nextCrop = {
      id: cropId,
      cropId,
      userId: null,
      name: meta.name,
      cropName: meta.name,
      icon: meta.icon,
      stage: options.stage || '',
      city: location.city,
      region: location.region,
      locationId: location.id,
      province: location.province,
      sowingDate: options.sowingDate || null,
      transplantDate: options.transplantDate || null,
      stageMode,
      estimatedStage: options.estimatedStage || '',
      stageConfidence: options.stageConfidence ?? null,
      stageReason: options.stageReason || '',
      stageEvaluatedAt: options.stageEvaluatedAt || null,
      location,
      diseases: meta.diseases,
      addedAt: new Date().toISOString(),
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      isActive: crops.value.length === 0
    }

    crops.value = [...crops.value, nextCrop]
    if (nextCrop.isActive) {
      activeCropId.value = nextCrop.id
    }
    persistState(target)
    return true
  }

  const applyLocalCropUpdate = (cropId, updates, target = 'local') => {
    crops.value = crops.value.map((crop) => {
      if (String(crop.id) !== String(cropId)) return crop
      return {
        ...crop,
        stage: updates.stage ?? crop.stage,
        sowingDate: updates.sowingDate ?? crop.sowingDate,
        transplantDate: updates.transplantDate ?? crop.transplantDate,
        stageMode: updates.stageMode ? normalizeStageMode(updates.stageMode, crop.stageMode) : crop.stageMode,
        estimatedStage: updates.estimatedStage ?? crop.estimatedStage,
        stageConfidence: updates.stageConfidence ?? crop.stageConfidence,
        stageReason: updates.stageReason ?? crop.stageReason,
        stageEvaluatedAt: updates.stageEvaluatedAt ?? crop.stageEvaluatedAt,
        city: updates.city ?? crop.city,
        province: updates.province ?? crop.province,
        region: updates.region ?? crop.region,
        locationId: updates.locationId ?? crop.locationId,
        location: {
          id: updates.locationId ?? crop.locationId,
          province: updates.province ?? crop.province,
          city: updates.city ?? crop.city,
          region: updates.region ?? crop.region
        },
        updatedAt: new Date().toISOString()
      }
    })
    persistState(target)
    return true
  }

  const applyLocalCropDelete = (cropId, target = 'local') => {
    crops.value = crops.value.filter(crop => String(crop.id) !== String(cropId))
    if (!crops.value.some(crop => String(crop.id) === String(activeCropId.value))) {
      activeCropId.value = crops.value[0]?.id || null
      crops.value = crops.value.map((crop, index) => ({
        ...crop,
        isActive: index === 0
      }))
    }
    identificationHistory.value = identificationHistory.value.filter(item => String(item.cropId) !== String(cropId))
    persistState(target)
    return true
  }

  const applyLocalActiveCrop = (cropId, target = 'local') => {
    activeCropId.value = cropId
    crops.value = crops.value.map(crop => ({
      ...crop,
      isActive: String(crop.id) === String(cropId)
    }))
    persistState(target)
    return true
  }

  const estimatePhenology = async (payload) => {
    const response = await axios.post('/api/farm/phenology/estimate', payload)
    return response.data
  }

  const addCrop = async (cropName, locationData, options = {}) => {
    const user = getStoredUser()
    if (!user?.userId) {
      syncMode.value = 'local'
      return applyLocalCropAdd(cropName, locationData, options, 'local')
    }

    try {
      const stageMode = normalizeStageMode(options.stageMode, 'AUTO')
      await axios.post('/api/farm/crops', {
        userId: user.userId,
        cropName,
        stage: options.stage || '',
        province: locationData?.province || '',
        city: locationData?.city || '',
        region: locationData?.region || '',
        locationId: String(locationData?.id ?? locationData?.locationId ?? ''),
        sowingDate: options.sowingDate || null,
        transplantDate: options.transplantDate || null,
        stageMode,
        estimatedStage: options.estimatedStage || '',
        stageConfidence: options.stageConfidence ?? null,
        stageReason: options.stageReason || '',
        stageEvaluatedAt: options.stageEvaluatedAt || null,
        isActive: crops.value.length === 0
      })
      await loadRemoteCrops(user.userId)
      syncMode.value = 'cloud'
      return true
    } catch (error) {
      setSyncError(getFriendlySyncError(error, '云端新增作物失败，已暂存到本地。'))
      syncMode.value = 'cloud-fallback'
      return applyLocalCropAdd(cropName, locationData, options, 'cloud')
    }
  }

  const updateCrop = async (cropId, updates) => {
    const user = getStoredUser()
    if (!user?.userId) {
      return applyLocalCropUpdate(cropId, updates, 'local')
    }

    try {
      await axios.put(`/api/farm/crops/${cropId}`, {
        userId: user.userId,
        stage: updates.stage,
        sowingDate: updates.sowingDate ?? null,
        transplantDate: updates.transplantDate ?? null,
        stageMode: updates.stageMode,
        estimatedStage: updates.estimatedStage,
        stageConfidence: updates.stageConfidence,
        stageReason: updates.stageReason,
        stageEvaluatedAt: updates.stageEvaluatedAt,
        province: updates.province,
        city: updates.city,
        region: updates.region,
        locationId: updates.locationId
      })
      await loadRemoteCrops(user.userId)
      syncMode.value = 'cloud'
      return true
    } catch (error) {
      setSyncError(getFriendlySyncError(error, '云端更新作物失败，已改为本地保存。'))
      syncMode.value = 'cloud-fallback'
      return applyLocalCropUpdate(cropId, updates, 'cloud')
    }
  }

  const removeCrop = async (cropId) => {
    const user = getStoredUser()
    if (!user?.userId) {
      return applyLocalCropDelete(cropId, 'local')
    }

    try {
      await axios.delete(`/api/farm/crops/${cropId}?userId=${user.userId}`)
      await Promise.all([
        loadRemoteCrops(user.userId),
        loadRemoteHistory(user.userId)
      ])
      syncMode.value = 'cloud'
      return true
    } catch (error) {
      setSyncError(getFriendlySyncError(error, '云端删除作物失败，已在本地更新状态。'))
      syncMode.value = 'cloud-fallback'
      return applyLocalCropDelete(cropId, 'cloud')
    }
  }

  const setActiveCrop = async (cropId) => {
    const user = getStoredUser()
    if (!user?.userId) {
      return applyLocalActiveCrop(cropId, 'local')
    }

    try {
      await axios.put(`/api/farm/crops/${cropId}/active`, {
        userId: user.userId
      })
      await loadRemoteCrops(user.userId)
      syncMode.value = 'cloud'
      return true
    } catch (error) {
      setSyncError(getFriendlySyncError(error, '切换当前作物失败，已在本地更新状态。'))
      syncMode.value = 'cloud-fallback'
      return applyLocalActiveCrop(cropId, 'cloud')
    }
  }

  const syncIdentificationHistory = (records = []) => {
    hydrateHistory(records, getCurrentPersistenceTarget())
  }

  const activeCrop = computed(() => {
    return crops.value.find(crop => String(crop.id) === String(activeCropId.value)) || null
  })

  const addIdentification = (record, target = getCurrentPersistenceTarget()) => {
    const crop = activeCrop.value
    identificationHistory.value.unshift(normalizeHistoryRecord({
      ...record,
      cropId: record.cropId ?? crop?.id ?? activeCropId.value,
      cropName: record.cropName || crop?.name || '',
      locationId: record.locationId ?? crop?.locationId ?? '',
      city: record.city || crop?.city || '',
      region: record.region || crop?.region || ''
    }))
    if (identificationHistory.value.length > 100) {
      identificationHistory.value = identificationHistory.value.slice(0, 100)
    }
    persistState(target)
    return identificationHistory.value[0]
  }

  const saveIdentification = async (record) => {
    const user = getStoredUser()
    const crop = activeCrop.value
    const payload = {
      userId: user?.userId ?? null,
      cropId: record.cropId ?? crop?.id ?? activeCropId.value ?? null,
      cropName: record.cropName || crop?.name || '',
      pestName: record.pestName || record.name || '',
      confidence: Number(record.confidence || 0),
      locationId: String(record.locationId ?? crop?.locationId ?? ''),
      city: record.city || crop?.city || '',
      region: record.region || crop?.region || '',
      imageUrl: record.imageUrl || ''
    }

    if (!user?.userId) {
      syncMode.value = 'local'
      addIdentification(payload, 'local')
      return true
    }

    try {
      const response = await axios.post('/api/record/add', payload)
      if (response.data?.code !== 200) {
        throw new Error(response.data?.message || 'Failed to save record')
      }
      await loadRemoteHistory(user.userId)
      syncMode.value = 'cloud'
      return true
    } catch (error) {
      setSyncError(getFriendlySyncError(error, 'Cloud record save failed, falling back to local cache.'))
      syncMode.value = 'cloud-fallback'
      addIdentification(payload, 'cloud')
      return false
    }
  }

  const removeLocalIdentification = (recordId, target = getCurrentPersistenceTarget()) => {
    identificationHistory.value = identificationHistory.value.filter(item => String(item.id) !== String(recordId))
    persistState(target)
    return true
  }

  const removeIdentification = async (recordId) => {
    const user = getStoredUser()
    if (!user?.userId) {
      return removeLocalIdentification(recordId, 'local')
    }

    try {
      await axios.delete(`/api/record/${recordId}`)
      await loadRemoteHistory(user.userId)
      syncMode.value = 'cloud'
      return true
    } catch (error) {
      setSyncError(getFriendlySyncError(error, 'Cloud record deletion failed, falling back to local cache.'))
      syncMode.value = 'cloud-fallback'
      return removeLocalIdentification(recordId, 'cloud')
    }
  }

  const clearLocalIdentificationHistory = (target = getCurrentPersistenceTarget()) => {
    identificationHistory.value = []
    persistState(target)
    return true
  }

  const clearIdentificationHistory = async () => {
    const user = getStoredUser()
    if (!user?.userId) {
      return clearLocalIdentificationHistory('local')
    }

    try {
      await axios.delete(`/api/record/clear?userId=${user.userId}`)
      await loadRemoteHistory(user.userId)
      syncMode.value = 'cloud'
      return true
    } catch (error) {
      setSyncError(getFriendlySyncError(error, 'Cloud record clear failed, falling back to local cache.'))
      syncMode.value = 'cloud-fallback'
      return clearLocalIdentificationHistory('cloud')
    }
  }

  const getHistoryByCrop = (cropId) => {
    return identificationHistory.value.filter(item => String(item.cropId) === String(cropId))
  }

  const getTopDiseases = (cropId) => {
    const counts = {}
    getHistoryByCrop(cropId).forEach((item) => {
      const pestName = item.pestName || ''
      if (HEALTHY_KEYWORDS.some(keyword => pestName.toLowerCase().includes(keyword.toLowerCase()))) {
        return
      }
      counts[pestName] = (counts[pestName] || 0) + 1
    })

    return Object.entries(counts)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 3)
      .map(([name, count]) => ({ name, count }))
  }

  loadLocalState()

  return {
    cropLibrary,
    crops,
    activeCropId,
    activeCrop,
    identificationHistory,
    syncMode,
    isSyncing,
    isInitialized,
    syncError,
    initialize,
    resetToLocal,
    estimatePhenology,
    addCrop,
    updateCrop,
    removeCrop,
    setActiveCrop,
    syncIdentificationHistory,
    addIdentification,
    saveIdentification,
    removeIdentification,
    clearIdentificationHistory,
    getHistoryByCrop,
    getTopDiseases
  }
})
