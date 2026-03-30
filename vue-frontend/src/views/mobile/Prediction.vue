<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useFarmStore } from '../../stores/farmCloud'
import axios from 'axios'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, GridComponent, MarkLineComponent } from 'echarts/components'
import VChart from 'vue-echarts'

use([CanvasRenderer, LineChart, TitleComponent, TooltipComponent, GridComponent, MarkLineComponent])

const router = useRouter()
const farmStore = useFarmStore()
const isDark = ref(document.documentElement.classList.contains('dark'))
let themeObserver = null

const syncDarkMode = () => {
  isDark.value = document.documentElement.classList.contains('dark')
}

// ========== API 状态 ==========
const loading = ref(false)
const predictionData = ref(null)
const errorMsg = ref('')
const targetsLoading = ref(false)
const targetOptions = ref({ diseaseTargets: [], pestTargets: [] })
const selectedTargetType = ref('DISEASE')
const selectedTargetName = ref('')

const targetTypeLabelMap = {
  DISEASE: '病害',
  PEST: '虫害'
}

const availableTargets = computed(() => (
  selectedTargetType.value === 'PEST'
    ? (targetOptions.value.pestTargets || [])
    : (targetOptions.value.diseaseTargets || [])
))

const hasTargetOptions = computed(() => (
  (targetOptions.value.diseaseTargets?.length || 0) > 0
  || (targetOptions.value.pestTargets?.length || 0) > 0
))

const currentTargetName = computed(() => (
  predictionData.value?.targetName || selectedTargetName.value || '预测对象'
))

const currentTargetTypeLabel = computed(() => (
  targetTypeLabelMap[predictionData.value?.targetType || selectedTargetType.value] || '预测对象'
))

const predictionWarnings = computed(() => predictionData.value?.warnings || [])

function resetTargets() {
  targetOptions.value = { diseaseTargets: [], pestTargets: [] }
  selectedTargetType.value = 'DISEASE'
  selectedTargetName.value = ''
}

function applyTargetSelection(options) {
  const nextDiseaseTargets = options.diseaseTargets || []
  const nextPestTargets = options.pestTargets || []

  if (selectedTargetType.value === 'DISEASE' && nextDiseaseTargets.includes(selectedTargetName.value)) {
    return
  }
  if (selectedTargetType.value === 'PEST' && nextPestTargets.includes(selectedTargetName.value)) {
    return
  }

  if (nextDiseaseTargets.length > 0) {
    selectedTargetType.value = 'DISEASE'
    selectedTargetName.value = nextDiseaseTargets[0]
    return
  }
  if (nextPestTargets.length > 0) {
    selectedTargetType.value = 'PEST'
    selectedTargetName.value = nextPestTargets[0]
    return
  }
  selectedTargetName.value = ''
}

async function fetchTargets() {
  const crop = farmStore.activeCrop
  if (!crop?.name) {
    resetTargets()
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
    applyTargetSelection(options)
    if (!options.diseaseTargets.length && !options.pestTargets.length) {
      predictionData.value = null
      errorMsg.value = '当前作物暂无可用预测对象。'
    }
  } catch (error) {
    console.error('加载预测对象失败:', error)
    resetTargets()
    predictionData.value = null
    errorMsg.value = '预测对象加载失败，请稍后重试。'
  } finally {
    targetsLoading.value = false
  }
}

// ========== 调用后端 API ==========
async function fetchPrediction() {
  const crop = farmStore.activeCrop
  if (!crop) return
  if (!selectedTargetName.value) {
    predictionData.value = null
    errorMsg.value = '请先选择预测对象。'
    return
  }
  if (!crop.locationId) {
    predictionData.value = null
    errorMsg.value = '请先在农田档案里补充地区信息。'
    return
  }
  if (!crop.stage) {
    predictionData.value = null
    errorMsg.value = '请先在农田档案里确认当前生效物候期。'
    return
  }

  loading.value = true
  errorMsg.value = ''

  try {
    const reqBody = {
      crop: crop.name,
      disease: selectedTargetType.value === 'DISEASE' ? selectedTargetName.value : null,
      targetType: selectedTargetType.value,
      targetName: selectedTargetName.value || null,
      regionCode: crop.locationId,
      targetDate: new Date().toISOString().split('T')[0],
      forecastDays: 7,
      phenologyStage: crop.stage,
      reportRiskHint: 0
    }

    const response = await axios.post('/api/trend/forecast', reqBody)
    const data = response.data
    if (data?.supported === false) {
      predictionData.value = null
      errorMsg.value = data.message || '未选择具体病害/虫害'
      return
    }
    predictionData.value = data

    if (data.targetType) {
      selectedTargetType.value = data.targetType
    }
    if (data.targetName) {
      selectedTargetName.value = data.targetName
    }
  } catch (e) {
    console.error('趋势预测请求失败:', e)
    errorMsg.value = '预测服务暂时不可用，请稍后重试'
  } finally {
    loading.value = false
  }
}

