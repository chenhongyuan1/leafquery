<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useFarmStore } from '../stores/farm'
import { useRouter } from 'vue-router'

const router = useRouter()
const farmStore = useFarmStore()

// ========== API 状态 ==========
const loading = ref(false)
const predictionData = ref(null)
const errorMsg = ref('')

// ========== 作物对应的病害选项 ==========
const cropDiseaseMap = {
  '水稻': ['稻瘟病', '纹枯病'],
  '玉米': ['大斑病', '锈病'],
  '冬小麦': ['白粉病', '条锈病'],
  '小麦': ['白粉病', '条锈病'],
}

// 当前选中的病害
const selectedDisease = ref('')

// 当前作物可选病害列表
const availableDiseases = computed(() => {
  const crop = farmStore.activeCrop
  if (!crop) return []
  return cropDiseaseMap[crop.name] || []
})

// ========== 物候期选项 ==========
const cropPhenologyMap = {
  '水稻': ['秧苗期', '分蘖期', '拔节期', '抽穗期', '灌浆期', '成熟期'],
  '玉米': ['苗期', '拔节期', '大喇叭口期', '抽雄期', '灌浆期', '成熟期'],
  '冬小麦': ['越冬期', '返青期', '拔节期', '抽穗期', '灌浆期', '成熟期'],
  '小麦': ['越冬期', '返青期', '拔节期', '抽穗期', '灌浆期', '成熟期'],
}

const availablePhenologies = computed(() => {
  const crop = farmStore.activeCrop
  if (!crop) return []
  return cropPhenologyMap[crop.name] || []
})

// ========== 选中物候期 ==========
const selectedPhenology = ref('')

// ========== 调用后端 API ==========
async function fetchPrediction() {
  const crop = farmStore.activeCrop
  if (!crop) return

  loading.value = true
  errorMsg.value = ''

  try {
    const reqBody = {
      crop: crop.name,
      disease: selectedDisease.value || null,
      regionCode: crop.locationId || '101010100', // 默认北京
      targetDate: new Date().toISOString().split('T')[0],
      forecastDays: 7,
      phenologyStage: selectedPhenology.value || crop.stage || '拔节期',
      reportRiskHint: 0
    }

    const response = await fetch('/api/trend/forecast', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(reqBody)
    })

    if (!response.ok) throw new Error(`HTTP ${response.status}`)
    const data = await response.json()
    predictionData.value = data
    
    // 同步 disease
    if (data.disease && !selectedDisease.value) {
      selectedDisease.value = data.disease
    }
  } catch (e) {
    console.error('趋势预测请求失败:', e)
    errorMsg.value = '预测服务暂时不可用，请稍后重试'
  } finally {
    loading.value = false
  }
}

