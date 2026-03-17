<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useSettingsStore } from '../stores/settings'

const router = useRouter()
const settingsStore = useSettingsStore()

const isUserLoggedIn = ref(false)
const showClearAnimation = ref(false)
const cacheSize = ref('43.2 MB')
const pushEnabled = ref(true)
const darkMode = ref(false)

const showDialectSheet = ref(false)
const showLanguageSheet = ref(false)
const selectedLanguage = ref('zh-CN')

const dialectOptions = [
  { label: '普通话', value: 'mandarin' },
  { label: '粤语 (广东话)', value: 'cantonese' },
  { label: '上海话 (吴语)', value: 'shanghainese' },
  { label: '四川话 (西南官话)', value: 'sichuan' },
  { label: '闽南语 (泉漳片)', value: 'minnan' },
  { label: '陕西话 (中原官话)', value: 'shaanxi' }
]

const languageOptions = [
  { label: '简体中文 (Chinese)', value: 'zh-CN' },
  { label: 'English', value: 'en-US' }
]

const getDialectLabel = (val) => dialectOptions.find(o => o.value === val)?.label || '普通话'
const getLanguageLabel = (val) => languageOptions.find(o => o.value === val)?.label || '简体中文 (Chinese)'
onMounted(() => {
  const userStr = localStorage.getItem('user')
  isUserLoggedIn.value = !!userStr
})

const goBack = () => {
  router.back()
}

const handleLogout = () => {
  localStorage.removeItem('user')
  // We can also clear other auth-related items here
  localStorage.setItem('selected_tab', 'home')
  
  // Create a small visual delay for UX
  setTimeout(() => {
    router.push('/')
  }, 100)
}

const clearCache = () => {
  if (cacheSize.value === '0 KB') return
  
  showClearAnimation.value = true
  // Fake calculation animation
  let counter = 43
  const interval = setInterval(() => {
    counter -= 5
    if (counter <= 0) {
      clearInterval(interval)
      cacheSize.value = '0 KB'
      showClearAnimation.value = false
    } else {
      cacheSize.value = counter.toFixed(1) + ' MB'
    }
  }, 100)
}
</script>