watch(() => farmStore.activeCrop?.name, async () => {
  predictionData.value = null
  errorMsg.value = ''
  await fetchTargets()
}, { immediate: true })

// 监听作物切换或预测对象切换 → 重新预测
watch([
  () => farmStore.activeCropId,
  () => farmStore.activeCrop?.stage,
  () => farmStore.activeCrop?.locationId,
  selectedTargetType,
  selectedTargetName
], () => {
  if (targetsLoading.value) {
    return
  }
  if (farmStore.activeCrop && selectedTargetName.value) {
    fetchPrediction()
  }
}, { immediate: true })

// ========== 计算属性 ==========
const riskLevelText = computed(() => {
  const level = predictionData.value?.todayRiskLevel
  switch (level) {
    case 3:  return '高风险'
    case 2:  return '中风险'
    case 1:  return '轻风险'
    case 0:  return '低风险'
    default: return '未知'
  }
})

const riskColor = computed(() => {
  const level = predictionData.value?.todayRiskLevel
  switch (level) {
    case 3:  return 'red'
    case 2:  return 'orange'
    case 1:  return 'yellow'
    case 0:  return 'green'
    default: return 'gray'
  }
})

const riskColorClasses = computed(() => {
  return {
    red:    { gradient: 'from-red-500 to-orange-500', shadow: 'shadow-orange-500/30', text: 'text-red-500' },
    orange: { gradient: 'from-orange-400 to-amber-500', shadow: 'shadow-amber-500/30', text: 'text-orange-500' },
    yellow: { gradient: 'from-amber-400 to-yellow-500', shadow: 'shadow-yellow-500/30', text: 'text-yellow-600' },
    green:  { gradient: 'from-emerald-500 to-green-500', shadow: 'shadow-green-500/30', text: 'text-green-500' },
    gray:   { gradient: 'from-slate-400 to-slate-500', shadow: 'shadow-slate-500/30', text: 'text-slate-500' }
  }[riskColor.value] || { gradient: 'from-slate-400 to-slate-500', shadow: 'shadow-slate-500/20', text: 'text-slate-500' }
})

const riskEmoji = computed(() => {
  const level = predictionData.value?.todayRiskLevel
  switch (level) {
    case 3:  return '⚠️'
    case 2:  return '⚡'
    case 1:  return '💡'
    case 0:  return '✅'
    default: return '❓'
  }
})

const trendArrow = computed(() => {
  const dir = predictionData.value?.trendDirection
  switch (dir) {
    case '上升': return '↑'
    case '下降': return '↓'
    default:     return '→'
  }
})

const riskPercentage = computed(() => {
  const score = predictionData.value?.todayRiskScore || 0
  return Math.round(score * 100)
})

