<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import axios from 'axios'
import { useFarmStore } from '../../stores/farmCloud'
import PCFarmManager from '../../components/pc/PCFarmManager.vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, MarkLineComponent, TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'

use([CanvasRenderer, PieChart, BarChart, LineChart, GridComponent, LegendComponent, MarkLineComponent, TooltipComponent])

const farmStore = useFarmStore()
const DEFAULT_REGION_LABEL = '全国默认区'
const HEALTHY_KEYWORDS = ['健康', 'healthy', 'unknown', '未发现', '暂无异常']
const TARGET_TYPE_LABELS = { DISEASE: '病害', PEST: '虫害' }
const DISEASE_SERIES_COLORS = ['#38bdf8', '#0ea5e9', '#3b82f6', '#6366f1', '#8b5cf6']
const PEST_SERIES_COLORS = ['#22c55e', '#10b981', '#14b8a6', '#84cc16', '#eab308']
const COMPARISON_FILTER_OPTIONS = [
  { key: 'ALL', label: '全部' },
  { key: 'DISEASE', label: '仅病害' },
  { key: 'PEST', label: '仅虫害' }
]

const currentUser = ref(null)
const isDark = ref(document.documentElement.classList.contains('dark'))
const loading = ref(true)
const recordsLoading = ref(false)
const forecastLoading = ref(false)
const weatherLoading = ref(false)
const records = ref([])
const forecastData = ref(null)
const comparisonForecasts = ref([])
const weatherFeatures = ref(null)
const forecastError = ref('')
const weatherError = ref('')
const targetContext = ref(null)
const targetOptions = ref({ diseaseTargets: [], pestTargets: [] })
const selectedTargetType = ref('DISEASE')
const selectedTargetName = ref('')
const comparisonViewMode = ref('ALL')
const targetsLoading = ref(false)
let themeObserver = null

const getStoredUser = () => {
  try {
    const raw = localStorage.getItem('user')
    return raw ? JSON.parse(raw) : null
  } catch (error) {
    console.error('Failed to parse user from localStorage', error)
    return null
  }
}

const parseDate = (value) => {
  if (!value) return null
  const date = new Date(value)
  if (!Number.isNaN(date.getTime())) return date
  if (!Number.isNaN(Number(value))) {
    const numericDate = new Date(Number(value))
    return Number.isNaN(numericDate.getTime()) ? null : numericDate
  }
  return null
}

