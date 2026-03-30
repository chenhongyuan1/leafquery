<script setup>
import { ref } from 'vue'
import axios from 'axios'

const isScanning = ref(false)
const previewUrl = ref(null)
const fileInput = ref(null)
const errorMsg = ref('')

const emit = defineEmits(['scan-complete'])

// 点击“开始识别”按钮时，触发隐藏的 file input
const startScan = () => {
  errorMsg.value = ''
  fileInput.value?.click()
}

// 用户选择或拍摄图片后
const onFileSelected = async (event) => {
  const file = event.target.files?.[0]
  if (!file) return

  // 在扫描区显示预览
  previewUrl.value = URL.createObjectURL(file)
  isScanning.value = true
  errorMsg.value = ''

  try {
    const formData = new FormData()
    formData.append('file', file)

    const response = await axios.post('/api/pest/detect', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 30000
    })

    // 模拟最少 2 秒扫描动画，让用户看到反馈
    await new Promise(resolve => setTimeout(resolve, 2000))

    isScanning.value = false
    
    // 解析新的嵌套返回结构 { prediction, report }
    const predictionRaw = response.data.prediction || response.data
    const savedImageUrl = response.data.imageUrl || previewUrl.value
    
    let finalPestName = predictionRaw.pest_name || predictionRaw.pestName || predictionRaw.primary_target_zh || predictionRaw.primaryTargetZh || 'unknown'
    if (String(finalPestName).toLowerCase() === 'unknown' || finalPestName === '未识别') {
      finalPestName = '未能识别出具体病虫害'
    }

    emit('scan-complete', {
      pestName: finalPestName,
      confidence: predictionRaw.confidence || predictionRaw.primary_confidence || predictionRaw.primaryConfidence || 0,
      primaryTargetZh: predictionRaw.primary_target_zh || predictionRaw.primaryTargetZh || finalPestName,
      primaryConfidence: predictionRaw.primary_confidence || predictionRaw.primaryConfidence || predictionRaw.confidence || 0,
      sceneType: predictionRaw.scene_type || predictionRaw.sceneType || 'single',
      classCount: predictionRaw.class_count ?? predictionRaw.classCount ?? 0,
      targetCount: predictionRaw.target_count ?? predictionRaw.targetCount ?? 0,
      classNamesZh: predictionRaw.class_names_zh || predictionRaw.classNamesZh || [],
      detectedSummary: predictionRaw.detected_summary || predictionRaw.detectedSummary || [],
      report: response.data.report || '',
      imageUrl: savedImageUrl
    })
  } catch (err) {
    isScanning.value = false
    
    let errMsg = err.response?.data?.error || err.response?.data?.message || err.message || '识别失败，请重试'
    // 翻译常见的英文服务端/网络报错
    if (errMsg.includes('Network Error')) errMsg = '网络连接失败，请检查您的网络'
    if (errMsg.includes('timeout') || errMsg.includes('Timeout')) errMsg = '请求超时，服务器处理过慢'
    if (errMsg.includes('500') || errMsg.includes('Internal Server Error')) errMsg = '服务器内部错误，请稍后再试'
    
    errorMsg.value = errMsg
    console.error('识别请求失败:', err)
  }

  // 清空 input，允许再次选择同一张图片
  event.target.value = ''
}

defineExpose({ startScan, isScanning })
</script>

