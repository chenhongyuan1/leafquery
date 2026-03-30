<script setup>
import { ref } from 'vue'
import axios from 'axios'
import { normalizeDiagnosisResponse } from '../../utils/diagnosisSelection'

const isScanning = ref(false)
const previewUrl = ref(null)
const fileInput = ref(null)
const errorMsg = ref('')

const emit = defineEmits(['scan-complete'])

const startScan = () => {
  errorMsg.value = ''
  fileInput.value?.click()
}

const onFileSelected = async event => {
  const file = event.target.files?.[0]
  if (!file) return

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

    await new Promise(resolve => setTimeout(resolve, 1200))
    emit('scan-complete', normalizeDiagnosisResponse(response.data, previewUrl.value))
  } catch (error) {
    let message = error.response?.data?.error || error.response?.data?.message || error.message || '识别失败，请重试'
    if (message.includes('Network Error')) message = '网络连接失败，请检查网络后重试'
    if (message.includes('timeout') || message.includes('Timeout')) message = '请求超时，服务处理时间过长'
    if (message.includes('500') || message.includes('Internal Server Error')) message = '服务暂时不可用，请稍后再试'
    errorMsg.value = message
    console.error('Scan overlay detect failed:', error)
  } finally {
    isScanning.value = false
    event.target.value = ''
  }
}

defineExpose({ startScan, isScanning })
</script>

<template>
  <div class="absolute inset-0 w-full h-full overflow-hidden rounded-[2rem] bg-slate-950 bg-aurora">
    <div class="absolute top-[0%] left-[0%] w-[50%] h-[50%] bg-emerald-500/30 rounded-full mix-blend-screen filter blur-[60px] animate-blob"></div>
    <div class="absolute bottom-[0%] right-[0%] w-[50%] h-[50%] bg-blue-500/40 rounded-full mix-blend-screen filter blur-[60px] animate-blob animation-delay-2000"></div>
    <div class="absolute top-[10%] right-[30%] w-[60%] h-[60%] bg-cyan-500/35 rounded-full mix-blend-screen filter blur-[70px] animate-blob animation-delay-4000"></div>

    <input
      ref="fileInput"
      type="file"
      accept="image/*"
      class="absolute inset-0 w-full h-full opacity-0 cursor-pointer z-50"
      :disabled="isScanning"
      @change="onFileSelected"
    />

    <div class="absolute inset-0">
      <img
        v-if="previewUrl"
        :src="previewUrl"
        alt="识别预览"
        class="w-full h-full object-cover transition-transform duration-500"
        :class="isScanning ? 'scale-105' : 'scale-100'"
      />
    </div>

    <div v-if="isScanning" class="absolute inset-0 z-10">
      <div class="scan-line w-full h-1 bg-gradient-to-r from-transparent via-green-400 to-transparent absolute top-0 shadow-[0_0_15px_rgba(74,222,128,0.8)]"></div>
      <div class="absolute inset-0 bg-green-500/10 animate-pulse"></div>
      <div class="absolute top-4 left-4 w-8 h-8 border-t-2 border-l-2 border-green-400 rounded-tl-lg"></div>
      <div class="absolute top-4 right-4 w-8 h-8 border-t-2 border-r-2 border-green-400 rounded-tr-lg"></div>
      <div class="absolute bottom-4 left-4 w-8 h-8 border-b-2 border-l-2 border-green-400 rounded-bl-lg"></div>
      <div class="absolute bottom-4 right-4 w-8 h-8 border-b-2 border-r-2 border-green-400 rounded-br-lg"></div>
    </div>

    <div v-if="errorMsg" class="absolute bottom-20 inset-x-0 flex justify-center z-20">
      <div class="bg-red-500/90 backdrop-blur-sm text-white px-4 py-2 rounded-xl text-sm font-medium shadow-lg animate-bounce-in">
        提示：{{ errorMsg }}
      </div>
    </div>

    <div v-if="!isScanning" class="absolute inset-0 bg-black/40 flex flex-col items-center justify-center pointer-events-none z-40">
      <div class="relative flex flex-col items-center justify-center animate-tap mt-4">
        <div class="absolute w-20 h-20 bg-green-500/40 rounded-full animate-ping -top-2"></div>
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-16 h-16 text-white drop-shadow-lg z-10">
          <path stroke-linecap="round" stroke-linejoin="round" d="M15.042 21.672 13.684 16.6m0 0-2.51 2.225.569-9.47 5.227 7.917-3.286-.672ZM12 2.25V4.5m5.834.166-1.591 1.591M20.25 10.5H18M7.757 14.743l-1.59 1.59M6 10.5H3.75m4.007-4.243-1.59-1.59" />
        </svg>
        <span class="mt-6 text-white/90 font-medium tracking-wide drop-shadow-md bg-black/20 px-4 py-1.5 rounded-full border border-white/10 backdrop-blur-sm">
          点击该区域开始识别
        </span>
      </div>
    </div>

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