// ========== ECharts 图表配置 ==========
const trendOption = computed(() => {
  const series = predictionData.value?.dailySeries || []
  if (series.length === 0) return {}
  const labels = series.map((d, i) => i === 0 ? '今天' : d.date)
  const data = series.map(d => Math.round(d.riskScore * 100))
  const darkMode = isDark.value
  const tooltipBg = darkMode ? '#0f172a' : '#ffffff'
  const tooltipBorder = darkMode ? '#334155' : '#e2e8f0'
  const titleColor = darkMode ? '#e2e8f0' : '#334155'
  const mutedColor = darkMode ? '#94a3b8' : '#64748b'
  const axisColor = darkMode ? '#475569' : '#e2e8f0'
  const gridColor = darkMode ? '#334155' : '#f1f5f9'
  const pointBorder = darkMode ? '#0f172a' : '#ffffff'
  
  return {
    tooltip: {
      trigger: 'axis',
      backgroundColor: tooltipBg,
      borderColor: tooltipBorder,
      textStyle: { color: titleColor },
      formatter: (p) => {
        const val = p[0].value
        const level = val >= 75 ? '高危' : val >= 55 ? '中风险' : val >= 30 ? '轻风险' : '低风险'
        const color = val >= 75 ? '#ef4444' : val >= 55 ? '#f97316' : val >= 30 ? '#eab308' : '#22c55e'
        const advice = val >= 75 ? '建议当天施药防治' : val >= 55 ? '加强田间巡查' : val >= 30 ? '注意观察' : '保持常规管理'
        return `<div style="padding:4px 8px;">
          <div style="font-weight:bold;margin-bottom:4px;color:${titleColor}">${p[0].name}</div>
          <div style="display:flex;align-items:center;gap:6px;margin-bottom:4px">
            <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${color}"></span>
            <span style="font-weight:bold;color:${color}">${val}% ${level}</span>
          </div>
          <div style="font-size:11px;color:${mutedColor}">${advice}</div>
        </div>`
      }
    },
    grid: { left: 40, right: 20, top: 30, bottom: 30 },
    xAxis: {
      type: 'category',
      data: labels,
      axisLine: { lineStyle: { color: axisColor } },
      axisLabel: { color: mutedColor, fontSize: 11 }
    },
    yAxis: {
      type: 'value',
      min: 0,
      max: 100,
      splitLine: { lineStyle: { color: gridColor, type: 'dashed' } },
      axisLabel: { color: mutedColor, fontSize: 10, formatter: '{value}%' }
    },
    series: [{
      type: 'line',
      data: data,
      smooth: true,
      symbol: 'circle',
      symbolSize: 8,
      lineStyle: { width: 3, color: '#10b981' },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [{ offset: 0, color: 'rgba(16,185,129,0.35)' }, { offset: 1, color: 'rgba(16,185,129,0.02)' }]
        }
      },
      itemStyle: { color: '#10b981', borderColor: pointBorder, borderWidth: 2 },
      markLine: {
        silent: true,
        symbol: 'none',
        lineStyle: { type: 'dashed', width: 1 },
        label: { position: 'insideStartTop', fontSize: 10, fontWeight: 700 },
        data: [
          { yAxis: 75, lineStyle: { color: '#ef4444' }, label: { formatter: '高风险 75%', color: '#ef4444' } },
          { yAxis: 55, lineStyle: { color: '#f97316' }, label: { formatter: '中风险 55%', color: '#f97316' } },
          { yAxis: 30, lineStyle: { color: '#eab308' }, label: { formatter: '轻风险 30%', color: '#eab308' } }
        ]
      }
    }]
  }
})

// ========== 防治建议时间线 ==========
const timelineItems = computed(() => {
  const series = predictionData.value?.dailySeries || []
  if (!series.length) return []

  return series.map((d, i) => {
    const riskPct = Math.round(d.riskScore * 100)
    const date = new Date()
    date.setDate(date.getDate() + i)
    const dateStr = `${date.getMonth() + 1}月${date.getDate()}日`
    const dayLabel = i === 0 ? '今天' : d.date

    let action, type, color
    if (d.riskLevel >= 3) {
      action = '立即施药防治，喷洒对应药剂'
      type = '紧急施药'
      color = 'red'
    } else if (d.riskLevel >= 2) {
      action = '加强田间巡查，预防性喷洒'
      type = '预防喷洒'
      color = 'orange'
    } else if (d.riskLevel >= 1) {
      action = '注意观察作物状态，保持通风'
      type = '重点观察'
      color = 'yellow'
    } else {
      action = '常规管理，保持良好田间环境'
      type = '日常管理'
      color = 'green'
    }
    return { date: dateStr, day: dayLabel, action, type, color, risk: riskPct }
  })
})

// ========== 环境因子（从 API 响应） ==========
const weatherFactors = computed(() => {
  const ws = predictionData.value?.weatherSummary || {}
  const humidity = ws.humidityMean3d || 60
  const temp = ws.tempMean3d || 20
  const rain = ws.rainSum7d || 0
  const rainDays = ws.consecutiveRainDays || 0

  return [
    {
      label: '近3天湿度', icon: '💧', value: Math.round(humidity) + '%',
      status: humidity >= 80 ? '偏高' : humidity >= 60 ? '正常' : '偏低',
      statusColor: humidity >= 80 ? 'text-red-500' : 'text-green-500',
      bgColor: 'bg-blue-50/50', borderColor: 'border-blue-100/50',
      iconBg: 'bg-blue-100', iconColor: 'text-blue-600',
      arrow: humidity >= 80 ? '↑' : ''
    },
    {
      label: '近3天均温', icon: '🌡️', value: Math.round(temp) + '°C',
      status: temp >= 15 && temp <= 28 ? '适宜' : temp > 28 ? '偏高' : '偏低',
      statusColor: temp >= 15 && temp <= 28 ? 'text-green-500' : 'text-orange-500',
      bgColor: 'bg-orange-50/50', borderColor: 'border-orange-100/50',
      iconBg: 'bg-orange-100', iconColor: 'text-orange-600',
      arrow: ''
    },
    {
      label: '近7天降水', icon: '🌧️', value: Math.round(rain) + 'mm',
      status: rain >= 20 ? '较多' : rain >= 5 ? '适中' : '偏少',
      statusColor: rain >= 20 ? 'text-red-500' : rain >= 5 ? 'text-orange-500' : 'text-green-500',
      bgColor: 'bg-indigo-50/50', borderColor: 'border-indigo-100/50',
      iconBg: 'bg-indigo-100', iconColor: 'text-indigo-600',
      arrow: rain >= 20 ? '↑' : ''
    },
    {
      label: '连续降雨', icon: '☔', value: rainDays + '天',
      status: rainDays >= 3 ? '持续阴雨' : rainDays > 0 ? '间歇降雨' : '无降雨',
      statusColor: rainDays >= 3 ? 'text-red-500' : 'text-green-500',
      bgColor: 'bg-teal-50/50', borderColor: 'border-teal-100/50',
      iconBg: 'bg-teal-100', iconColor: 'text-teal-600',
      arrow: rainDays >= 3 ? '↑' : ''
    }
  ]
})

