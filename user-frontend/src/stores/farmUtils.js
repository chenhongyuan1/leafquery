import { getApiErrorMessage } from '../api/index.js'

export const LOCAL_STORAGE_KEY = 'farm_data_local'
export const CLOUD_CACHE_KEY = 'farm_data_cloud'
export const HEALTHY_KEYWORDS = ['健康', 'healthy', 'unknown', '未发现', '暂无异常']

export const parseJson = (value, fallback) => {
  try {
    return value ? JSON.parse(value) : fallback
  } catch (error) {
    console.error('Failed to parse stored farm data', error)
    return fallback
  }
}

export const hasChineseText = (value) => typeof value === 'string' && /[\u4e00-\u9fff]/.test(value)

export const getFriendlySyncError = (error, fallbackMessage) => getApiErrorMessage(error, fallbackMessage)

export const getStoredUser = () => {
  return parseJson(localStorage.getItem('user'), null)
}

export const sanitizeLocation = (locationData = {}) => {
  const source = locationData.location ? locationData.location : locationData
  return {
    id: String(source.id ?? source.locationId ?? ''),
    province: source.province || '',
    city: source.city || '',
    region: source.region || ''
  }
}

export const normalizeStageMode = (value, fallback = 'MANUAL') => {
  if (typeof value !== 'string' || !value.trim()) {
    return fallback
  }
  return value.trim().toUpperCase() === 'AUTO' ? 'AUTO' : 'MANUAL'
}

export const resolveEffectiveStage = (crop = {}) => {
  const stage = typeof crop.stage === 'string' ? crop.stage.trim() : ''
  if (stage) {
    return stage
  }

  const estimatedStage = typeof crop.estimatedStage === 'string' ? crop.estimatedStage.trim() : ''
  if (!estimatedStage) {
    return ''
  }

  return normalizeStageMode(crop.stageMode, 'AUTO') === 'AUTO' ? estimatedStage : ''
}