const formatDate = (value, withTime = false) => {
  const date = parseDate(value)
  if (!date) return '未知时间'
  const base = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
  if (!withTime) return base
  return `${base} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

const normalizeRecord = (item) => ({
  id: item.id ?? `${item.pestName || 'record'}-${item.createTime || Date.now()}`,
  pestName: item.pestName || '未知结果',
  cropId: item.cropId ?? null,
  cropName: item.cropName || '',
  confidence: Number(item.confidence || 0),
  locationId: String(item.locationId ?? ''),
  city: item.city || '',
  region: item.region || '',
  imageUrl: item.imageUrl || '',
  createTime: item.createTime || item.time || ''
})

const isHealthyResult = (name) => {
  if (!name) return true
  const normalized = String(name).toLowerCase()
  return HEALTHY_KEYWORDS.some(keyword => normalized.includes(keyword.toLowerCase()))
}

const riskMetaOf = (score) => {
  const value = Number(score || 0)
  if (value >= 0.75) return { label: '高风险', chip: 'bg-rose-100 text-rose-700 dark:bg-rose-500/10 dark:text-rose-300', text: 'text-rose-500', color: '#ef4444' }
  if (value >= 0.55) return { label: '中风险', chip: 'bg-amber-100 text-amber-700 dark:bg-amber-500/10 dark:text-amber-300', text: 'text-amber-500', color: '#f59e0b' }
  if (value >= 0.3) return { label: '轻风险', chip: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-500/10 dark:text-yellow-300', text: 'text-yellow-500', color: '#eab308' }
  return { label: '低风险', chip: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300', text: 'text-emerald-500', color: '#10b981' }
}

const targetKeyOf = (targetType, targetName) => `${targetType}:${targetName}`
const targetDisplayName = (targetType, targetName) => `${TARGET_TYPE_LABELS[targetType] || '对象'} · ${targetName}`

const activeCrop = computed(() => farmStore.activeCrop)
const sortedRecords = computed(() => [...records.value].sort((a, b) => (parseDate(b.createTime)?.getTime() || 0) - (parseDate(a.createTime)?.getTime() || 0)))
const filteredRecords = computed(() => {
  const crop = activeCrop.value
  if (!crop) return sortedRecords.value
  return sortedRecords.value.filter((item) => {
    if (item.cropId !== null && item.cropId !== undefined && item.cropId !== '') {
      return String(item.cropId) === String(crop.id)
    }
    return item.cropName === crop.name
  })
})
const cropScopeLabel = computed(() => {
  const crop = activeCrop.value
  if (!crop) return '当前显示全部作物记录'
  return `${crop.name} · ${[crop.province, crop.city].filter(Boolean).join(' / ') || crop.region || DEFAULT_REGION_LABEL}`
})
const diseaseDistribution = computed(() => {
  const counts = {}
  filteredRecords.value.forEach((item) => {
    if (isHealthyResult(item.pestName)) return
    counts[item.pestName] = (counts[item.pestName] || 0) + 1
  })
  return Object.entries(counts).sort((a, b) => b[1] - a[1]).slice(0, 6).map(([name, value]) => ({ name, value }))
})
const confidenceBuckets = computed(() => {
  const buckets = [
    { key: 'high', label: '高置信', range: '>= 80%', value: 0, color: '#10b981' },
    { key: 'mid', label: '中置信', range: '55% - 79%', value: 0, color: '#f59e0b' },
    { key: 'low', label: '低置信', range: '< 55%', value: 0, color: '#ef4444' }
  ]
  filteredRecords.value.forEach((item) => {
    const confidence = Number(item.confidence || 0)
    if (confidence >= 0.8) buckets[0].value += 1
    else if (confidence >= 0.55) buckets[1].value += 1
    else buckets[2].value += 1
  })
  return buckets
})
const activityTrend = computed(() => {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  const list = Array.from({ length: 14 }, (_, index) => {
    const date = new Date(today)
    date.setDate(today.getDate() - (13 - index))
    return { key: formatDate(date), label: `${date.getMonth() + 1}/${date.getDate()}`, value: 0 }
  })
  const map = Object.fromEntries(list.map(item => [item.key, item]))
  filteredRecords.value.forEach((item) => {
    const date = parseDate(item.createTime)
    if (!date) return
    date.setHours(0, 0, 0, 0)
    const key = formatDate(date)
    if (map[key]) map[key].value += 1
  })
  return list
})
const weatherSummary = computed(() => {
  const summary = forecastData.value?.weatherSummary || {}
  const features = weatherFeatures.value || {}
  return {
    tempMean3d: Number(summary.tempMean3d ?? features.temp_mean_3d ?? 0),
    humidityMean3d: Number(summary.humidityMean3d ?? features.humidity_mean_3d ?? 0),
    rainSum7d: Number(summary.rainSum7d ?? features.rain_sum_7d ?? 0),
    consecutiveRainDays: Number(summary.consecutiveRainDays ?? features.consecutive_rain_days ?? 0)
  }
})
const weatherPressure = computed(() => {
  const summary = weatherSummary.value
  let score = 0
  if (summary.humidityMean3d >= 85) score += 35
  else if (summary.humidityMean3d >= 75) score += 20
  if (summary.rainSum7d >= 25) score += 30
  else if (summary.rainSum7d >= 12) score += 15
  if (summary.consecutiveRainDays >= 3) score += 20
  else if (summary.consecutiveRainDays >= 1) score += 8
  if (summary.tempMean3d > 30 || summary.tempMean3d < 10) score += 15
  const capped = Math.min(score, 100)
  if (capped >= 65) return { score: capped, label: '高压', chip: 'bg-rose-100 text-rose-700 dark:bg-rose-500/10 dark:text-rose-300', desc: '天气条件正在明显抬升病虫害活跃度。' }
  if (capped >= 35) return { score: capped, label: '中压', chip: 'bg-amber-100 text-amber-700 dark:bg-amber-500/10 dark:text-amber-300', desc: '环境压力正在累积，建议提高巡检频次。' }
  return { score: capped, label: '低压', chip: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300', desc: '天气条件整体平稳，当前未见显著压力。' }
})
const todayRiskScore = computed(() => Number(forecastData.value?.todayRiskScore || 0))
const todayRiskMeta = computed(() => riskMetaOf(todayRiskScore.value))
const highRiskDays = computed(() => (forecastData.value?.dailySeries || []).filter(item => Number(item.riskScore || 0) >= 0.75).length)
const averageConfidence = computed(() => filteredRecords.value.length ? filteredRecords.value.reduce((sum, item) => sum + Number(item.confidence || 0), 0) / filteredRecords.value.length : 0)
const recentRecords = computed(() => filteredRecords.value.slice(0, 6))
const weatherCards = computed(() => ([
  { label: '近 3 天平均湿度', value: `${Math.round(weatherSummary.value.humidityMean3d || 0)}%` },
  { label: '近 3 天平均温度', value: `${Math.round(weatherSummary.value.tempMean3d || 0)}°C` },
  { label: '近 7 天累计降雨', value: `${Math.round(weatherSummary.value.rainSum7d || 0)} mm` },
  { label: '连续降雨天数', value: `${Math.round(weatherSummary.value.consecutiveRainDays || 0)} 天` }
]))
const summaryCards = computed(() => ([
  { label: '作物档案数', value: String(farmStore.crops.length), desc: '云端与本地兜底后的作物档案数' },
  { label: '当前作物记录数', value: String(filteredRecords.value.length), desc: activeCrop.value ? `仅统计 ${activeCrop.value.name} 相关记录` : '未设置作物时展示全部记录' },
  { label: '近 7 天高风险日', value: String(highRiskDays.value), desc: '未来 7 天预测中达到高风险阈值的天数' },
  { label: '天气压力', value: `${weatherPressure.value.label} ${weatherPressure.value.score}`, desc: weatherPressure.value.desc }
]))
const isRefreshing = computed(() => recordsLoading.value || forecastLoading.value || weatherLoading.value || targetsLoading.value)
const availableTargetNames = computed(() => (
  selectedTargetType.value === 'PEST'
    ? (targetOptions.value.pestTargets || [])
    : (targetOptions.value.diseaseTargets || [])
))
const allTargetDefinitions = computed(() => ([
  ...(targetOptions.value.diseaseTargets || []).map(targetName => ({ targetType: 'DISEASE', targetName })),
  ...(targetOptions.value.pestTargets || []).map(targetName => ({ targetType: 'PEST', targetName }))
]))
const selectedTargetKey = computed(() => targetKeyOf(selectedTargetType.value, selectedTargetName.value))
const filteredComparisonForecasts = computed(() => {
  if (comparisonViewMode.value === 'ALL') return comparisonForecasts.value
  return comparisonForecasts.value.filter(item => item.targetType === comparisonViewMode.value)
})
const forecastWarnings = computed(() => forecastData.value?.warnings || [])
const currentTargetLabel = computed(() => forecastData.value?.targetName || selectedTargetName.value || '未设定对象')
const currentTargetTypeLabel = computed(() => TARGET_TYPE_LABELS[forecastData.value?.targetType || selectedTargetType.value] || '预测对象')
const currentModelVersion = computed(() => forecastData.value?.modelVersion || 'rule_v2')
const topDrivers = computed(() => forecastData.value?.topDrivers || [])
const forecastSeries = computed(() => forecastData.value?.dailySeries || [])
const comparisonPrimaryKey = computed(() => {
  if (filteredComparisonForecasts.value.some(item => targetKeyOf(item.targetType, item.targetName) === selectedTargetKey.value)) {
    return selectedTargetKey.value
  }
  const fallback = filteredComparisonForecasts.value[0]
  return fallback ? targetKeyOf(fallback.targetType, fallback.targetName) : ''
})
const comparisonSelectionVisible = computed(() => filteredComparisonForecasts.value.some(item => targetKeyOf(item.targetType, item.targetName) === selectedTargetKey.value))
const comparisonSeriesMeta = computed(() => {
  let diseaseIndex = 0
  let pestIndex = 0
  return filteredComparisonForecasts.value.map((item) => {
    const palette = item.targetType === 'PEST' ? PEST_SERIES_COLORS : DISEASE_SERIES_COLORS
    const paletteIndex = item.targetType === 'PEST' ? pestIndex++ : diseaseIndex++
    const color = palette[paletteIndex % palette.length]
    const key = targetKeyOf(item.targetType, item.targetName)
    return {
      key,
      color,
      isSelected: key === selectedTargetKey.value,
      name: targetDisplayName(item.targetType, item.targetName),
      targetName: item.targetName,
      targetType: item.targetType,
      data: (item.dailySeries || []).map(day => Math.round(Number(day.riskScore || 0) * 100))
    }
  })
})
const comparisonXAxis = computed(() => {
  const source = comparisonSelectionVisible.value
    ? forecastSeries.value
    : (filteredComparisonForecasts.value[0]?.dailySeries || [])
  return source.map((item, index) => (index === 0 ? '今天' : item.date))
})
const comparisonTargetCount = computed(() => filteredComparisonForecasts.value.length)
const comparisonTotalTargetCount = computed(() => comparisonForecasts.value.length)
const forecastTrendChartKey = computed(() => `${comparisonViewMode.value}-${comparisonTargetCount.value}-${selectedTargetKey.value}-${comparisonSeriesMeta.value.map(item => item.key).join('|')}`)
const forecastTrendUpdateOptions = { notMerge: true, replaceMerge: ['series', 'legend', 'xAxis'] }
const targetOverviewSeries = computed(() => {
  const diseaseCount = targetOptions.value.diseaseTargets?.length || 0
  const pestCount = targetOptions.value.pestTargets?.length || 0
  const series = [
    { name: '病害对象', value: diseaseCount },
    { name: '虫害对象', value: pestCount }
  ].filter(item => item.value > 0)
  return series.length ? series : [{ name: '暂无对象', value: 1 }]
})
const predictionContextCards = computed(() => ([
  { label: '对象类型', value: currentTargetTypeLabel.value },
  { label: '预测对象', value: currentTargetLabel.value },
  { label: '当前物候期', value: targetContext.value?.stage || '待确认' },
  { label: '地区', value: targetContext.value?.regionLabel || DEFAULT_REGION_LABEL },
  { label: '模型版本', value: currentModelVersion.value },
  { label: '天气压力', value: `${weatherPressure.value.label} ${weatherPressure.value.score}` }
]))

const resolveForecastTarget = () => {
  const crop = activeCrop.value
  if (!crop) {
    return null
  }
  return {
    crop: crop.name,
    targetType: selectedTargetType.value,
    targetName: selectedTargetName.value,
    regionCode: crop.locationId || '',
    regionLabel: [crop.province, crop.city].filter(Boolean).join(' / ') || crop.region || DEFAULT_REGION_LABEL,
    stage: crop.stage || ''
  }
}

const syncSelectedTarget = (options) => {
  const diseaseTargets = options.diseaseTargets || []
  const pestTargets = options.pestTargets || []

  if (selectedTargetType.value === 'DISEASE' && diseaseTargets.includes(selectedTargetName.value)) {
    return
  }
  if (selectedTargetType.value === 'PEST' && pestTargets.includes(selectedTargetName.value)) {
    return
  }

  if (diseaseTargets.length > 0) {
    selectedTargetType.value = 'DISEASE'
    selectedTargetName.value = diseaseTargets[0]
    return
  }
  if (pestTargets.length > 0) {
    selectedTargetType.value = 'PEST'
    selectedTargetName.value = pestTargets[0]
    return
  }
  selectedTargetName.value = ''
}

const syncSelectedForecastFromComparison = () => {
  if (!comparisonForecasts.value.length) {
    forecastData.value = null
    return false
  }
  const selected = comparisonForecasts.value.find(item => targetKeyOf(item.targetType, item.targetName) === selectedTargetKey.value)
  if (selected) {
    forecastData.value = selected
    return true
  }
  forecastData.value = comparisonForecasts.value[0]
  return true
}

const loadTrendTargets = async () => {
  const crop = activeCrop.value
  if (!crop?.name) {
    targetOptions.value = { diseaseTargets: [], pestTargets: [] }
    comparisonForecasts.value = []
    selectedTargetType.value = 'DISEASE'
    selectedTargetName.value = ''
    return
  }

  targetsLoading.value = true
  try {
    const response = await axios.get('/api/trend/targets', { params: { crop: crop.name } })
    const options = {
      diseaseTargets: response.data?.diseaseTargets || [],
      pestTargets: response.data?.pestTargets || []
    }
    targetOptions.value = options
    syncSelectedTarget(options)
    if (!options.diseaseTargets.length && !options.pestTargets.length) {
      forecastError.value = '当前作物暂无可用预测对象。'
    }
  } catch (error) {
    console.error('Failed to load trend targets', error)
    targetOptions.value = { diseaseTargets: [], pestTargets: [] }
    comparisonForecasts.value = []
    selectedTargetName.value = ''
    forecastError.value = '预测对象加载失败，请稍后重试。'
  } finally {
    targetsLoading.value = false
  }
}

const loadRecords = async () => {
  recordsLoading.value = true
  if (!currentUser.value?.userId) {
    records.value = (farmStore.identificationHistory || []).map(normalizeRecord)
    recordsLoading.value = false
    return
  }
  try {
    const response = await axios.get(`/api/record/list?userId=${currentUser.value.userId}`)
    if (response.data?.code === 200) {
      const rows = response.data.data || []
      farmStore.syncIdentificationHistory(rows)
      records.value = rows.map(normalizeRecord)
      return
    }
  } catch (error) {
    console.error('Failed to load PC data center records', error)
  } finally {
    recordsLoading.value = false
  }
  records.value = (farmStore.identificationHistory || []).map(normalizeRecord)
}

const refreshClimate = async () => {
  const target = resolveForecastTarget()
  targetContext.value = target
  if (!target) {
    forecastData.value = null
    comparisonForecasts.value = []
    weatherFeatures.value = null
    forecastError.value = '请先在我的农场中设置当前作物。'
    weatherError.value = ''
    return
  }
  if (!target.regionCode) {
    forecastData.value = null
    comparisonForecasts.value = []
    weatherFeatures.value = null
    forecastError.value = '请先在农场档案中补充地区信息。'
    weatherError.value = ''
    return
  }
  if (!target.stage) {
    forecastData.value = null
    comparisonForecasts.value = []
    weatherFeatures.value = null
    forecastError.value = '请先在农场档案中确认当前生效物候期。'
    weatherError.value = ''
    return
  }
  if (!allTargetDefinitions.value.length) {
    forecastData.value = null
    comparisonForecasts.value = []
    weatherFeatures.value = null
    forecastError.value = '当前作物暂无可用预测对象。'
    weatherError.value = ''
    return
  }
  forecastLoading.value = true
  weatherLoading.value = true
  forecastError.value = ''
  weatherError.value = ''
  await Promise.all([
    Promise.allSettled(allTargetDefinitions.value.map((definition) => axios.post('/api/trend/forecast', {
      crop: target.crop,
      disease: definition.targetType === 'DISEASE' ? definition.targetName : null,
      targetType: definition.targetType,
      targetName: definition.targetName,
      regionCode: target.regionCode,
      targetDate: new Date().toISOString().slice(0, 10),
      forecastDays: 7,
      phenologyStage: target.stage,
      reportRiskHint: 0
    }))).then((results) => {
      const successful = results.flatMap((result, index) => {
        const definition = allTargetDefinitions.value[index]
        if (result.status !== 'fulfilled') {
          console.error('Failed to fetch forecast for target', definition, result.reason)
          return []
        }
        const data = result.value.data || null
        if (!data || data.supported === false) {
          return []
        }
        return [{
          ...data,
          targetType: data.targetType || definition.targetType,
          targetName: data.targetName || definition.targetName
        }]
      })
      comparisonForecasts.value = successful
      if (!successful.length) {
        forecastData.value = null
        forecastError.value = '当前作物暂无可用预测结果。'
        return
      }
      syncSelectedForecastFromComparison()
    }).catch((error) => {
      console.error('Failed to fetch PC trend forecast collection', error)
      comparisonForecasts.value = []
      forecastData.value = null
      forecastError.value = '趋势预测服务暂时不可用，请稍后重试。'
    }).finally(() => {
      forecastLoading.value = false
    }),
    axios.get(`/api/weather/features?locationId=${encodeURIComponent(target.regionCode)}`).then((response) => {
      weatherFeatures.value = response.data || null
    }).catch((error) => {
      console.error('Failed to fetch weather features for PC data center', error)
      weatherFeatures.value = null
      weatherError.value = '天气特征暂时不可用。'
    }).finally(() => {
      weatherLoading.value = false
    })
  ])
}

const refreshDashboard = async () => {
  currentUser.value = getStoredUser()
  await farmStore.initialize({ force: true })
  await loadRecords()
  await loadTrendTargets()
  await refreshClimate()
  loading.value = false
}

watch(() => farmStore.activeCrop?.name, async () => {
  if (loading.value) return
  await loadTrendTargets()
  await refreshClimate()
})

watch(() => [farmStore.activeCropId, farmStore.activeCrop?.locationId, farmStore.activeCrop?.stage], () => {
  if (loading.value || targetsLoading.value) return
  void refreshClimate()
})

watch(() => [selectedTargetType.value, selectedTargetName.value], () => {
  if (loading.value || targetsLoading.value) return
  if (syncSelectedForecastFromComparison()) {
    forecastError.value = ''
    return
  }
  void refreshClimate()
})

onMounted(async () => {
  themeObserver = new MutationObserver(() => {
    isDark.value = document.documentElement.classList.contains('dark')
  })
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
  await refreshDashboard()
})

onBeforeUnmount(() => {
  themeObserver?.disconnect()
})

const activityTrendOption = computed(() => ({
  backgroundColor: 'transparent',
  tooltip: { trigger: 'axis', backgroundColor: isDark.value ? '#020617' : '#ffffff', borderColor: isDark.value ? '#1e293b' : '#e2e8f0', textStyle: { color: isDark.value ? '#e2e8f0' : '#334155' } },
  grid: { left: 40, right: 20, top: 24, bottom: 28 },
  xAxis: { type: 'category', data: activityTrend.value.map(item => item.label), axisLine: { lineStyle: { color: isDark.value ? '#334155' : '#e2e8f0' } }, axisLabel: { color: isDark.value ? '#94a3b8' : '#64748b' } },
  yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: isDark.value ? '#1e293b' : '#e2e8f0', type: 'dashed' } }, axisLabel: { color: isDark.value ? '#94a3b8' : '#64748b' } },
  series: [{ type: 'line', smooth: true, symbolSize: 8, data: activityTrend.value.map(item => item.value), lineStyle: { width: 3, color: '#10b981' }, itemStyle: { color: '#10b981' }, areaStyle: { color: 'rgba(16,185,129,0.14)' } }]
}))

const diseaseDistributionOption = computed(() => ({
  backgroundColor: 'transparent',
  tooltip: { trigger: 'item', backgroundColor: isDark.value ? '#020617' : '#ffffff', borderColor: isDark.value ? '#1e293b' : '#e2e8f0', textStyle: { color: isDark.value ? '#e2e8f0' : '#334155' } },
  legend: { bottom: 0, textStyle: { color: isDark.value ? '#94a3b8' : '#64748b', fontSize: 12 } },
  series: [{ type: 'pie', radius: ['48%', '74%'], center: ['50%', '42%'], label: { show: false }, data: diseaseDistribution.value.length ? diseaseDistribution.value : [{ name: '暂无识别对象分布', value: 1 }], color: ['#10b981', '#3b82f6', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4'] }]
}))

const confidenceBucketOption = computed(() => ({
  backgroundColor: 'transparent',
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, backgroundColor: isDark.value ? '#020617' : '#ffffff', borderColor: isDark.value ? '#1e293b' : '#e2e8f0', textStyle: { color: isDark.value ? '#e2e8f0' : '#334155' } },
  grid: { left: 24, right: 16, top: 24, bottom: 32 },
  xAxis: { type: 'category', data: confidenceBuckets.value.map(item => item.label), axisLine: { lineStyle: { color: isDark.value ? '#334155' : '#e2e8f0' } }, axisLabel: { color: isDark.value ? '#94a3b8' : '#64748b' } },
  yAxis: { type: 'value', minInterval: 1, splitLine: { lineStyle: { color: isDark.value ? '#1e293b' : '#e2e8f0', type: 'dashed' } }, axisLabel: { color: isDark.value ? '#94a3b8' : '#64748b' } },
  series: [{ type: 'bar', barWidth: 30, data: confidenceBuckets.value.map(item => ({ value: item.value, itemStyle: { color: item.color, borderRadius: [10, 10, 0, 0] } })), label: { show: true, position: 'top', color: isDark.value ? '#e2e8f0' : '#334155', fontWeight: 700 } }]
}))

const forecastTrendOption = computed(() => ({
  backgroundColor: 'transparent',
  color: comparisonSeriesMeta.value.map(item => item.color),
  tooltip: { trigger: 'axis', backgroundColor: isDark.value ? '#020617' : '#ffffff', borderColor: isDark.value ? '#1e293b' : '#e2e8f0', textStyle: { color: isDark.value ? '#e2e8f0' : '#334155' } },
  legend: {
    top: 0,
    type: 'scroll',
    textStyle: { color: isDark.value ? '#94a3b8' : '#64748b', fontSize: 12 },
    pageTextStyle: { color: isDark.value ? '#94a3b8' : '#64748b' }
  },
  grid: { left: 40, right: 24, top: 58, bottom: 28 },
  xAxis: { type: 'category', data: comparisonXAxis.value, axisLine: { lineStyle: { color: isDark.value ? '#334155' : '#e2e8f0' } }, axisLabel: { color: isDark.value ? '#94a3b8' : '#64748b' } },
  yAxis: { type: 'value', min: 0, max: 100, splitLine: { lineStyle: { color: isDark.value ? '#1e293b' : '#e2e8f0', type: 'dashed' } }, axisLabel: { color: isDark.value ? '#94a3b8' : '#64748b', formatter: '{value}%' } },
  series: comparisonSeriesMeta.value.map(item => ({
    name: item.name,
    type: 'line',
    smooth: true,
    symbolSize: item.isSelected ? 9 : 6,
    data: item.data,
    lineStyle: { width: item.isSelected ? 4 : 2.5, color: item.color, opacity: item.isSelected ? 1 : 0.82 },
    itemStyle: { color: item.color },
    areaStyle: item.isSelected ? { color: item.color, opacity: 0.12 } : undefined,
    emphasis: { focus: 'series' },
    z: item.isSelected ? 10 : 3,
    markLine: item.key === comparisonPrimaryKey.value ? {
      silent: true,
      symbol: 'none',
      label: { show: false },
      data: [
        { yAxis: 75, lineStyle: { color: '#ef4444', type: 'dashed' } },
        { yAxis: 55, lineStyle: { color: '#f59e0b', type: 'dashed' } }
      ]
    } : undefined
  }))
}))

const forecastStructureOption = computed(() => ({
  backgroundColor: 'transparent',
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, backgroundColor: isDark.value ? '#020617' : '#ffffff', borderColor: isDark.value ? '#1e293b' : '#e2e8f0', textStyle: { color: isDark.value ? '#e2e8f0' : '#334155' } },
  grid: { left: 36, right: 16, top: 18, bottom: 28 },
  xAxis: { type: 'category', data: forecastSeries.value.map((item, index) => (index === 0 ? '今天' : item.date)), axisLine: { lineStyle: { color: isDark.value ? '#334155' : '#e2e8f0' } }, axisLabel: { color: isDark.value ? '#94a3b8' : '#64748b' } },
  yAxis: { type: 'value', min: 0, max: 100, splitLine: { lineStyle: { color: isDark.value ? '#1e293b' : '#e2e8f0', type: 'dashed' } }, axisLabel: { color: isDark.value ? '#94a3b8' : '#64748b', formatter: '{value}%' } },
  series: [{
    type: 'bar',
    barWidth: 26,
    data: forecastSeries.value.map(item => {
      const score = Number(item.riskScore || 0)
      return {
        value: Math.round(score * 100),
        itemStyle: {
          color: riskMetaOf(score).color,
          borderRadius: [10, 10, 0, 0]
        }
      }
    }),
    label: { show: true, position: 'top', color: isDark.value ? '#e2e8f0' : '#334155', fontWeight: 700, formatter: '{c}%' }
  }]
}))

const targetOverviewOption = computed(() => ({
  backgroundColor: 'transparent',
  tooltip: { trigger: 'item', backgroundColor: isDark.value ? '#020617' : '#ffffff', borderColor: isDark.value ? '#1e293b' : '#e2e8f0', textStyle: { color: isDark.value ? '#e2e8f0' : '#334155' } },
  legend: { bottom: 0, textStyle: { color: isDark.value ? '#94a3b8' : '#64748b', fontSize: 12 } },
  series: [{
    type: 'pie',
    radius: ['48%', '74%'],
    center: ['50%', '42%'],
    label: { formatter: '{b}\n{c}', color: isDark.value ? '#e2e8f0' : '#334155', fontWeight: 700 },
    data: targetOverviewSeries.value,
    color: ['#0ea5e9', '#10b981', '#94a3b8']
  }]
}))
</script>

<template>
  <div class="pc-data-page custom-scrollbar h-full overflow-y-auto bg-slate-50/50 p-8 dark:bg-slate-950/[0.35]">
    <div class="mb-8 flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
      <div>
        <h2 class="text-3xl font-black tracking-tight text-slate-900 dark:text-slate-100">数据中心</h2>
      </div>
      <button class="inline-flex items-center justify-center rounded-2xl bg-slate-950 px-5 py-3 text-sm font-bold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-60 dark:bg-emerald-400 dark:text-slate-950 dark:hover:bg-emerald-300" :disabled="isRefreshing" @click="refreshDashboard">
        {{ isRefreshing ? '刷新中...' : '刷新看板' }}
      </button>
    </div>

    <div v-if="loading" class="flex h-[60vh] items-center justify-center text-sm font-bold text-slate-400">
      正在同步农场档案与专业看板数据...
    </div>

    <div v-else class="space-y-6">
      <div class="grid grid-cols-1 gap-6 xl:grid-cols-[1.15fr_0.85fr]">
        <section class="rounded-[2rem] border border-slate-100 bg-gradient-to-br from-slate-950 via-slate-900 to-sky-900 p-7 text-white shadow-[0_28px_90px_rgba(15,23,42,0.2)] dark:border-slate-800">
          <div class="flex flex-wrap items-center gap-2">
            <span class="rounded-full bg-white/10 px-3 py-1 text-xs font-bold tracking-[0.18em] text-white/80">作物视角</span>
            <span class="rounded-full bg-white/10 px-3 py-1 text-xs font-bold text-white/80">{{ cropScopeLabel }}</span>
            <span class="rounded-full bg-white/15 px-3 py-1 text-xs font-bold text-white">{{ todayRiskMeta.label }}</span>
            <span class="rounded-full bg-white/15 px-3 py-1 text-xs font-bold text-white">天气{{ weatherPressure.label }}</span>
          </div>
          <div class="mt-6 grid grid-cols-1 gap-6 lg:grid-cols-[1fr_0.9fr]">
            <div>
              <div class="text-sm font-bold uppercase tracking-[0.18em] text-white/[0.55]">Risk Outlook</div>
              <div class="mt-3 text-5xl font-black" :class="todayRiskMeta.text.replace('500', '300')">{{ Math.round(todayRiskScore * 100) }}%</div>
              <p class="mt-3 max-w-xl text-sm text-white/70">
                当前趋势 {{ forecastData?.trendDirection || '平稳' }}，未来 7 天高风险日 {{ highRiskDays }} 天，区域 {{ targetContext?.regionLabel || DEFAULT_REGION_LABEL }}。
              </p>
              <div class="mt-5 flex flex-wrap gap-3">
                <div class="rounded-2xl bg-white/[0.08] px-4 py-3 ring-1 ring-white/10">
                  <div class="text-xs text-white/[0.55]">识别热点</div>
                  <div class="mt-2 text-sm font-black">{{ diseaseDistribution[0]?.name || '暂无热点' }}</div>
                </div>
                <div class="rounded-2xl bg-white/[0.08] px-4 py-3 ring-1 ring-white/10">
                  <div class="text-xs text-white/[0.55]">平均置信度</div>
                  <div class="mt-2 text-sm font-black">{{ Math.round(averageConfidence * 100) }}%</div>
                </div>
              </div>
            </div>
            <div class="rounded-[1.75rem] bg-white/[0.08] p-5 ring-1 ring-white/10">
              <div class="text-xs font-bold uppercase tracking-[0.18em] text-white/[0.55]">预测上下文</div>
              <div class="mt-3 text-lg font-black">{{ targetContext?.crop || '未设定作物' }}</div>
              <div class="mt-4">
                <div class="text-[11px] font-bold text-white/55 mb-2">预测对象</div>
                <div class="flex flex-wrap gap-2 mb-2">
                  <button
                    v-for="targetType in ['DISEASE', 'PEST']"
                    :key="targetType"
                    @click="selectedTargetType = targetType"
                    :disabled="targetType === 'DISEASE' ? !targetOptions.diseaseTargets.length : !targetOptions.pestTargets.length"
                    class="rounded-full px-3 py-1 text-xs font-bold transition disabled:opacity-40 disabled:cursor-not-allowed"
                    :class="selectedTargetType === targetType ? 'bg-white text-slate-950' : 'bg-white/10 text-white/80'"
                  >
                    {{ TARGET_TYPE_LABELS[targetType] }}
                  </button>
                </div>
                <div class="flex flex-wrap gap-2">
                  <button
                    v-for="targetName in availableTargetNames"
                    :key="targetName"
                    @click="selectedTargetName = targetName"
                    class="rounded-full px-3 py-1 text-xs font-bold transition"
                    :class="selectedTargetName === targetName ? 'bg-emerald-400 text-slate-950' : 'bg-white/10 text-white/80'"
                  >
                    {{ targetName }}
                  </button>
                </div>
              </div>
              <div class="mt-4 grid grid-cols-1 gap-3 text-sm text-white/75">
                <div v-for="item in predictionContextCards" :key="item.label" class="flex items-center justify-between gap-4 rounded-2xl bg-black/10 px-4 py-3">
                  <span>{{ item.label }}</span>
                  <span class="font-bold text-white">{{ item.value }}</span>
                </div>
              </div>
            </div>
          </div>
        </section>

        <PCFarmManager compact title="农场管理" subtitle="在数据中心内直接新增、编辑、删除作物并设为当前，所有图表会立即按当前作物联动。" />
      </div>

      <div class="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-4">
        <article v-for="item in summaryCards" :key="item.label" class="pc-data-panel rounded-[1.75rem] border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900/[0.88]">
          <div class="text-xs font-bold uppercase tracking-[0.18em] text-slate-400">{{ item.label }}</div>
          <div class="mt-3 text-3xl font-black text-slate-900 dark:text-slate-100">{{ item.value }}</div>
          <div class="mt-2 text-sm text-slate-500 dark:text-slate-400">{{ item.desc }}</div>
        </article>
      </div>

      <div class="grid grid-cols-1 gap-6 xl:grid-cols-3">
        <section class="pc-data-panel pc-data-chart rounded-[2rem] border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900/[0.88]">
          <div class="mb-4 flex items-end justify-between gap-4"><div><h3 class="text-xl font-black text-slate-900 dark:text-slate-100">14 天识别趋势</h3><p class="mt-2 text-sm text-slate-500 dark:text-slate-400">按当前作物过滤后的记录日趋势。</p></div><span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600 dark:bg-slate-800 dark:text-slate-300">{{ filteredRecords.length }} 条</span></div>
          <VChart :option="activityTrendOption" autoresize style="height: 300px;" />
        </section>

        <section class="pc-data-panel pc-data-chart rounded-[2rem] border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900/[0.88]">
          <div class="mb-4 flex items-end justify-between gap-4"><div><h3 class="text-xl font-black text-slate-900 dark:text-slate-100">识别对象分布</h3><p class="mt-2 text-sm text-slate-500 dark:text-slate-400">热点识别对象占比。</p></div><span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600 dark:bg-slate-800 dark:text-slate-300">{{ diseaseDistribution.length ? `Top ${diseaseDistribution.length}` : '暂无数据' }}</span></div>
          <VChart :option="diseaseDistributionOption" autoresize style="height: 300px;" />
        </section>

        <section class="pc-data-panel pc-data-chart rounded-[2rem] border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900/[0.88]">
          <div class="mb-4 flex items-end justify-between gap-4"><div><h3 class="text-xl font-black text-slate-900 dark:text-slate-100">预测对象概览</h3><p class="mt-2 text-sm text-slate-500 dark:text-slate-400">当前作物支持的病害与虫害对象结构。</p></div><span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600 dark:bg-slate-800 dark:text-slate-300">{{ (targetOptions.diseaseTargets?.length || 0) + (targetOptions.pestTargets?.length || 0) }} 个</span></div>
          <VChart :option="targetOverviewOption" autoresize style="height: 300px;" />
        </section>
      </div>

      <div class="grid grid-cols-1 gap-6 xl:grid-cols-[0.88fr_1.12fr]">
        <section class="pc-data-panel pc-data-chart rounded-[2rem] border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900/[0.88]">
          <div class="mb-4 flex items-end justify-between gap-4"><div><h3 class="text-xl font-black text-slate-900 dark:text-slate-100">置信度分析</h3><p class="mt-2 text-sm text-slate-500 dark:text-slate-400">识别结果可信区间结构。</p></div><span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600 dark:bg-slate-800 dark:text-slate-300">平均 {{ Math.round(averageConfidence * 100) }}%</span></div>
          <VChart :option="confidenceBucketOption" autoresize style="height: 300px;" />
          <div class="mt-4 grid grid-cols-1 gap-3 md:grid-cols-3">
            <div v-for="bucket in confidenceBuckets" :key="bucket.key" class="pc-data-soft rounded-2xl border border-slate-100 bg-slate-50 px-4 py-4 dark:border-slate-800 dark:bg-slate-950/[0.55]">
              <div class="text-xs font-bold uppercase tracking-[0.16em] text-slate-400">{{ bucket.label }}</div>
              <div class="mt-2 text-lg font-black text-slate-900 dark:text-slate-100">{{ bucket.value }}</div>
              <div class="mt-1 text-xs text-slate-500 dark:text-slate-400">{{ bucket.range }}</div>
            </div>
          </div>
        </section>

        <section class="pc-data-panel pc-data-chart rounded-[2rem] border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900/[0.88]">
          <div class="mb-4 flex items-end justify-between gap-4">
            <div>
              <h3 class="text-xl font-black text-slate-900 dark:text-slate-100">7 天风险对比预测</h3>
              <p class="mt-2 text-sm text-slate-500 dark:text-slate-400">同一张折线图对比当前作物下全部病害与虫害对象的未来 7 天风险变化。</p>
            </div>
            <div class="flex flex-wrap items-center justify-end gap-2">
              <div class="flex flex-wrap gap-2">
                <button
                  v-for="option in COMPARISON_FILTER_OPTIONS"
                  :key="option.key"
                  @click="comparisonViewMode = option.key"
                  class="rounded-full px-3 py-1 text-xs font-bold transition"
                  :class="comparisonViewMode === option.key ? 'bg-slate-900 text-white dark:bg-emerald-400 dark:text-slate-950' : 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300'"
                >
                  {{ option.label }}
                </button>
              </div>
              <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600 dark:bg-slate-800 dark:text-slate-300">{{ comparisonTargetCount }}/{{ comparisonTotalTargetCount }} 个对象</span>
              <span class="rounded-full px-3 py-1 text-xs font-bold" :class="todayRiskMeta.chip">当前选中今日 {{ Math.round(todayRiskScore * 100) }}%</span>
            </div>
          </div>
          <div v-if="forecastLoading || targetsLoading" class="flex h-[300px] items-center justify-center text-sm font-bold text-slate-400">{{ targetsLoading ? '正在加载预测对象...' : '正在生成全部对象的未来 7 天风险对比...' }}</div>
          <div v-else-if="forecastError" class="flex h-[300px] items-center justify-center"><div class="rounded-3xl border border-amber-200 bg-amber-50 px-6 py-5 text-center text-sm font-bold text-amber-700 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-200">{{ forecastError }}</div></div>
          <div v-else class="space-y-5">
            <VChart
              v-if="comparisonTargetCount"
              :key="forecastTrendChartKey"
              :option="forecastTrendOption"
              :update-options="forecastTrendUpdateOptions"
              autoresize
              style="height: 300px;"
            />
            <div v-else class="flex h-[300px] items-center justify-center rounded-[1.5rem] border border-dashed border-slate-200 bg-slate-50 text-sm font-bold text-slate-400 dark:border-slate-700 dark:bg-slate-950/[0.55] dark:text-slate-500">
              当前筛选下暂无可展示的预测对象。
            </div>
            <div v-if="comparisonSelectionVisible" class="rounded-[1.25rem] border border-sky-100 bg-sky-50 px-4 py-3 text-sm text-sky-700 dark:border-sky-500/30 dark:bg-sky-500/10 dark:text-sky-100">
              当前高亮对象为 <span class="font-bold">{{ currentTargetTypeLabel }} · {{ currentTargetLabel }}</span>。下方结构图、预警提示和主要驱动因子会跟随当前选中的预测对象更新。
            </div>
            <div v-else class="rounded-[1.25rem] border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-100">
              当前选中的 <span class="font-bold">{{ currentTargetTypeLabel }} · {{ currentTargetLabel }}</span> 不在本次图表筛选范围内，折线图仅展示 {{ comparisonViewMode === 'DISEASE' ? '病害' : comparisonViewMode === 'PEST' ? '虫害' : '全部对象' }}。
            </div>
            <div class="grid grid-cols-1 gap-4 xl:grid-cols-[0.95fr_1.05fr]">
              <div class="pc-data-soft rounded-[1.5rem] border border-slate-100 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-950/[0.55]">
                <div class="mb-3 flex items-center justify-between gap-3">
                  <div>
                    <div class="text-sm font-black text-slate-900 dark:text-slate-100">当前选中对象风险结构图</div>
                    <div class="mt-1 text-xs text-slate-500 dark:text-slate-400">按日拆解 {{ currentTargetLabel }} 的未来 7 天风险等级结构。</div>
                  </div>
                  <span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600 dark:bg-slate-800 dark:text-slate-300">{{ forecastSeries.length }} 天</span>
                </div>
                <VChart :option="forecastStructureOption" autoresize style="height: 220px;" />
              </div>
              <div class="grid grid-cols-1 gap-4">
                <div class="rounded-[1.5rem] border border-amber-200 bg-amber-50 px-4 py-4 dark:border-amber-500/30 dark:bg-amber-500/10">
                  <div class="text-sm font-black text-amber-800 dark:text-amber-200">预警提示</div>
                  <div v-if="forecastWarnings.length" class="mt-3 space-y-2 text-sm text-amber-700 dark:text-amber-100">
                    <div v-for="warning in forecastWarnings" :key="warning">{{ warning }}</div>
                  </div>
                  <div v-else class="mt-3 text-sm text-amber-700/80 dark:text-amber-100/80">当前没有额外预警，建议按常规频率巡检。</div>
                </div>
                <div class="pc-data-soft rounded-[1.5rem] border border-slate-100 bg-slate-50 px-4 py-4 dark:border-slate-800 dark:bg-slate-950/[0.55]">
                  <div class="text-sm font-black text-slate-900 dark:text-slate-100">主要驱动因子</div>
                  <div v-if="topDrivers.length" class="mt-3 flex flex-wrap gap-2">
                    <span v-for="driver in topDrivers" :key="driver" class="rounded-full bg-slate-900 px-3 py-1.5 text-xs font-bold text-white dark:bg-emerald-400 dark:text-slate-950">
                      {{ driver }}
                    </span>
                  </div>
                  <div v-else class="mt-3 text-sm text-slate-500 dark:text-slate-400">当前暂无驱动因子摘要。</div>
                </div>
              </div>
            </div>
          </div>
        </section>
      </div>

      <div class="grid grid-cols-1 gap-6 xl:grid-cols-[0.92fr_1.08fr]">
        <section class="pc-data-panel rounded-[2rem] border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900/[0.88]">
          <div class="flex items-end justify-between gap-4"><div><h3 class="text-xl font-black text-slate-900 dark:text-slate-100">天气摘要</h3><p class="mt-2 text-sm text-slate-500 dark:text-slate-400">由天气特征接口与趋势预测摘要共同驱动。</p></div><span class="rounded-full px-3 py-1 text-xs font-bold" :class="weatherPressure.chip">{{ weatherPressure.label }} {{ weatherPressure.score }}</span></div>
          <div v-if="weatherError" class="mt-4 rounded-2xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-700 dark:border-amber-500/30 dark:bg-amber-500/10 dark:text-amber-200">{{ weatherError }}</div>
          <div class="mt-5 grid grid-cols-1 gap-4 sm:grid-cols-2">
            <article v-for="card in weatherCards" :key="card.label" class="pc-data-soft rounded-[1.5rem] border border-slate-100 bg-slate-50 p-5 dark:border-slate-800 dark:bg-slate-950/[0.55]">
              <div class="text-xs font-bold uppercase tracking-[0.16em] text-slate-400">{{ card.label }}</div>
              <div class="mt-3 text-2xl font-black text-slate-900 dark:text-slate-100">{{ card.value }}</div>
            </article>
          </div>
          <div class="pc-data-soft mt-5 rounded-[1.5rem] border border-slate-100 bg-slate-50 p-5 text-sm leading-relaxed text-slate-600 dark:border-slate-800 dark:bg-slate-950/[0.55] dark:text-slate-300">
            {{ weatherPressure.desc }} 当前关注作物为 {{ targetContext?.crop || '未设定' }}，当前预测对象为 {{ currentTargetLabel }}。
          </div>
        </section>

        <section class="pc-data-panel rounded-[2rem] border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-900/[0.88]">
          <div class="flex items-end justify-between gap-4"><div><h3 class="text-xl font-black text-slate-900 dark:text-slate-100">最近识别记录</h3><p class="mt-2 text-sm text-slate-500 dark:text-slate-400">只展示当前作物视角下的最新识别结果。</p></div><span class="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600 dark:bg-slate-800 dark:text-slate-300">{{ cropScopeLabel }}</span></div>
          <div v-if="!recentRecords.length" class="pc-data-soft mt-6 rounded-[1.75rem] border border-dashed border-slate-200 bg-slate-50 px-6 py-16 text-center text-slate-400 dark:border-slate-700 dark:bg-slate-950/[0.55]">当前作物下暂无识别记录。</div>
          <div v-else class="mt-5 space-y-4">
            <article v-for="item in recentRecords" :key="item.id" class="pc-data-soft flex items-center gap-4 rounded-[1.5rem] border border-slate-100 bg-slate-50 p-4 dark:border-slate-800 dark:bg-slate-950/[0.55]">
              <div class="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-2xl bg-slate-100 text-xs font-bold text-slate-500 dark:bg-slate-800 dark:text-slate-300"><img v-if="item.imageUrl" :src="item.imageUrl" class="h-full w-full object-cover" /><span v-else>Leaf</span></div>
              <div class="min-w-0 flex-1">
                <div class="flex flex-col gap-2 md:flex-row md:items-center md:justify-between"><div class="truncate text-base font-black text-slate-900 dark:text-slate-100">{{ item.pestName }}</div><div class="text-xs text-slate-400">{{ formatDate(item.createTime, true) }}</div></div>
                <div class="mt-2 flex flex-wrap items-center gap-2">
                  <span class="rounded-full px-2.5 py-1 text-xs font-bold" :class="riskMetaOf(item.confidence).chip">置信度 {{ Math.round(item.confidence * 100) }}%</span>
                  <span v-if="item.cropName" class="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-700 dark:bg-slate-800 dark:text-slate-300">{{ item.cropName }}</span>
                  <span v-if="item.region || item.city" class="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-700 dark:bg-slate-800 dark:text-slate-300">{{ item.region || item.city }}</span>
                </div>
              </div>
            </article>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.custom-scrollbar::-webkit-scrollbar { width: 6px; }
.custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
.custom-scrollbar::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 9999px; }
.custom-scrollbar::-webkit-scrollbar-thumb:hover { background: #94a3b8; }
:global(.dark .custom-scrollbar::-webkit-scrollbar-thumb) { background: #334155; }
:global(.dark .custom-scrollbar::-webkit-scrollbar-thumb:hover) { background: #475569; }

.pc-data-panel {
  background: rgba(255, 255, 255, 0.96);
  border-color: rgba(226, 232, 240, 0.92);
  box-shadow: 0 18px 44px rgba(15, 23, 42, 0.08);
}

.pc-data-soft {
  background: rgba(248, 250, 252, 0.96);
  border-color: rgba(226, 232, 240, 0.9);
}

:global(.dark .pc-data-page) {
  color-scheme: dark;
}

:global(.dark .pc-data-panel) {
  background: rgba(15, 23, 42, 0.88);
  border-color: rgba(51, 65, 85, 0.88);
  box-shadow: 0 22px 52px rgba(2, 6, 23, 0.34);
}

:global(.dark .pc-data-soft) {
  background: rgba(2, 6, 23, 0.55);
  border-color: rgba(51, 65, 85, 0.82);
}

:global(.dark .pc-data-chart canvas),
:global(.dark .pc-data-chart svg) {
  background: transparent !important;
}
</style>