// 监听作物切换或病害切换 → 重新预测
watch([() => farmStore.activeCropId, selectedDisease, selectedPhenology], () => {
  if (farmStore.activeCrop) {
    // 切换作物时重置病害和物候期
    const diseases = availableDiseases.value
    if (diseases.length > 0 && !diseases.includes(selectedDisease.value)) {
      selectedDisease.value = diseases[0]
    }
    const phenologies = availablePhenologies.value
    if (phenologies.length > 0 && !phenologies.includes(selectedPhenology.value)) {
      selectedPhenology.value = phenologies[0]
    }
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

// ========== 图表数据 ==========
const chartData = computed(() => {
  const series = predictionData.value?.dailySeries || []
  return series.map(d => Math.round(d.riskScore * 100))
})

const chartLabels = computed(() => {
  const series = predictionData.value?.dailySeries || []
  return series.map((d, i) => i === 0 ? '今天' : d.date)
})

const maxChartValue = computed(() => Math.max(...chartData.value, 100))

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

// ========== 柱状图点击详情 ==========
const selectedBarIndex = ref(null)
const toggleBarDetail = (index) => {
  selectedBarIndex.value = selectedBarIndex.value === index ? null : index
}

// ========== 历史记录展开 ==========
const showHistory = ref(false)
const cropHistory = computed(() => {
  if (!farmStore.activeCrop) return []
  return farmStore.getHistoryByCrop(farmStore.activeCropId).slice(0, 5)
})
</script>

<template>
  <div class="px-6 pt-4 min-h-full pb-8">
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

    <!-- ========== A2: 病害 + 物候期 选择器 ========== -->
    <div v-if="farmStore.activeCrop && availableDiseases.length > 0" class="mb-6 flex space-x-3" v-motion-slide-visible-once-bottom :delay="70">
      <!-- 病害选择 -->
      <div class="flex-1">
        <label class="text-[10px] font-bold text-slate-400 mb-1 block">🦠 病害</label>
        <div class="flex space-x-2 overflow-x-auto scrollbar-hide">
          <button
            v-for="disease in availableDiseases"
            :key="disease"
            @click="selectedDisease = disease"
            class="flex-shrink-0 text-xs font-bold px-3 py-1.5 rounded-xl border transition-all active:scale-95"
            :class="selectedDisease === disease
              ? 'border-slate-800 bg-slate-800 text-white shadow-lg'
              : 'border-slate-200 bg-white text-slate-600'"
          >
            {{ disease }}
          </button>
        </div>
      </div>

      <!-- 物候期选择 -->
      <div class="flex-1">
        <label class="text-[10px] font-bold text-slate-400 mb-1 block">🌱 物候期</label>
        <select
          v-model="selectedPhenology"
          class="w-full text-xs font-bold px-3 py-1.5 rounded-xl border border-slate-200 bg-white text-slate-700 outline-none focus:border-green-400"
        >
          <option v-for="p in availablePhenologies" :key="p" :value="p">{{ p }}</option>
        </select>
      </div>
    </div>

    <!-- ========== Loading ==========  -->
    <div v-if="loading" class="flex flex-col items-center justify-center py-16">
      <div class="w-10 h-10 border-3 border-green-200 border-t-green-500 rounded-full animate-spin mb-4"></div>
      <p class="text-sm text-slate-400 font-medium">正在分析气象数据...</p>
    </div>

    <!-- ========== Error ==========  -->
    <div v-else-if="errorMsg" class="bg-red-50 border border-red-100 rounded-2xl p-6 text-center mb-6">
      <div class="text-2xl mb-2">⚠️</div>
      <p class="text-sm text-red-600 font-medium">{{ errorMsg }}</p>
      <button @click="fetchPrediction" class="mt-3 text-xs font-bold text-red-500 bg-red-100 px-4 py-1.5 rounded-lg">重试</button>
    </div>

    <!-- ========== 有数据时展示 ========== -->
    <template v-else-if="predictionData && farmStore.activeCrop">
      <!-- ========== B: 风险等级卡片 ========== -->
      <div 
        class="relative overflow-hidden rounded-[2rem] p-8 text-white mb-6 transform transition-all duration-500 hover:scale-[1.02]" 
        :class="[`shadow-2xl ${riskColorClasses.shadow}`]"
        v-motion-pop-visible-once
      >
        <div class="absolute inset-0 bg-gradient-to-br" :class="riskColorClasses.gradient"></div>
        <div class="absolute top-0 right-0 w-32 h-32 bg-white/10 rounded-full -mr-10 -mt-10 blur-2xl"></div>
        <div class="absolute bottom-0 left-0 w-24 h-24 bg-black/10 rounded-full -ml-8 -mb-8 blur-xl"></div>
        
        <div class="relative z-10">
          <div class="flex justify-between items-start mb-4">
            <div>
              <h2 class="text-white/80 text-xs font-bold tracking-wider uppercase mb-1">
                {{ farmStore.activeCrop.icon }} {{ farmStore.activeCrop.name }} · {{ predictionData.disease }}
              </h2>
              <div class="text-4xl font-black tracking-tighter">{{ riskLevelText }}</div>
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
          <p class="text-white/90 font-medium leading-relaxed text-sm">
            气象规则引擎分析，<span class="font-bold">{{ predictionData.disease }}</span>风险评分 
            <span class="font-bold text-white text-lg">{{ riskPercentage }}%</span>。
            {{ riskPercentage >= 75 ? '建议立即采取防治措施。' : riskPercentage >= 55 ? '建议加强田间巡查并预防性喷洒。' : riskPercentage >= 30 ? '注意观察，保持通风。' : '当前风险可控，保持日常管理即可。' }}
          </p>
        </div>
      </div>

      <!-- ========== C: 主导因子卡片 ========== -->
      <div v-if="predictionData.topDrivers && predictionData.topDrivers.length > 0" class="mb-6" v-motion-slide-visible-once-bottom :delay="100">
        <h2 class="text-sm font-bold text-slate-700 mb-3">🔍 主导因子</h2>
        <div class="bg-white rounded-2xl p-5 border border-slate-100 shadow-sm space-y-3">
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

      <!-- ========== D: 风险走势图 ========== -->
      <div class="bg-white rounded-[2rem] shadow-[0_8px_30px_rgb(0,0,0,0.04)] p-6 mb-6 border border-slate-100" v-motion-slide-visible-once-bottom :delay="150">
        <div class="flex justify-between items-center mb-5">
          <h2 class="text-base font-bold text-slate-900">📊 未来7天风险走势</h2>
          <div class="flex items-center space-x-2">
            <span class="text-xs font-bold text-green-500 bg-green-50 px-2 py-0.5 rounded-lg">LIVE</span>
            <span class="text-[10px] font-bold text-slate-400">{{ predictionData.modelVersion }}</span>
          </div>
        </div>
        
        <!-- 阈值标注 -->
        <div class="relative">
          <div class="absolute left-0 right-0 bottom-[75%] border-b border-dashed border-red-300/60 z-10">
            <span class="absolute -top-3 right-0 text-[8px] font-bold text-red-400 bg-white px-1">75%</span>
          </div>
          <div class="absolute left-0 right-0 bottom-[55%] border-b border-dashed border-orange-300/50 z-10">
            <span class="absolute -top-3 right-0 text-[8px] font-bold text-orange-400 bg-white px-1">55%</span>
          </div>
          <div class="absolute left-0 right-0 bottom-[30%] border-b border-dashed border-amber-200/50 z-10">
            <span class="absolute -top-3 right-0 text-[8px] font-bold text-amber-400 bg-white px-1">30%</span>
          </div>
          
          <div class="h-48 flex items-end justify-between space-x-2 px-1">
            <div 
              v-for="(value, index) in chartData" 
              :key="index" 
              class="flex flex-col items-center flex-1 group cursor-pointer"
              @click="toggleBarDetail(index)"
            >
              <span 
                class="text-[9px] font-bold mb-1 opacity-0 group-hover:opacity-100 transition-opacity"
                :class="value > 55 ? 'text-orange-500' : 'text-green-600'"
              >
                {{ value }}%
              </span>
              
              <div 
                class="w-full rounded-t-lg relative overflow-hidden transition-all duration-500 group-hover:scale-y-105 origin-bottom"
                :class="selectedBarIndex === index ? 'ring-2 ring-slate-900 ring-offset-2 rounded-lg' : ''"
                :style="{ height: (value / maxChartValue * 100) + '%' }"
              >
                <div 
                  class="absolute bottom-0 left-0 w-full h-full transition-all duration-1000 delay-200" 
                  :class="value >= 75 ? 'bg-gradient-to-t from-red-500 to-orange-400' : value >= 55 ? 'bg-gradient-to-t from-orange-400 to-amber-400' : value >= 30 ? 'bg-gradient-to-t from-amber-400 to-yellow-300' : 'bg-gradient-to-t from-green-500 to-emerald-400'"
                ></div>
              </div>
              <span class="text-[10px] font-bold text-slate-400 mt-2 group-hover:text-slate-600 transition-colors">{{ chartLabels[index] }}</span>
            </div>
          </div>
        </div>

        <!-- 点击柱子的详细信息 -->
        <transition
          enter-active-class="transition duration-200 ease-out"
          enter-from-class="opacity-0 -translate-y-2"
          enter-to-class="opacity-100 translate-y-0"
          leave-active-class="transition duration-150 ease-in"
          leave-from-class="opacity-100"
          leave-to-class="opacity-0"
        >
          <div v-if="selectedBarIndex !== null && chartData[selectedBarIndex] !== undefined" class="mt-4 p-3 bg-slate-50 rounded-xl border border-slate-100 text-sm">
            <div class="flex justify-between items-center">
              <span class="font-bold text-slate-700">{{ chartLabels[selectedBarIndex] }} 风险详情</span>
              <span class="font-bold" :class="chartData[selectedBarIndex] >= 55 ? 'text-orange-500' : 'text-green-500'">
                {{ chartData[selectedBarIndex] }}%
              </span>
            </div>
            <p class="text-xs text-slate-500 mt-1">
              {{ chartData[selectedBarIndex] >= 75 ? '高危日，建议当天施药防治' : chartData[selectedBarIndex] >= 55 ? '中风险日，加强田间巡查' : chartData[selectedBarIndex] >= 30 ? '轻风险日，注意观察' : '低风险日，保持常规管理' }}
            </p>
          </div>
        </transition>
      </div>

      <!-- ========== E: 防治建议时间线 ========== -->
      <div 
        v-if="timelineItems.length > 0"
        class="bg-white rounded-[2rem] shadow-[0_8px_30px_rgb(0,0,0,0.04)] p-6 mb-6 border border-slate-100" 
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
              <span class="text-xs font-bold flex-shrink-0 ml-3" :class="{
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

      <!-- ========== F: 环境因子面板 ========== -->
      <div class="mb-6" v-motion-slide-visible-once-bottom :delay="250">
        <h2 class="text-sm font-bold text-slate-700 mb-3">🌡️ 环境因子</h2>
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

      <!-- ========== G: 识别历史 ========== -->
      <div 
        v-if="farmStore.activeCrop"
        class="bg-white rounded-[2rem] shadow-[0_8px_30px_rgb(0,0,0,0.04)] p-6 border border-slate-100" 
        v-motion-slide-visible-once-bottom :delay="300"
      >
        <button @click="showHistory = !showHistory" class="w-full flex justify-between items-center">
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
</style>
