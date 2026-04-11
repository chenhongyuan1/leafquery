<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import axios from 'axios'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent, MarkLineComponent } from 'echarts/components'
import VChart from 'vue-echarts'

use([CanvasRenderer, PieChart, BarChart, LineChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent, MarkLineComponent])

const isDark = ref(document.documentElement.classList.contains('dark'))
let themeObserver = null

// ==== Dashboard Data ====
const recordCount = ref(0)
const diseaseCount = ref(0)
const diseaseDistribution = ref([])
const historyItems = ref([])

onMounted(async () => {
  themeObserver = new MutationObserver(() => {
    isDark.value = document.documentElement.classList.contains('dark')
  })
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })

  try {
    const userStr = localStorage.getItem('user')
    if (!userStr) return
    const user = JSON.parse(userStr)
    if (!user?.userId) return

    const res = await axios.get(`/api/record/list?userId=${user.userId}`)
    if (res.data?.code === 200) {
      const data = res.data.data || []
      historyItems.value = data
      recordCount.value = data.length

      // Build disease distribution
      const pestMap = {}
      data.forEach(item => {
        const name = item.pestName || '未知'
        if (name.includes('健康') || name.includes('Healthy') || name === 'unknown') return
        pestMap[name] = (pestMap[name] || 0) + 1
      })
      diseaseCount.value = Object.keys(pestMap).length
      diseaseDistribution.value = Object.entries(pestMap)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 10) // Top 10
        .map(([name, value]) => ({ name, value }))
    }
  } catch (e) { console.error('Data fetch failed', e) }
})

onUnmounted(() => {
  themeObserver?.disconnect()
})

// ==== ECharts Configs ====

// 1. Pie Chart
const pieOption = computed(() => {
  const t = isDark.value
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c}次 ({d}%)', backgroundColor: t ? '#1e293b' : '#fff', borderColor: t ? '#334155' : '#e2e8f0', textStyle: { color: t ? '#e2e8f0' : '#334155' } },
    legend: { bottom: 10, textStyle: { color: t ? '#94a3b8' : '#64748b', fontSize: 12 }, itemWidth: 12, itemHeight: 12 },
    series: [{
      type: 'pie', radius: ['45%', '75%'], center: ['50%', '40%'],
      itemStyle: { borderRadius: 8, borderColor: t ? '#0f172a' : '#fff', borderWidth: 3 },
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold', color: t ? '#fff' : '#1e293b' }, itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.2)' } },
      data: diseaseDistribution.value.length > 0 ? diseaseDistribution.value : [{ name: '暂无数据', value: 1 }],
      color: ['#10b981', '#3b82f6', '#f59e0b', '#ef4444', '#8b5cf6', '#ec4899', '#14b8a6', '#f97316', '#6366f1', '#14b8a6']
    }]
  }
})

// 2. Horizontal Bar Chart
const barOption = computed(() => {
  const t = isDark.value
  const items = diseaseDistribution.value.slice(0, 8)
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, backgroundColor: t ? '#1e293b' : '#fff', borderColor: t ? '#334155' : '#e2e8f0', textStyle: { color: t ? '#e2e8f0' : '#334155' } },
    grid: { left: 100, right: 30, top: 20, bottom: 20 },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: t ? '#1e293b' : '#f1f5f9' } }, axisLabel: { color: t ? '#94a3b8' : '#64748b' } },
    yAxis: {
      type: 'category', data: items.map(d => d.name), inverse: true,
      axisLine: { show: false }, axisTick: { show: false },
      axisLabel: { color: t ? '#cbd5e1' : '#475569', fontSize: 12, fontWeight: 600 }
    },
    series: [{
      type: 'bar', data: items.map(d => d.value), barWidth: 18, itemStyle: {
        borderRadius: [0, 8, 8, 0],
        color: { type: 'linear', x: 0, y: 0, x2: 1, y2: 0, colorStops: [{ offset: 0, color: '#3b82f6' }, { offset: 1, color: '#60a5fa' }] }
      },
      label: { show: true, position: 'right', fontSize: 12, fontWeight: 700, color: t ? '#93c5fd' : '#2563eb' }
    }]
  }
})

