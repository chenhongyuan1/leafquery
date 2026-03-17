<script setup>
import { useRouter } from 'vue-router'

const router = useRouter()

const goBack = () => {
  router.back()
}
</script>

<template>
  <div class="min-h-full bg-slate-50 flex flex-col relative overflow-y-auto overflow-x-hidden">
    <!-- Header -->
    <div class="sticky top-0 z-50 bg-white/80 backdrop-blur-md border-b border-slate-100 flex items-center justify-between px-4 py-4">
      <button @click="goBack" class="p-2 -ml-2 text-slate-400 hover:text-slate-600 active:scale-95 transition-all outline-none">
        <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
        </svg>
      </button>
      <h1 class="text-lg font-extrabold text-slate-800 tracking-wide">关于我们</h1>
      <div class="w-10"></div> <!-- Placeholder to center title -->
    </div>

    <!-- Background Decoration -->
    <div class="absolute top-0 right-0 w-64 h-64 bg-green-100/50 rounded-full blur-3xl -translate-y-1/2 translate-x-1/2 pointer-events-none"></div>

    <div class="flex-1 flex flex-col pt-12 pb-24 px-6 relative z-10">
      <!-- App Header Icon -->
      <div class="flex flex-col items-center justify-center mb-10 text-center" v-motion-slide-visible-once-bottom>
        <div class="w-24 h-24 bg-gradient-to-br from-green-400 to-emerald-600 text-white rounded-[2rem] shadow-xl shadow-green-900/20 flex items-center justify-center text-5xl mb-6 transform rotate-3 hover:rotate-0 transition-transform cursor-default">
          🌱
        </div>
        <h2 class="text-3xl font-black text-slate-800 tracking-tight">LeafQuery</h2>
        <span class="text-sm font-bold text-green-600 mt-2 bg-green-50 px-3 py-1 rounded-full">v1.0.0 • 叶小问大模型版</span>
      </div>

      <!-- Introduction Section -->
      <div class="bg-white rounded-3xl p-6 shadow-[0_8px_30px_rgb(0,0,0,0.04)] mb-8" v-motion-slide-visible-once-bottom :delay="100">
        <h3 class="text-lg font-bold text-slate-800 mb-3 flex items-center">
          <span class="w-8 h-8 rounded-full bg-blue-50 text-blue-500 flex items-center justify-center mr-3 text-sm font-black">1</span>
          我们的使命
        </h3>
        <p class="text-slate-600 leading-relaxed text-sm">
          在这个数字化转型的农业时代，<strong class="text-green-600">LeafQuery</strong> 致力于通过最前沿的机器视觉和深度学习大模型技术，让每一位农场主、农业学者和爱好者，都能在掌心掌握专业的“农业病虫害诊断专家”。
        </p>
      </div>

      <!-- Tech Feature Section -->
      <div class="bg-white rounded-3xl p-6 shadow-[0_8px_30px_rgb(0,0,0,0.04)] mb-8" v-motion-slide-visible-once-bottom :delay="200">
        <h3 class="text-lg font-bold text-slate-800 mb-3 flex items-center">
          <span class="w-8 h-8 rounded-full bg-purple-50 text-purple-500 flex items-center justify-center mr-3 text-sm font-black">2</span>
          核心技术
        </h3>
        <ul class="space-y-4 mt-4">
          <li class="flex items-start">
            <div class="w-6 h-6 rounded-full bg-slate-100 flex items-center justify-center text-xs mt-0.5 mr-3 shrink-0">🎯</div>
            <div>
              <p class="font-bold text-slate-800 text-sm">YOLOv8 实时识别引擎</p>
              <p class="text-xs text-slate-500 mt-1">毫秒级响应，高效识别数十种常见作物病害与虫害侵入情况。</p>
            </div>
          </li>
          <li class="flex items-start">
            <div class="w-6 h-6 rounded-full bg-slate-100 flex items-center justify-center text-xs mt-0.5 mr-3 shrink-0">🤖</div>
            <div>
              <p class="font-bold text-slate-800 text-sm">火山引擎豆包大模型</p>
              <p class="text-xs text-slate-500 mt-1">集成大语言模型，将干瘪的数据转化为通俗易懂の精准防治建议。</p>
            </div>
          </li>
          <li class="flex items-start">
            <div class="w-6 h-6 rounded-full bg-slate-100 flex items-center justify-center text-xs mt-0.5 mr-3 shrink-0">📈</div>
            <div>
              <p class="font-bold text-slate-800 text-sm">气象与趋势预测</p>
              <p class="text-xs text-slate-500 mt-1">结合历史气候数据，洞悉未来的病虫害爆发趋势规律。</p>
            </div>
          </li>
        </ul>
      </div>

      <!-- Copyright / Contact -->
      <div class="mt-8 text-center" v-motion-slide-visible-once-bottom :delay="300">
        <p class="text-xs font-semibold text-slate-400">© 2026 LeafQuery Tech, Inc. All rights reserved.</p>
        <p class="text-[10px] text-slate-400 mt-1">为您提供卓越的农业智能护航</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* Hidden scrollbar */
.overflow-y-auto::-webkit-scrollbar {
  display: none;
}
.overflow-y-auto {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
</style>
