<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { recordApi, trendApi } from '../../api/index.js'
import RecordDetailSheet from '../../components/RecordDetailSheet.vue'
import { useFarmStore } from '../../stores/farmStore'
import { resolveEffectiveStage } from '../../stores/farmUtils'
import DataCenterFarmDrawer from '../../components/pc/DataCenterFarmDrawer.vue'
import { createDataCenterCharts } from '../../utils/dataCenterCharts.js'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import { GraphicComponent, GridComponent, LegendComponent, MarkLineComponent, TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'

use([CanvasRenderer, PieChart, BarChart, LineChart, GraphicComponent, GridComponent, LegendComponent, MarkLineComponent, TooltipComponent])

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
const selectedRecord = ref(null)
const farmDrawerOpen = ref(false)
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
  yoloUsed: item.yoloUsed ?? null,
  reviewRequired: item.reviewRequired ?? null,
  reviewResult: item.reviewResult ?? null,
  reviewError: item.reviewError ?? null,
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
  return series
})
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
    stage: resolveEffectiveStage(crop)
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
    const response = await trendApi.targets(crop.name)
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
    const response = await recordApi.list(currentUser.value.userId)
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
    Promise.allSettled(allTargetDefinitions.value.map((definition) => trendApi.forecast({
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
    trendApi.weatherFeatures(target.regionCode).then((response) => {
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

watch(() => [farmStore.activeCropId, farmStore.activeCrop?.locationId, resolveEffectiveStage(farmStore.activeCrop || {})], () => {
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

const chartOptions = computed(() => createDataCenterCharts({
  dark: isDark.value, activity: activityTrend.value, distribution: diseaseDistribution.value,
  buckets: confidenceBuckets.value, comparisons: comparisonSeriesMeta.value, dates: comparisonXAxis.value,
  primaryKey: comparisonPrimaryKey.value, forecast: forecastSeries.value, overview: targetOverviewSeries.value
}))
const weatherAvailable = computed(() => Boolean(weatherFeatures.value || forecastData.value?.weatherSummary))
const weatherValue = (value, unit = '') => weatherAvailable.value ? `${Math.round(value)}${unit}` : '—'
const inlineTargets = computed(() => allTargetDefinitions.value.filter(item => comparisonViewMode.value === 'ALL' || item.targetType === comparisonViewMode.value))
const selectTarget = item => { selectedTargetType.value = item.targetType; selectedTargetName.value = item.targetName }
const metricItems = computed(() => [
  { label: '今日风险指数', value: forecastData.value ? `${Math.round(todayRiskScore.value * 100)}%` : '—', hint: forecastData.value ? todayRiskMeta.value.label : '暂无预测', color: forecastData.value ? todayRiskMeta.value.color : '' },
  { label: '近 7 天高风险日', value: forecastData.value ? String(highRiskDays.value) : '—', hint: '天 / 7 天' },
  { label: '当前作物记录', value: String(filteredRecords.value.length), hint: filteredRecords.value.length ? `均信 ${Math.round(averageConfidence.value * 100)}%` : '暂无记录' },
  { label: '档案与监测对象', value: String(farmStore.crops.length), hint: `作物 · ${allTargetDefinitions.value.length} 对象` },
  { label: '天气压力指数', value: weatherAvailable.value ? String(weatherPressure.value.score) : '—', hint: weatherAvailable.value ? weatherPressure.value.label : '暂无天气' },
  { label: '3 日均湿 / 均温', value: weatherValue(weatherSummary.value.humidityMean3d, '%'), hint: weatherValue(weatherSummary.value.tempMean3d, '°C') },
  { label: '7 日降雨 / 连雨', value: weatherValue(weatherSummary.value.rainSum7d, 'mm'), hint: weatherValue(weatherSummary.value.consecutiveRainDays, ' 天') },
  { label: '当前物候期', value: activeCrop.value ? resolveEffectiveStage(activeCrop.value) || '待确认' : '—', hint: activeCrop.value?.city || activeCrop.value?.region || '未设置地区', small: true }
])
const contextItems = computed(() => [
  { label: '对象类型', value: currentTargetTypeLabel.value }, { label: '预测对象', value: currentTargetLabel.value },
  { label: '当前物候期', value: targetContext.value?.stage || '待确认' }, { label: '地区', value: targetContext.value?.regionLabel || '未设置' },
  { label: '模型版本', value: forecastData.value?.modelVersion || '—' },
  { label: '天气压力', value: weatherAvailable.value ? `${weatherPressure.value.label} ${weatherPressure.value.score}` : '—' },
  { label: '3 日均湿', value: weatherValue(weatherSummary.value.humidityMean3d, '%') }, { label: '3 日均温', value: weatherValue(weatherSummary.value.tempMean3d, '°C') },
  { label: '7 日降雨', value: weatherValue(weatherSummary.value.rainSum7d, 'mm') }, { label: '连雨天数', value: weatherValue(weatherSummary.value.consecutiveRainDays, ' 天') }
])
</script>

<template>
  <div class="pc-data-page pc-page-scroll dc-minimal" :aria-busy="isRefreshing || loading">
    <div class="pc-content-frame dc-content">
      <header class="dc-toolbar">
        <div class="dc-heading"><span class="dc-status-dot" aria-hidden="true"></span><h2>数据中心</h2></div>
        <div class="dc-crops" aria-label="当前作物"><span class="dc-muted">当前作物</span><button v-for="crop in farmStore.crops" :key="crop.id" type="button" :aria-pressed="String(farmStore.activeCropId) === String(crop.id)" @click="farmStore.setActiveCrop(crop.id)">{{ crop.name }}</button><span v-if="!farmStore.crops.length" class="dc-muted">暂无作物</span></div>
        <button type="button" class="dc-button" @click="farmDrawerOpen = true">农场档案管理 <span class="dc-muted">({{ farmStore.crops.length }})</span></button>
        <button type="button" class="dc-button dc-refresh" :disabled="isRefreshing" @click="refreshDashboard">{{ isRefreshing ? '刷新中...' : '刷新看板' }}</button>
      </header>

      <section class="dc-metrics" aria-label="数据概览">
        <div v-for="item in metricItems" :key="item.label" class="dc-metric"><div class="dc-metric-label">{{ item.label }}</div><div class="dc-metric-values"><strong :class="{ 'dc-small-value': item.small }" :style="item.color ? { color: item.color } : undefined">{{ item.value }}</strong><span>{{ item.hint }}</span></div></div>
      </section>

      <div v-if="farmStore.syncError" class="dc-notice" role="status">{{ farmStore.syncError }}</div>
      <main class="dc-main" aria-label="作物监测看板">
        <div class="dc-column dc-column-risk">
          <section class="dc-panel dc-forecast" aria-label="7 天风险对比预测">
            <div class="dc-panel-title"><h3>7 天风险对比预测</h3><span class="dc-muted dc-count">{{ comparisonTargetCount }}/{{ allTargetDefinitions.length }} 对象</span><div class="dc-filter" aria-label="预测类型"><button v-for="mode in COMPARISON_FILTER_OPTIONS" :key="mode.key" type="button" :aria-pressed="comparisonViewMode === mode.key" @click="comparisonViewMode = mode.key">{{ mode.label }}</button></div></div>
            <div class="dc-targets" aria-label="预测对象"><button v-for="item in inlineTargets" :key="targetKeyOf(item.targetType, item.targetName)" type="button" :aria-pressed="selectedTargetKey === targetKeyOf(item.targetType, item.targetName)" @click="selectTarget(item)"><span class="dc-target-dot" :class="{ 'dc-target-dot-pest': item.targetType === 'PEST' }"></span>{{ item.targetName }}</button><span v-if="!inlineTargets.length" class="dc-muted">暂无预测对象</span></div>
            <p v-if="forecastLoading" class="dc-chart-status" role="status">正在生成全部对象的 7 天预测…</p>
            <p v-else-if="forecastError" class="dc-chart-status dc-error" role="status">{{ forecastError }}</p>
            <VChart class="dc-chart" :key="forecastTrendChartKey" :option="chartOptions.comparison" :update-options="forecastTrendUpdateOptions" autoresize />
          </section>
          <section class="dc-risk-details">
            <div class="dc-panel dc-structure"><div class="dc-panel-title"><h3>{{ currentTargetLabel }} · 7 日风险结构</h3><span class="dc-muted">7d</span></div><VChart class="dc-chart" :option="chartOptions.structure" autoresize /></div>
            <aside class="dc-insights" aria-label="预警与驱动因子"><div><div class="dc-panel-title"><h3 class="dc-warning-title">预警提示</h3><span class="dc-muted">{{ forecastData ? todayRiskMeta.label : '暂无预测' }}</span></div><ul v-if="forecastWarnings.length" class="dc-warning-list"><li v-for="(warning, index) in forecastWarnings" :key="index">{{ warning }}</li></ul><p v-else class="dc-muted dc-insight-copy">{{ forecastData ? '暂无额外预警提示。' : '等待有效预测结果。' }}</p></div><div class="dc-drivers"><h3>主要驱动因子</h3><div v-if="topDrivers.length" class="dc-driver-list"><span v-for="(driver, index) in topDrivers" :key="index">{{ typeof driver === 'string' ? driver : driver.name || driver.factor || driver.label || '环境因子' }}</span></div><p v-else class="dc-muted dc-insight-copy">暂无驱动因子</p></div><p class="dc-weather-description">{{ weatherError || (weatherAvailable ? weatherPressure.desc : '暂无可用天气信息。') }}</p></aside>
          </section>
        </div>

        <div class="dc-column dc-column-activity">
          <section class="dc-panel"><div class="dc-panel-title"><h3>14 天识别趋势</h3><span class="dc-muted">{{ filteredRecords.length }} 条</span></div><VChart class="dc-chart" :option="chartOptions.activity" autoresize /></section>
          <section class="dc-distributions"><div class="dc-panel"><div class="dc-panel-title"><h3>识别热点分布</h3><span class="dc-muted">Top 6</span></div><VChart class="dc-chart" :option="chartOptions.distribution" autoresize /></div><div class="dc-panel"><div class="dc-panel-title"><h3>预测对象概览</h3><span class="dc-muted">{{ allTargetDefinitions.length }} 个</span></div><VChart class="dc-chart" :option="chartOptions.overview" autoresize /></div></section>
          <section class="dc-panel dc-confidence"><div class="dc-panel-title"><h3>置信度分析</h3><div class="dc-buckets"><span v-for="bucket in confidenceBuckets" :key="bucket.key" :style="{ color: bucket.color }">{{ bucket.label }} {{ bucket.value }}</span></div></div><VChart class="dc-chart" :option="chartOptions.confidence" autoresize /></section>
        </div>

        <div class="dc-column dc-column-context">
          <section class="dc-context dc-panel"><div class="dc-panel-title"><h3>预测上下文与气象特征</h3><span class="dc-muted dc-region">{{ activeCrop?.name || '未设置作物' }}</span></div><dl class="dc-context-grid"><div v-for="item in contextItems" :key="item.label"><dt>{{ item.label }}</dt><dd :title="String(item.value)">{{ item.value }}</dd></div></dl></section>
          <section class="dc-records"><div class="dc-panel-title"><h3>最近识别记录</h3><span class="dc-muted">{{ recentRecords.length }} 条</span></div><div class="dc-record-list"><p v-if="!recentRecords.length" class="dc-empty">当前作物下暂无识别记录。</p><button v-for="item in recentRecords" :key="item.id" type="button" class="dc-record" :aria-label="`查看${item.pestName}的识别记录`" data-testid="record-detail-trigger" @click="selectedRecord = item"><div class="dc-record-image"><img v-if="item.imageUrl" :src="item.imageUrl" alt="" @error="$event.target.style.display = 'none'" /><span v-else aria-hidden="true">🌿</span></div><div class="dc-record-copy"><div class="dc-record-heading"><strong>{{ item.pestName }}</strong><time :datetime="String(item.createTime)">{{ formatDate(item.createTime) }}</time></div><div class="dc-record-meta"><span class="dc-confidence-chip">{{ item.yoloUsed === false ? '未使用 YOLO' : `置信度 ${Math.round(item.confidence * 100)}%` }}</span><span>{{ item.cropName }}</span><span>{{ item.city || item.region }}</span></div></div></button></div></section>
        </div>
      </main>
    </div>
    <!-- Single element root is required by PCLayout's out-in page transition. -->
    <RecordDetailSheet :record="selectedRecord" @close="selectedRecord = null" />
    <DataCenterFarmDrawer :open="farmDrawerOpen" @close="farmDrawerOpen = false" />
  </div>
</template>

<style scoped>
.pc-data-page { --dc-line: rgba(203,213,225,.45); --dc-text: #0f172a; --dc-muted: #718096; --dc-accent: #059669; display: flex; height: 100%; min-height: 0; overflow-y: auto; overflow-x: hidden; color: var(--dc-text); background: linear-gradient(180deg,#fff 0%,#f1f5f9 100%); font-size: 14px; }
.dc-content { display: flex; flex: 1; flex-direction: column; min-width: 0; min-height: 0; }
.dc-toolbar { display: flex; flex-shrink: 0; min-height: 64px; align-items: center; gap: 14px; padding: 12px 18px; border-bottom: 1px solid var(--dc-line); }
.dc-heading { display: flex; align-items: center; gap: 9px; flex-shrink: 0; }
.dc-heading h2 { font-size: 20px; font-weight: 800; letter-spacing: -.03em; }
.dc-status-dot { width: 7px; height: 7px; border-radius: 50%; background: #10b981; }
.dc-crops { display: flex; gap: 4px; align-items: center; min-width: 0; overflow-x: auto; scrollbar-width: none; }
.dc-crops > span { flex-shrink: 0; padding-right: 4px; font-size: 13px; }
.dc-crops button, .dc-targets button { flex-shrink: 0; padding: 6px 11px; font-size: 13px; white-space: nowrap; }
.dc-crops button[aria-pressed='true'], .dc-targets button[aria-pressed='true'] { color: var(--dc-accent); background: rgba(16,185,129,.09); }
.dc-button { flex-shrink: 0; border: 1px solid var(--dc-line); padding: 7px 13px; font-size: 13px; font-weight: 650; white-space: nowrap; }
.dc-button:hover { border-color: #10b981; color: var(--dc-accent); }
.dc-refresh { margin-left: auto; background: #10b981; border-color: #10b981; color: #fff; }
.dc-refresh:hover { color: #fff; background: #059669; }
.dc-refresh:disabled { opacity: .5; cursor: wait; }
.dc-metrics { display: grid; grid-template-columns: repeat(8,minmax(0,1fr)); flex-shrink: 0; border-bottom: 1px solid var(--dc-line); }
.dc-metric { padding: 16px 14px; min-width: 0; border-right: 1px solid var(--dc-line); }
.dc-metric:last-child { border: 0; }
.dc-metric-label { font-size: 12px; color: var(--dc-muted); margin-bottom: 5px; white-space: nowrap; }
.dc-metric-values { display: flex; align-items: baseline; flex-wrap: wrap; gap: 4px 6px; }
.dc-metric-values strong { font: 750 26px/1.3 ui-monospace,Consolas,monospace; letter-spacing: -.06em; }
.dc-metric-values .dc-small-value { font-size: 16px; font-family: inherit; line-height: 1.5; letter-spacing: 0; }
.dc-metric-values > span { color: var(--dc-muted); font-size: 12px; }
.dc-main { flex: 1; min-height: 0; display: grid; grid-template-columns: minmax(0,5fr) minmax(0,4fr) minmax(0,3fr); }
.dc-column { min-width: 0; min-height: 0; display: grid; overflow: hidden; }
.dc-column + .dc-column { border-left: 1px solid var(--dc-line); }
.dc-column-risk { grid-template-rows: minmax(0,1.2fr) minmax(0,1fr); }
.dc-column-activity { grid-template-rows: repeat(3,minmax(0,1fr)); }
.dc-column-context { grid-template-rows: auto minmax(0,1fr); }
.dc-column > * + * { border-top: 1px solid var(--dc-line); }
.dc-panel { display: flex; flex-direction: column; min-width: 0; min-height: 0; overflow: hidden; padding: 14px; }
.dc-panel-title { display: flex; flex-shrink: 0; align-items: center; flex-wrap: wrap; gap: 5px 8px; min-height: 26px; margin-bottom: 7px; }
.dc-panel-title h3, .dc-drivers h3 { font-size: 16px; line-height: 1.5; font-weight: 750; }
.dc-panel-title > .dc-muted { margin-left: auto; font-size: 12px; }
.dc-chart { flex: 1; width: 100%; min-height: 0; }
.dc-filter { display: flex; margin-left: auto; gap: 2px; }
.dc-filter button { padding: 5px 9px; font-size: 13px; white-space: nowrap; color: var(--dc-muted); }
.dc-filter button[aria-pressed='true'] { color: #fff; background: #0f172a; }
.dc-targets { display: flex; flex-shrink: 0; align-items: center; overflow-x: auto; scrollbar-width: thin; gap: 3px; margin-bottom: 5px; min-height: 25px; }
.dc-targets button { display: inline-flex; align-items: center; gap: 5px; }
.dc-target-dot { width: 5px; height: 5px; border-radius: 50%; background: #38bdf8; }
.dc-target-dot-pest { background: #10b981; }
.dc-chart-status { font-size: 13px; color: var(--dc-muted); padding: 5px 0; }
.dc-error { color: #d97706; }
.dc-risk-details { display: grid; min-height: 0; grid-template-columns: minmax(0,7fr) minmax(0,5fr); overflow: hidden; }
.dc-insights { display: flex; flex-direction: column; min-width: 0; min-height: 0; overflow-y: auto; gap: 16px; padding: 16px 14px; border-left: 1px solid var(--dc-line); }
.dc-warning-title { color: #d97706; }
.dc-warning-list { padding-left: 14px; list-style: disc; font-size: 13px; line-height: 1.8; }
.dc-insight-copy { font-size: 13px; margin-top: 7px; line-height: 1.8; }
.dc-drivers, .dc-weather-description { border-top: 1px solid var(--dc-line); padding-top: 10px; }
.dc-driver-list { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 7px; }
.dc-driver-list span { padding: 2px 5px; background: rgba(148,163,184,.09); font-size: 12px; }
.dc-weather-description { margin-top: auto; color: var(--dc-muted); font-size: 13px; line-height: 1.8; }
.dc-distributions { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); min-height: 0; }
.dc-distributions > * + * { border-left: 1px solid var(--dc-line); }
.dc-buckets { display: flex; gap: 6px; margin-left: auto; flex-wrap: wrap; font-size: 11px; }
.dc-region { max-width: 80px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dc-context-grid { display: grid; grid-template-columns: repeat(2,minmax(0,1fr)); border-top: 1px solid var(--dc-line); border-left: 1px solid var(--dc-line); margin-top: 4px; }
.dc-context-grid > div { min-width: 0; padding: 8px; border-right: 1px solid var(--dc-line); border-bottom: 1px solid var(--dc-line); }
.dc-context-grid dt { color: var(--dc-muted); font-size: 12px; margin-bottom: 3px; }
.dc-context-grid dd { font-size: 13px; font-weight: 650; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dc-records { display: flex; flex-direction: column; min-height: 0; overflow: hidden; }
.dc-records > .dc-panel-title { padding: 9px 14px; margin: 0; border-bottom: 1px solid var(--dc-line); }
.dc-record-list { flex: 1; overflow-y: auto; min-height: 0; }
.dc-record { display: flex; align-items: center; gap: 10px; padding: 12px 14px; width: 100%; text-align: left; border-bottom: 1px solid var(--dc-line); }
.dc-record:hover { background: rgba(16,185,129,.04); }
.dc-record-image { width: 34px; height: 34px; flex-shrink: 0; display: flex; align-items: center; justify-content: center; background: rgba(16,185,129,.08); overflow: hidden; }
.dc-record-image img { width: 100%; height: 100%; object-fit: cover; }
.dc-record-copy { flex: 1; min-width: 0; }
.dc-record-heading { display: flex; align-items: center; flex-wrap: wrap; gap: 4px 8px; }
.dc-record-heading strong { font-size: 14px; font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dc-record-heading time { color: var(--dc-muted); font-size: 11px; margin-left: auto; }
.dc-record-meta { display: flex; gap: 5px; flex-wrap: wrap; color: var(--dc-muted); font-size: 12px; margin-top: 5px; }
.dc-confidence-chip { color: var(--dc-accent); background: rgba(16,185,129,.08); padding: 1px 3px; }
.dc-muted { color: var(--dc-muted); }
.dc-empty { padding: 30px 16px; color: var(--dc-muted); font-size: 12px; }
.dc-notice { padding: 8px 16px; color: #b45309; font-size: 11px; border-bottom: 1px solid var(--dc-line); }
.dc-minimal button:focus-visible { outline: 2px solid #10b981; outline-offset: 2px; }
.dc-button, .dc-crops button, .dc-filter button, .dc-targets button { border-radius: 9999px; }
.dc-driver-list span, .dc-confidence-chip { border-radius: 9999px; }
:global(.dark .pc-data-page) { --dc-line: rgba(51,65,85,.4); --dc-text: #e2e8f0; --dc-muted: #94a3b8; --dc-accent: #34d399; background: linear-gradient(180deg,#0b1120 0%,#020617 100%); }
:global(.dark .dc-filter button[aria-pressed='true']) { background: #34d399; color: #020617; }
@media (max-width: 1399px) { .dc-metrics { grid-template-columns: repeat(4,minmax(0,1fr)); } .dc-metric:nth-child(-n+4) { border-bottom: 1px solid var(--dc-line); } .dc-metric:nth-child(4) { border-right: 0; } }
@media (max-width: 1279px) { .dc-main { grid-template-columns: minmax(0,1.1fr) minmax(0,1fr); min-height: 980px; flex: none; grid-template-rows: 660px 320px; } .dc-column-context { grid-column: 1 / -1; grid-template-columns: minmax(0,1fr) minmax(0,1fr); grid-template-rows: 1fr; border-left: 0 !important; border-top: 1px solid var(--dc-line); } .dc-column-context > * + * { border-top: 0; border-left: 1px solid var(--dc-line); } .dc-toolbar { gap: 8px; flex-wrap: wrap; } .dc-crops { max-width: 48%; } }
@media (min-width: 1280px) and (max-height: 800px) { .dc-main { flex: none; min-height: 640px; } }
@media (prefers-reduced-motion: reduce) { .dc-content { transition: none; } }
</style>