// 3. Trend Forecast Line Chart
const trendOption = computed(() => {
  const t = isDark.value
  const days = ['今天', '明天', '后天', '+3天', '+4天', '+5天', '+6天']
  const series = [22, 28, 35, 42, 60, 55, 48]
  return {
    tooltip: { trigger: 'axis', backgroundColor: t ? '#1e293b' : '#fff', borderColor: t ? '#334155' : '#e2e8f0', textStyle: { color: t ? '#e2e8f0' : '#334155' }, formatter: (p) => `${p[0].name}<br/>风险指数: <b>${p[0].value}%</b>` },
    grid: { left: 40, right: 40, top: 30, bottom: 30 },
    xAxis: { type: 'category', data: days, axisLine: { lineStyle: { color: t ? '#334155' : '#e2e8f0' } }, axisLabel: { color: t ? '#94a3b8' : '#64748b', fontSize: 12 } },
    yAxis: { type: 'value', min: 0, max: 100, splitLine: { lineStyle: { color: t ? '#1e293b' : '#f1f5f9', type: 'dashed' } }, axisLabel: { color: t ? '#94a3b8' : '#64748b', fontSize: 11, formatter: '{value}%' } },
    series: [{
      type: 'line', data: series, smooth: true, symbol: 'circle', symbolSize: 10,
      lineStyle: { width: 4, color: '#10b981' },
      areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{ offset: 0, color: 'rgba(16,185,129,0.4)' }, { offset: 1, color: 'rgba(16,185,129,0.02)' }] } },
      itemStyle: { color: '#10b981', borderColor: t ? '#0f172a' : '#fff', borderWidth: 2 },
      markLine: { silent: true, symbol: 'none', data: [
        { yAxis: 75, lineStyle: { color: '#ef4444', type: 'dashed', width: 2 }, label: { formatter: '高危 75%', color: '#ef4444', fontSize: 10, fontWeight: 700 } },
        { yAxis: 30, lineStyle: { color: '#eab308', type: 'dashed', width: 2 }, label: { formatter: '轻风险 30%', color: '#eab308', fontSize: 10, fontWeight: 700 } }
      ]}
    }]
  }
})

</script>

<template>
  <div class="p-8 h-full flex flex-col overflow-y-auto custom-scrollbar">
    
    <!-- Header -->
    <div class="mb-8 flex justify-between items-end">
      <div>
        <h2 class="text-3xl font-black text-slate-800 dark:text-slate-100 tracking-tight">数据中心</h2>
        <p class="text-slate-500 dark:text-slate-400 mt-1 font-medium">全局农业病害风险预测大屏与统计分析</p>
      </div>
      <!-- Fast Stats -->
      <div class="flex gap-4 hidden lg:flex">
         <div class="bg-white dark:bg-slate-900 px-6 py-4 rounded-2xl border border-slate-100 dark:border-slate-800 shadow-sm flex items-center gap-4">
            <div class="text-3xl">🔬</div>
            <div>
               <div class="text-xs font-bold text-slate-400">总识别次数</div>
               <div class="text-2xl font-black text-slate-800 dark:text-slate-100">{{ recordCount }}</div>
            </div>
         </div>
         <div class="bg-white dark:bg-slate-900 px-6 py-4 rounded-2xl border border-slate-100 dark:border-slate-800 shadow-sm flex items-center gap-4">
            <div class="text-3xl">🦠</div>
            <div>
               <div class="text-xs font-bold text-slate-400">发现病害种类</div>
               <div class="text-2xl font-black text-slate-800 dark:text-slate-100">{{ diseaseCount }}</div>
            </div>
         </div>
      </div>
    </div>
    
    <div class="grid grid-cols-1 xl:grid-cols-3 gap-6">
      
      <!-- Chart 1: Trend Line -->
      <div class="xl:col-span-2 bg-white dark:bg-slate-900 rounded-[2rem] border border-slate-100 dark:border-slate-800 shadow-sm p-6 lg:p-8 hover:shadow-md transition-shadow">
        <div class="flex items-center justify-between mb-6">
          <h3 class="font-bold text-xl text-slate-800 dark:text-slate-100 flex items-center">
             <span class="text-green-500 mr-2 border-l-4 border-green-500 pl-2">7天大盘风险趋势</span>
          </h3>
          <span class="text-xs font-bold px-3 py-1 rounded-lg bg-green-50 text-green-600 border border-green-100">实时预测</span>
        </div>
        <v-chart :option="trendOption" autoresize style="height: 350px;" />
      </div>

      <!-- Chart 2: Dist Pie -->
      <div class="bg-white dark:bg-slate-900 rounded-[2rem] border border-slate-100 dark:border-slate-800 shadow-sm p-6 lg:p-8 hover:shadow-md transition-shadow">
        <h3 class="font-bold text-xl text-slate-800 dark:text-slate-100 flex items-center mb-6">
           <span class="text-blue-500 mr-2 border-l-4 border-blue-500 pl-2">危害类型占比</span>
        </h3>
        <v-chart :option="pieOption" autoresize style="height: 350px;" />
      </div>

      <!-- Chart 3: Top Bar -->
      <div class="xl:col-span-3 bg-white dark:bg-slate-900 rounded-[2rem] border border-slate-100 dark:border-slate-800 shadow-sm p-6 lg:p-8 hover:shadow-md transition-shadow">
        <h3 class="font-bold text-xl text-slate-800 dark:text-slate-100 flex items-center mb-6">
           <span class="text-indigo-500 mr-2 border-l-4 border-indigo-500 pl-2">病害发生频率排行榜</span>
        </h3>
        <v-chart :option="barOption" autoresize style="height: 400px;" />
      </div>
      
    </div>
  </div>
</template>

<style scoped>
.custom-scrollbar::-webkit-scrollbar { width: 6px; }
.custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
.custom-scrollbar::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }
.custom-scrollbar::-webkit-scrollbar-thumb:hover { background: #94a3b8; }
</style>