// ========== 历史记录展开 ==========
const showHistory = ref(false)
const cropHistory = computed(() => {
  if (!farmStore.activeCrop) return []
  return farmStore.getHistoryByCrop(farmStore.activeCropId).slice(0, 5)
})

onMounted(() => {
  syncDarkMode()
  themeObserver = new MutationObserver(syncDarkMode)
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class']
  })
})

onBeforeUnmount(() => {
  themeObserver?.disconnect()
})
</script>

<template>
  <div class="prediction-page px-6 pt-4 min-h-full pb-8">
    <!-- Header -->
    <div class="mb-6">
      <h1 class="text-3xl font-bold text-slate-900 tracking-tight" v-motion-slide-visible-once-bottom>趋势预测</h1>
      <p class="text-slate-500 text-sm mt-1 font-medium" v-motion-slide-visible-once-bottom :delay="100">气象驱动 × 农学规则 × 智能分析</p>
    </div>
    
    <!-- ========== A: 我的作物选择器 ========== -->
    <div class="mb-6" v-motion-slide-visible-once-bottom :delay="50">
      <div class="flex items-center justify-between mb-3">
        <h2 class="text-sm font-bold text-slate-700">🌾 我的作物</h2>
        <button 
          @click="router.push('/farm')"
          class="text-xs font-bold text-green-600 bg-green-50 px-3 py-1 rounded-lg active:scale-95 transition-transform"
        >
          管理
        </button>
      </div>

      <!-- 无作物提示 -->
      <div v-if="farmStore.crops.length === 0" class="bg-white rounded-2xl p-6 border border-slate-100 shadow-sm text-center">
        <div class="text-3xl mb-2">🌱</div>
        <p class="text-sm text-slate-500 mb-3">还没有添加作物</p>
        <button 
          @click="router.push('/farm')"
          class="bg-green-500 text-white px-4 py-2 rounded-xl text-sm font-bold shadow-lg shadow-green-500/30 active:scale-95 transition-transform"
        >
          前往添加
        </button>
      </div>

      <!-- 作物横向列表 -->
      <div v-else class="flex space-x-3 overflow-x-auto pb-2 -mx-1 px-1 scrollbar-hide">
        <button 
          v-for="crop in farmStore.crops" 
          :key="crop.id"
          @click="farmStore.setActiveCrop(crop.id)"
          class="flex-shrink-0 flex items-center space-x-2.5 px-4 py-2.5 rounded-2xl border-2 transition-all active:scale-95"
          :class="farmStore.activeCropId === crop.id 
            ? 'border-green-500 bg-green-50 shadow-lg shadow-green-500/20' 
            : 'border-slate-100 bg-white'"
        >
          <span class="text-xl">{{ crop.icon }}</span>
          <div class="text-left">
            <div class="text-sm font-bold" :class="farmStore.activeCropId === crop.id ? 'text-green-700' : 'text-slate-700'">{{ crop.name }}</div>
            <div class="text-[10px] font-medium text-slate-400">{{ crop.stage }} · {{ crop.region }}</div>
          </div>
        </button>
      </div>
    </div>

    <!-- ========== A2: 预测对象 + 物候期 选择器 ========== -->
    <div v-if="farmStore.activeCrop && hasTargetOptions" class="mb-6 space-y-3" v-motion-slide-visible-once-bottom :delay="70">
      <div>
        <label class="text-[10px] font-bold text-slate-400 mb-2 block">🎯 预测对象</label>
        <div class="flex space-x-2 mb-2">
          <button
            v-for="targetType in ['DISEASE', 'PEST']"
            :key="targetType"
            @click="selectedTargetType = targetType"
            :disabled="targetType === 'DISEASE' ? !targetOptions.diseaseTargets.length : !targetOptions.pestTargets.length"
            class="text-xs font-bold px-3 py-1.5 rounded-xl border transition-all active:scale-95 disabled:opacity-40 disabled:cursor-not-allowed"
            :class="selectedTargetType === targetType
              ? 'border-slate-800 bg-slate-800 text-white shadow-lg'
              : 'border-slate-200 bg-white text-slate-600'"
          >
            {{ targetTypeLabelMap[targetType] }}
          </button>
        </div>
        <div class="flex space-x-2 overflow-x-auto scrollbar-hide">
          <button
            v-for="target in availableTargets"
            :key="target"
            @click="selectedTargetName = target"
            class="flex-shrink-0 text-xs font-bold px-3 py-1.5 rounded-xl border transition-all active:scale-95"
            :class="selectedTargetName === target
              ? 'border-green-500 bg-green-500 text-white shadow-lg shadow-green-500/20'
              : 'border-slate-200 bg-white text-slate-600'"
          >
            {{ target }}
          </button>
        </div>
      </div>

      <div>
        <label class="text-[10px] font-bold text-slate-400 mb-1 block">🌱 物候期</label>
        <div class="w-full rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-xs font-bold text-slate-700">
          {{ farmStore.activeCrop.stage || '待确认' }}
          <span class="ml-2 text-[10px] text-slate-400">
            {{ farmStore.activeCrop.stageMode === 'AUTO' ? '自动判断' : '手动设置' }}
          </span>
        </div>
      </div>
    </div>

    <!-- ========== Loading ==========  -->
    <div v-if="loading || targetsLoading" class="flex flex-col items-center justify-center py-16">
      <div class="w-10 h-10 border-3 border-green-200 border-t-green-500 rounded-full animate-spin mb-4"></div>
      <p class="text-sm text-slate-400 font-medium">{{ targetsLoading ? '正在加载预测对象...' : '正在分析气象数据...' }}</p>
    </div>

    <!-- ========== Error ==========  -->
    <div v-else-if="errorMsg" class="bg-red-50 border border-red-100 rounded-2xl p-6 text-center mb-6">
      <div class="text-2xl mb-2">⚠️</div>
      <p class="text-sm text-red-600 font-medium">{{ errorMsg }}</p>
      <button @click="fetchPrediction" class="mt-3 text-xs font-bold text-red-500 bg-red-100 px-4 py-1.5 rounded-lg">重试</button>
    </div>

    <!-- ========== 有数据时展示 ========== -->
    <template v-else-if="predictionData && farmStore.activeCrop">
      <div class="lg:grid lg:grid-cols-12 lg:gap-8">
        <!-- 左侧核心区 -->
        <div class="lg:col-span-8 flex flex-col lg:gap-6">
          <div class="lg:flex lg:gap-6">
            <!-- ========== B: 风险等级卡片 ========== -->
            <div 
              class="relative overflow-hidden rounded-[2rem] p-8 text-white flex-1 mb-6 lg:mb-0 transform transition-all duration-500 hover:scale-[1.02]" 
              :class="[`shadow-2xl ${riskColorClasses.shadow}`]"
              v-motion-pop-visible-once
            >
              <div class="absolute inset-0 bg-gradient-to-br" :class="riskColorClasses.gradient"></div>
              <div class="absolute top-0 right-0 w-32 h-32 bg-white/10 rounded-full -mr-10 -mt-10 blur-2xl"></div>
              <div class="absolute bottom-0 left-0 w-24 h-24 bg-black/10 rounded-full -ml-8 -mb-8 blur-xl"></div>
              
              <div class="relative z-10 flex flex-col h-full justify-between">
                <div>
                  <div class="flex justify-between items-start mb-4">
                    <div>
                      <h2 class="text-white/80 text-xs font-bold tracking-wider uppercase mb-1">
                        {{ farmStore.activeCrop.icon }} {{ farmStore.activeCrop.name }} · {{ currentTargetTypeLabel }} · {{ currentTargetName }}
                      </h2>
                      <div class="text-4xl lg:text-5xl font-black tracking-tighter">{{ riskLevelText }}</div>
                    </div>
                    <div class="flex flex-col items-center">
                      <div class="w-14 h-14 bg-white/20 rounded-2xl flex items-center justify-center text-3xl backdrop-blur-md shadow-inner border border-white/20">
                        {{ riskEmoji }}
                      </div>
                      <!-- 趋势方向 -->
                      <div class="mt-1.5 text-xs font-bold text-white/80 flex items-center space-x-1">
                        <span>{{ trendArrow }}</span>
                        <span>{{ predictionData.trendDirection }}</span>
                      </div>
                    </div>
                  </div>
                </div>
                <p class="text-white/90 font-medium leading-relaxed text-sm mt-4">
                  气象规则引擎分析，<span class="font-bold">{{ currentTargetName }}</span>风险评分 
                  <span class="font-bold text-white text-lg">{{ riskPercentage }}%</span>。
                  {{ riskPercentage >= 75 ? '建议立即采取防治措施。' : riskPercentage >= 55 ? '建议加强田间巡查并预防性喷洒。' : riskPercentage >= 30 ? '注意观察，保持通风。' : '当前风险可控，保持日常管理即可。' }}
                </p>
              </div>
            </div>

            <!-- ========== C: 主导因子卡片 ========== -->
            <div v-if="predictionData.topDrivers && predictionData.topDrivers.length > 0" class="lg:w-1/3 mb-6 lg:mb-0" v-motion-slide-visible-once-bottom :delay="100">
              <h2 class="text-sm font-bold text-slate-700 mb-3">🔍 主导因子</h2>
              <div class="bg-white rounded-2xl p-5 border border-slate-100 shadow-sm space-y-3 h-[calc(100%-2rem)]">
                <div 
                  v-for="(driver, i) in predictionData.topDrivers" 
                  :key="i"
                  class="flex items-center space-x-3"
                >
                  <div class="w-6 h-6 rounded-full flex items-center justify-center text-white text-xs font-bold flex-shrink-0"
                    :class="i === 0 ? 'bg-red-500' : i === 1 ? 'bg-orange-400' : 'bg-amber-400'"
                  >
                    {{ i + 1 }}
                  </div>
                  <span class="text-sm text-slate-700 font-medium">{{ driver }}</span>
                </div>
              </div>
            </div>
          </div>

          <div v-if="predictionWarnings.length > 0" class="bg-amber-50 border border-amber-100 rounded-2xl p-4 mb-6" v-motion-slide-visible-once-bottom :delay="130">
            <div class="text-xs font-bold text-amber-700 mb-2">预测提示</div>
            <div class="space-y-1">
              <div v-for="warning in predictionWarnings" :key="warning" class="text-xs text-amber-700 font-medium">
                {{ warning }}
              </div>
            </div>
          </div>

          <!-- ========== D: 风险走势图 (ECharts) ========== -->
          <div class="bg-white rounded-[2rem] shadow-[0_8px_30px_rgb(0,0,0,0.04)] p-6 mb-6 lg:mb-0 border border-slate-100 flex-1" v-motion-slide-visible-once-bottom :delay="150">
            <div class="flex justify-between items-center mb-2">
              <h2 class="text-base font-bold text-slate-900">📊 未来7天风险走势</h2>
              <div class="flex items-center space-x-2">
                <span class="text-xs font-bold text-green-500 bg-green-50 px-2 py-0.5 rounded-lg">LIVE</span>
                <span class="text-[10px] font-bold text-slate-400 hidden lg:inline">{{ predictionData.modelVersion }}</span>
              </div>
            </div>
            
            <v-chart :option="trendOption" autoresize style="height: 280px; width: 100%;" />
          </div>
        </div>

        <!-- 右侧详情区 -->
        <div class="lg:col-span-4 flex flex-col gap-6">
          <!-- ========== F: 环境因子面板 ========== -->
          <div v-motion-slide-visible-once-bottom :delay="250">
            <h2 class="text-sm font-bold text-slate-700 mb-3 hidden lg:block">🌡️ 环境因子</h2>
            <div class="grid grid-cols-2 gap-3">
              <div 
                v-for="factor in weatherFactors" 
                :key="factor.label"
                class="p-5 rounded-[1.5rem] flex flex-col justify-between border"
                :class="[factor.bgColor, factor.borderColor]"
              >
                <div class="w-9 h-9 rounded-xl flex items-center justify-center text-lg mb-2" :class="[factor.iconBg, factor.iconColor]">{{ factor.icon }}</div>
                <div>
                  <div class="text-[10px] font-bold text-slate-400 mb-0.5">{{ factor.label }}</div>
                  <div class="text-xl font-black text-slate-800">{{ factor.value }}</div>
                  <div class="text-[10px] font-bold mt-0.5 flex items-center" :class="factor.statusColor">
                    <span>{{ factor.arrow }} {{ factor.status }}</span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <!-- ========== E: 防治建议时间线 ========== -->
          <div 
            v-if="timelineItems.length > 0"
            class="bg-white rounded-[2rem] shadow-[0_8px_30px_rgb(0,0,0,0.04)] p-6 border border-slate-100" 
            v-motion-slide-visible-once-bottom :delay="200"
          >
            <h2 class="text-base font-bold text-slate-900 mb-5">🔮 7天防治建议</h2>
            
            <div class="relative pl-6">
              <div class="absolute left-[7px] top-2 bottom-2 w-0.5 bg-slate-100"></div>
              
              <div v-for="(item, index) in timelineItems" :key="index" class="relative pb-5 last:pb-0">
                <div 
                  class="absolute -left-6 top-1 w-4 h-4 rounded-full border-2 border-white shadow-md z-10"
                  :class="{
                    'bg-red-500': item.color === 'red',
                    'bg-orange-400': item.color === 'orange',
                    'bg-yellow-400': item.color === 'yellow',
                    'bg-green-500': item.color === 'green'
                  }"
                ></div>
                
                <div class="flex items-start justify-between">
                  <div class="flex-1 min-w-0">
                    <div class="flex items-center space-x-2 mb-1">
                      <span class="text-xs font-bold text-slate-800">{{ item.day }}</span>
                      <span class="text-[10px] text-slate-400">{{ item.date }}</span>
                      <span 
                        class="text-[9px] font-bold px-1.5 py-0.5 rounded-full text-white"
                        :class="{
                          'bg-red-500': item.color === 'red',
                          'bg-orange-400': item.color === 'orange',
                          'bg-yellow-400 !text-yellow-800': item.color === 'yellow',
                          'bg-green-500': item.color === 'green'
                        }"
                      >
                        {{ item.type }}
                      </span>
                    </div>
                    <p class="text-xs text-slate-500 leading-relaxed">{{ item.action }}</p>
                  </div>
                  <span class="text-xs font-bold flex-shrink-0 ml-3 hidden sm:inline" :class="{
                    'text-red-500': item.risk >= 75,
                    'text-orange-500': item.risk >= 55 && item.risk < 75,
                    'text-yellow-600': item.risk >= 30 && item.risk < 55,
                    'text-green-500': item.risk < 30
                  }">
                    {{ item.risk }}%
                  </span>
                </div>
              </div>
            </div>
          </div>

          <!-- ========== G: 识别历史 ========== -->
          <div 
            v-if="farmStore.activeCrop"
            class="bg-white rounded-[2rem] shadow-[0_8px_30px_rgb(0,0,0,0.04)] p-6 border border-slate-100" 
            v-motion-slide-visible-once-bottom :delay="300"
          >
            <button @click="showHistory = !showHistory" class="w-full flex justify-between items-center outline-none">
              <h2 class="text-base font-bold text-slate-900">📋 {{ farmStore.activeCrop.name }} 识别记录</h2>
              <div class="flex items-center space-x-2">
                <span class="text-xs font-bold text-slate-400">{{ cropHistory.length }} 条</span>
                <svg 
                  xmlns="http://www.w3.org/2000/svg" 
                  class="h-4 w-4 text-slate-400 transition-transform duration-300"
                  :class="showHistory ? 'rotate-180' : ''"
                  fill="none" viewBox="0 0 24 24" stroke="currentColor"
                >
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19 9l-7 7-7-7" />
                </svg>
              </div>
            </button>

            <transition
              enter-active-class="transition-all duration-300 ease-out"
              enter-from-class="opacity-0 max-h-0"
              enter-to-class="opacity-100 max-h-[500px]"
              leave-active-class="transition-all duration-200 ease-in"
              leave-from-class="opacity-100 max-h-[500px]"
              leave-to-class="opacity-0 max-h-0"
            >
              <div v-show="showHistory" class="mt-4 space-y-3 overflow-hidden">
                <div v-if="cropHistory.length === 0" class="text-center py-6">
                  <div class="text-3xl mb-2">🔍</div>
                  <p class="text-sm text-slate-400">该作物暂无识别记录</p>
                </div>
                
                <div 
                  v-for="record in cropHistory" 
                  :key="record.id"
                  class="flex items-center justify-between p-3 bg-slate-50 rounded-xl border border-slate-100"
                >
                  <div class="flex items-center space-x-3">
                    <div class="w-9 h-9 bg-orange-50 rounded-lg flex items-center justify-center text-sm overflow-hidden">
                      <img v-if="record.imageUrl" :src="record.imageUrl" class="w-full h-full object-cover" />
                      <span v-else>🍃</span>
                    </div>
                    <div>
                      <div class="text-xs font-bold text-slate-700">{{ record.pestName }}</div>
                      <div class="text-[10px] text-slate-400">{{ new Date(record.time).toLocaleDateString() }}</div>
                    </div>
                  </div>
                  <span class="text-xs font-bold" :class="record.confidence >= 0.8 ? 'text-green-500' : 'text-orange-500'">
                    {{ (record.confidence * 100).toFixed(0) }}%
                  </span>
                </div>
              </div>
            </transition>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.scrollbar-hide::-webkit-scrollbar {
  display: none;
}
.scrollbar-hide {
  -ms-overflow-style: none;
  scrollbar-width: none;
}

