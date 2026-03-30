export const cropLibrary = [
  { name: '水稻', icon: '🌾', diseases: ['稻瘟病', '纹枯病'] },
  { name: '玉米', icon: '🌽', diseases: ['大斑病', '锈病'] },
  { name: '小麦', icon: '🌾', diseases: ['白粉病', '条锈病'] }
]

export const cropPhenologyMap = {
  水稻: ['秧苗期', '分蘖期', '拔节期', '抽穗期', '灌浆期', '成熟期'],
  玉米: ['苗期', '拔节期', '大喇叭口期', '抽雄期', '灌浆期', '成熟期'],
  冬小麦: ['越冬期', '返青期', '拔节期', '抽穗期', '灌浆期', '成熟期'],
  小麦: ['越冬期', '返青期', '拔节期', '抽穗期', '灌浆期', '成熟期']
}

export const DEFAULT_STAGE = '拔节期'

export const getDefaultStage = (cropName = '') => {
  return cropPhenologyMap[cropName]?.[0] || DEFAULT_STAGE
}

export const getCropMeta = (cropName = '') => {
  return cropLibrary.find(item => item.name === cropName) || {
    name: cropName || '未命名作物',
    icon: '🌿',
    diseases: []
  }
}