<template>
  <div class="absolute inset-0 w-full h-full overflow-hidden rounded-[2rem] bg-slate-950 bg-aurora">
    <!-- Aurora 动态光斑 -->
    <!-- 适当提高透明度和层次感，让背景更有空间感 -->
    <div class="absolute top-[0%] left-[0%] w-[50%] h-[50%] bg-emerald-500/30 rounded-full mix-blend-screen filter blur-[60px] animate-blob"></div>
    <div class="absolute bottom-[0%] right-[0%] w-[50%] h-[50%] bg-blue-500/40 rounded-full mix-blend-screen filter blur-[60px] animate-blob animation-delay-2000"></div>
    <!-- 补一层紫色光团，让画面更有纵深 -->
    <div class="absolute top-[10%] right-[30%] w-[60%] h-[60%] bg-purple-500/60 rounded-full mix-blend-screen filter blur-[70px] animate-blob animation-delay-4000"></div>
    <!-- 透明文件选择器覆盖整个区域，避免浏览器拦截程序化点击 -->
    <input
      type="file"
      accept="image/*"
      class="absolute inset-0 w-full h-full opacity-0 cursor-pointer z-50"
      :disabled="isScanning"
      @change="onFileSelected"
    />

    <!-- 相机预览 / 图片预览 -->
    <div class="absolute inset-0">
      <img
        v-if="previewUrl"
        :src="previewUrl"
        alt="识别预览"
        class="w-full h-full object-cover transition-transform duration-500"
        :class="isScanning ? 'scale-105' : 'scale-100'"
      />
    </div>

    <!-- 扫描动画 -->
    <div v-if="isScanning" class="absolute inset-0 z-10">
      <div class="scan-line w-full h-1 bg-gradient-to-r from-transparent via-green-400 to-transparent absolute top-0 shadow-[0_0_15px_rgba(74,222,128,0.8)]"></div>
      <div class="absolute inset-0 bg-green-500/10 animate-pulse"></div>

      <!-- Corner Markers -->
      <div class="absolute top-4 left-4 w-8 h-8 border-t-2 border-l-2 border-green-400 rounded-tl-lg"></div>
      <div class="absolute top-4 right-4 w-8 h-8 border-t-2 border-r-2 border-green-400 rounded-tr-lg"></div>
      <div class="absolute bottom-4 left-4 w-8 h-8 border-b-2 border-l-2 border-green-400 rounded-bl-lg"></div>
      <div class="absolute bottom-4 right-4 w-8 h-8 border-b-2 border-r-2 border-green-400 rounded-br-lg"></div>
    </div>

    <!-- 错误提示 -->
    <div v-if="errorMsg" class="absolute bottom-20 inset-x-0 flex justify-center z-20">
      <div class="bg-red-500/90 backdrop-blur-sm text-white px-4 py-2 rounded-xl text-sm font-medium shadow-lg animate-bounce-in">
        提示：{{ errorMsg }}
      </div>
    </div>

    <!-- 视觉提示层，不拦截点击，让底层 input 接收事件 -->
    <div v-if="!isScanning" class="absolute inset-0 bg-black/40 flex flex-col items-center justify-center pointer-events-none z-40">
      <div class="relative flex flex-col items-center justify-center animate-tap mt-4">
        <!-- 脉冲波纹动画 -->
        <div class="absolute w-20 h-20 bg-green-500/40 rounded-full animate-ping -top-2"></div>
        <!-- 点击提示图标 -->
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-16 h-16 text-white drop-shadow-lg z-10">
          <path stroke-linecap="round" stroke-linejoin="round" d="M15.042 21.672 13.684 16.6m0 0-2.51 2.225.569-9.47 5.227 7.917-3.286-.672ZM12 2.25V4.5m5.834.166-1.591 1.591M20.25 10.5H18M7.757 14.743l-1.59 1.59M6 10.5H3.75m4.007-4.243-1.59-1.59" />
        </svg>
        <span class="mt-6 text-white/90 font-medium tracking-wide drop-shadow-md bg-black/20 px-4 py-1.5 rounded-full border border-white/10 backdrop-blur-sm">
          点击该区域开始识别
        </span>
      </div>
    </div>

    <!-- 扫描中文案 -->
    <div v-else class="absolute bottom-8 w-full text-center text-green-300 font-mono text-sm tracking-wider animate-pulse">
      AI 正在分析叶片特征...
    </div>
  </div>
</template>

<style scoped>
.scan-line {
  animation: scan 2s linear infinite;
}

@keyframes scan {
  0% { top: 0%; opacity: 0; }
  10% { opacity: 1; }
  90% { opacity: 1; }
  100% { top: 100%; opacity: 0; }
}

@keyframes bounce-in {
  0% { transform: translateY(20px); opacity: 0; }
  100% { transform: translateY(0); opacity: 1; }
}
.animate-bounce-in { animation: bounce-in 0.3s ease-out; }

@keyframes tap {
  0% { transform: scale(1) translateY(0); }
  10% { transform: scale(0.9) translateY(4px); }
  20% { transform: scale(1) translateY(0); }
  100% { transform: scale(1) translateY(0); }
}
.animate-tap {
  animation: tap 2s ease-in-out infinite;
}

/* Aurora Background Effect */
.bg-aurora {
  background: linear-gradient(-45deg, #020617, #064e3b, #0f172a);
  background-size: 400% 400%;
  animation: gradientBG 15s ease infinite;
}

@keyframes gradientBG {
  0% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
  100% { background-position: 0% 50%; }
}

@keyframes blob {
  0% { transform: translate(0px, 0px) scale(1); }
  33% { transform: translate(30px, -50px) scale(1.1); }
  66% { transform: translate(-20px, 20px) scale(0.9); }
  100% { transform: translate(0px, 0px) scale(1); }
}

.animate-blob {
  animation: blob 8s infinite alternate cubic-bezier(0.4, 0, 0.2, 1);
}
.animation-delay-2000 {
  animation-delay: 2s;
}
.animation-delay-4000 {
  animation-delay: 4s;
}
</style>

