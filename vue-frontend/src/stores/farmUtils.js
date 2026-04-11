import axios from 'axios'

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

export const getFriendlySyncError = (error, fallbackMessage) => {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status
    const serverMessage = error.response?.data?.message

    if (hasChineseText(serverMessage)) {
      return serverMessage
    }

    if (!error.response) {
      return '网络连接异常，暂时无法同步农场数据，请稍后重试。'
    }

    switch (status) {
      case 400:
        return '请求参数有误，暂时无法完成本次农场同步。'
      case 401:
        return '登录状态已失效，请重新登录后再试。'
      case 403:
        return '当前账号没有权限执行这项农场操作。'
      case 404:
        return '未找到对应的农场数据，请刷新页面后重试。'
      case 409:
        return '农场数据已发生变化，请刷新页面后重试。'
      case 500:
        return fallbackMessage
      default:
        return fallbackMessage
    }
  }

  if (error instanceof Error && hasChineseText(error.message)) {
    return error.message
  }

  return fallbackMessage
}

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