<template>
  <div class="px-4 pt-1 pb-12 min-h-screen bg-slate-50 flex flex-col relative selection:bg-transparent">
    
    <!-- Toast Placeholder (Would use global store in real app) -->
    
    <!-- Header -->
    <div class="flex items-center mb-6 pt-4 sticky top-0 z-10 bg-slate-50/90 backdrop-blur-md pb-4">
      <button @click="goBack" class="w-10 h-10 flex items-center justify-center bg-white rounded-full shadow-sm text-slate-600 active:scale-90 transition-transform relative z-10">
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-5 h-5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 19.5L8.25 12l7.5-7.5" />
        </svg>
      </button>
      <h2 class="text-xl font-bold text-slate-800 absolute left-1/2 -translate-x-1/2 w-full text-center pointer-events-none">系统设置</h2>
    </div>

    <div class="flex-1 overflow-y-auto custom-scrollbar -mx-4 px-4 pb-20 space-y-6">

      <!-- Section: 账号与安全 -->
      <div v-show="isUserLoggedIn" v-motion-slide-visible-once-bottom :delay="50">
        <h3 class="text-xs font-bold text-slate-400 pl-4 mb-2 tracking-widest uppercase">账号与安全</h3>
        <div class="bg-white rounded-3xl p-2 shadow-[0_4px_20px_rgb(0,0,0,0.03)] border border-slate-50 overflow-hidden flex flex-col justify-center">
          <button class="w-full h-14 flex items-center justify-between px-4 active:bg-slate-50 transition-colors">
            <span class="text-[15px] font-bold text-slate-700">修改密码</span>
            <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M9 5l7 7-7 7" /></svg>
          </button>
          <div class="h-px bg-slate-100 mx-4"></div>
          <button class="w-full h-14 flex items-center justify-between px-4 active:bg-slate-50 transition-colors">
            <span class="text-[15px] font-bold text-slate-700">注销账号</span>
            <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M9 5l7 7-7 7" /></svg>
          </button>
        </div>
      </div>

      <!-- Section: 通用偏好 -->
      <div v-motion-slide-visible-once-bottom :delay="100">
        <h3 class="text-xs font-bold text-slate-400 pl-4 mb-2 tracking-widest uppercase">设备与交互</h3>
        <div class="bg-white rounded-3xl p-2 shadow-[0_4px_20px_rgb(0,0,0,0.03)] border border-slate-50 overflow-hidden">
          
          <!-- 方言设置 (Dialect) -->
          <button @click="showDialectSheet = true" class="w-full h-14 flex items-center justify-between px-4 active:bg-slate-50 transition-colors">
            <div class="flex items-center">
              <span class="text-xl mr-3 opacity-50">🗣️</span>
              <span class="text-[15px] font-bold text-slate-700">识别方言录入</span>
            </div>
            <div class="flex items-center space-x-2">
              <span class="text-[13px] font-bold text-slate-500">{{ getDialectLabel(settingsStore.selectedDialect) }}</span>
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M9 5l7 7-7 7" /></svg>
            </div>
          </button>
          
          <div class="h-px bg-slate-100 mx-4"></div>

          <!-- 语言切换 -->
          <button @click="showLanguageSheet = true" class="w-full h-14 flex items-center justify-between px-4 active:bg-slate-50 transition-colors">
            <div class="flex items-center">
              <span class="text-xl mr-3 opacity-50">🌐</span>
              <span class="text-[15px] font-bold text-slate-700">界面语言</span>
            </div>
            <div class="flex items-center space-x-2">
              <span class="text-[13px] font-bold text-slate-500">{{ getLanguageLabel(selectedLanguage) }}</span>
              <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M9 5l7 7-7 7" /></svg>
            </div>
          </button>

          <div class="h-px bg-slate-100 mx-4"></div>

          <!-- 深色模式 -->
          <button @click="darkMode = !darkMode" class="w-full h-14 flex items-center justify-between px-4 active:bg-slate-50 transition-colors">
            <div class="flex items-center">
              <span class="text-xl mr-3 opacity-50">🌙</span>
              <span class="text-[15px] font-bold text-slate-700">深色模式</span>
            </div>
            <div class="w-12 h-6 rounded-full p-1 transition-colors duration-300 ease-in-out relative flex items-center" :class="darkMode ? 'bg-slate-800' : 'bg-slate-200'">
              <div class="w-4 h-4 rounded-full bg-white shadow-sm transform transition-transform duration-300 ease-in-out" :class="darkMode ? 'translate-x-6' : 'translate-x-0'"></div>
            </div>
          </button>
          
        </div>
      </div>

      <!-- Section: 数据维护 -->
      <div v-motion-slide-visible-once-bottom :delay="150">
        <h3 class="text-xs font-bold text-slate-400 pl-4 mb-2 tracking-widest uppercase">数据维护与系统</h3>
        <div class="bg-white rounded-3xl p-2 shadow-[0_4px_20px_rgb(0,0,0,0.03)] border border-slate-50 overflow-hidden">
          
          <button @click="clearCache" class="w-full h-14 flex items-center justify-between px-4 active:bg-slate-50 transition-colors group">
            <span class="text-[15px] font-bold text-slate-700">清除本地缓存</span>
            <div class="flex items-center space-x-2">
              <span class="text-[13px] font-bold transition-all" :class="cacheSize === '0 KB' ? 'text-green-500' : 'text-slate-400'">{{ cacheSize }}</span>
              <svg v-if="cacheSize !== '0 KB'" xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 text-slate-300 group-hover:text-slate-500 transition-colors" :class="{'animate-spin text-green-500 shadow-xl': showClearAnimation}" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
              </svg>
              <svg v-else xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="3" d="M5 13l4 4L19 7" />
              </svg>
            </div>
          </button>
          

          
        </div>
      </div>

      <div class="h-6"></div>

    </div>

    <!-- 方言选择 Bottom Sheet -->
    <Transition
      enter-active-class="transition duration-300 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-200 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div v-if="showDialectSheet" class="fixed inset-0 z-[100] flex items-end justify-center sm:items-center">
        <!-- Backdrop -->
        <div class="absolute inset-0 bg-slate-900/40 backdrop-blur-sm" @click="showDialectSheet = false"></div>
        
        <!-- Sheet Content -->
        <Transition
          enter-active-class="transition duration-300 ease-out"
          enter-from-class="translate-y-full sm:translate-y-4 sm:opacity-0"
          enter-to-class="translate-y-0 sm:translate-y-0 sm:opacity-100"
          leave-active-class="transition duration-200 ease-in"
          leave-from-class="translate-y-0 sm:translate-y-0 sm:opacity-100"
          leave-to-class="translate-y-full sm:translate-y-4 sm:opacity-0"
        >
          <div v-if="showDialectSheet" class="relative z-10 w-full sm:w-[400px] bg-white rounded-t-[2rem] sm:rounded-3xl pt-6 pb-8 px-6 shadow-2xl flex flex-col max-h-[85vh]">
            <div class="w-12 h-1.5 bg-slate-200 rounded-full mx-auto mb-6 shrink-0"></div>
            <h3 class="text-xl font-bold text-slate-800 text-center mb-6">选择识别方言</h3>
            <div class="flex-1 overflow-y-auto custom-scrollbar space-y-2">
              <button 
                v-for="opt in dialectOptions" 
                :key="opt.value"
                @click="settingsStore.selectedDialect = opt.value; showDialectSheet = false"
                class="w-full flex items-center justify-between p-4 rounded-2xl border-2 transition-all active:scale-[0.98]"
                :class="settingsStore.selectedDialect === opt.value ? 'border-green-500 bg-green-50/50' : 'border-transparent bg-slate-50 hover:bg-slate-100'"
              >
                <span class="font-bold text-[15px]" :class="settingsStore.selectedDialect === opt.value ? 'text-green-600' : 'text-slate-700'">{{ opt.label }}</span>
                <div 
                  class="w-6 h-6 rounded-full border-2 flex items-center justify-center transition-colors"
                  :class="settingsStore.selectedDialect === opt.value ? 'border-green-500 bg-green-500 text-white' : 'border-slate-300 bg-white text-transparent'"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-4 h-4"><path fill-rule="evenodd" d="M16.704 4.153a.75.75 0 01.143 1.052l-8 10.5a.75.75 0 01-1.127.075l-4.5-4.5a.75.75 0 011.06-1.06l3.894 3.893 7.48-9.817a.75.75 0 011.05-.143z" clip-rule="evenodd" /></svg>
                </div>
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>

    <!-- 语言切换 Bottom Sheet -->
    <Transition
      enter-active-class="transition duration-300 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-200 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div v-if="showLanguageSheet" class="fixed inset-0 z-[100] flex items-end justify-center sm:items-center">
        <!-- Backdrop -->
        <div class="absolute inset-0 bg-slate-900/40 backdrop-blur-sm" @click="showLanguageSheet = false"></div>
        
        <!-- Sheet Content -->
        <Transition
          enter-active-class="transition duration-300 ease-out"
          enter-from-class="translate-y-full sm:translate-y-4 sm:opacity-0"
          enter-to-class="translate-y-0 sm:translate-y-0 sm:opacity-100"
          leave-active-class="transition duration-200 ease-in"
          leave-from-class="translate-y-0 sm:translate-y-0 sm:opacity-100"
          leave-to-class="translate-y-full sm:translate-y-4 sm:opacity-0"
        >
          <div v-if="showLanguageSheet" class="relative z-10 w-full sm:w-[400px] bg-white rounded-t-[2rem] sm:rounded-3xl pt-6 pb-8 px-6 shadow-2xl flex flex-col max-h-[85vh]">
            <div class="w-12 h-1.5 bg-slate-200 rounded-full mx-auto mb-6 shrink-0"></div>
            <h3 class="text-xl font-bold text-slate-800 text-center mb-6">切换界面语言</h3>
            <div class="flex-1 overflow-y-auto custom-scrollbar space-y-2">
              <button 
                v-for="opt in languageOptions" 
                :key="opt.value"
                @click="selectedLanguage = opt.value; showLanguageSheet = false"
                class="w-full flex items-center justify-between p-4 rounded-2xl border-2 transition-all active:scale-[0.98]"
                :class="selectedLanguage === opt.value ? 'border-green-500 bg-green-50/50' : 'border-transparent bg-slate-50 hover:bg-slate-100'"
              >
                <span class="font-bold text-[15px]" :class="selectedLanguage === opt.value ? 'text-green-600' : 'text-slate-700'">{{ opt.label }}</span>
                <div 
                  class="w-6 h-6 rounded-full border-2 flex items-center justify-center transition-colors"
                  :class="selectedLanguage === opt.value ? 'border-green-500 bg-green-500 text-white' : 'border-slate-300 bg-white text-transparent'"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-4 h-4"><path fill-rule="evenodd" d="M16.704 4.153a.75.75 0 01.143 1.052l-8 10.5a.75.75 0 01-1.127.075l-4.5-4.5a.75.75 0 011.06-1.06l3.894 3.893 7.48-9.817a.75.75 0 011.05-.143z" clip-rule="evenodd" /></svg>
                </div>
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>

  </div>
</template>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  display: none;
}
.custom-scrollbar {
  -ms-overflow-style: none;
  scrollbar-width: none;
}
</style>