:global(.dark) .prediction-page {
  background:
    radial-gradient(circle at top, rgba(16, 185, 129, 0.18), transparent 34%),
    linear-gradient(180deg, #020617 0%, #0f172a 45%, #111827 100%);
}

:global(.dark) .prediction-page :deep([class~='bg-white']) {
  background-color: rgba(15, 23, 42, 0.88) !important;
}

:global(.dark) .prediction-page :deep([class~='bg-slate-50']),
:global(.dark) .prediction-page :deep([class~='bg-slate-50/50']) {
  background-color: rgba(30, 41, 59, 0.78) !important;
}

:global(.dark) .prediction-page :deep([class~='bg-slate-100']) {
  background-color: rgba(51, 65, 85, 0.82) !important;
}

:global(.dark) .prediction-page :deep([class~='border-slate-50']),
:global(.dark) .prediction-page :deep([class~='border-slate-100']),
:global(.dark) .prediction-page :deep([class~='border-slate-200']) {
  border-color: rgba(71, 85, 105, 0.76) !important;
}

:global(.dark) .prediction-page :deep([class~='text-slate-900']),
:global(.dark) .prediction-page :deep([class~='text-slate-800']) {
  color: #f8fafc !important;
}

:global(.dark) .prediction-page :deep([class~='text-slate-700']),
:global(.dark) .prediction-page :deep([class~='text-slate-600']) {
  color: #e2e8f0 !important;
}

:global(.dark) .prediction-page :deep([class~='text-slate-500']),
:global(.dark) .prediction-page :deep([class~='text-slate-400']) {
  color: #94a3b8 !important;
}

:global(.dark) .prediction-page :deep([class~='bg-red-50']),
:global(.dark) .prediction-page :deep([class~='bg-red-100']) {
  background-color: rgba(239, 68, 68, 0.16) !important;
}

:global(.dark) .prediction-page :deep([class~='border-red-100']) {
  border-color: rgba(248, 113, 113, 0.28) !important;
}

:global(.dark) .prediction-page :deep([class~='bg-green-50']) {
  background-color: rgba(16, 185, 129, 0.16) !important;
}

:global(.dark) .prediction-page :deep([class~='bg-blue-50/50']) {
  background-color: rgba(59, 130, 246, 0.14) !important;
}

:global(.dark) .prediction-page :deep([class~='border-blue-100/50']) {
  border-color: rgba(96, 165, 250, 0.24) !important;
}

:global(.dark) .prediction-page :deep([class~='bg-blue-100']) {
  background-color: rgba(59, 130, 246, 0.2) !important;
}

:global(.dark) .prediction-page :deep([class~='bg-orange-50/50']) {
  background-color: rgba(249, 115, 22, 0.14) !important;
}

:global(.dark) .prediction-page :deep([class~='border-orange-100/50']) {
  border-color: rgba(251, 146, 60, 0.24) !important;
}

:global(.dark) .prediction-page :deep([class~='bg-orange-100']) {
  background-color: rgba(249, 115, 22, 0.2) !important;
}

:global(.dark) .prediction-page :deep([class~='bg-indigo-50/50']) {
  background-color: rgba(99, 102, 241, 0.14) !important;
}

:global(.dark) .prediction-page :deep([class~='border-indigo-100/50']) {
  border-color: rgba(129, 140, 248, 0.24) !important;
}

:global(.dark) .prediction-page :deep([class~='bg-indigo-100']) {
  background-color: rgba(99, 102, 241, 0.2) !important;
}

:global(.dark) .prediction-page :deep([class~='bg-teal-50/50']) {
  background-color: rgba(20, 184, 166, 0.14) !important;
}

:global(.dark) .prediction-page :deep([class~='border-teal-100/50']) {
  border-color: rgba(45, 212, 191, 0.24) !important;
}

:global(.dark) .prediction-page :deep([class~='bg-teal-100']) {
  background-color: rgba(20, 184, 166, 0.2) !important;
}
</style>
