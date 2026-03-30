<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useSettingsStore } from '../../stores/settings'
import { useFarmStore } from '../../stores/farmCloud'
import { useFavoritesStore } from '../../stores/favorites'
import {
  changePassword,
  clearUserSession,
  deleteAccount,
  getAccountErrorMessage,
  getStoredUser
} from '../../utils/accountSecurity'

const router = useRouter()
const settingsStore = useSettingsStore()
const farmStore = useFarmStore()
const favoritesStore = useFavoritesStore()

const currentUser = ref(null)
const isUserLoggedIn = ref(false)
const showClearAnimation = ref(false)
const cacheSize = ref('43.2 MB')
const feedbackMessage = ref('')
const feedbackType = ref('success')

const darkMode = ref(false)
let themeObserver = null

const syncDarkMode = () => {
  darkMode.value = document.documentElement.classList.contains('dark')
}

const toggleTheme = () => {
  const nextDark = !darkMode.value
  darkMode.value = nextDark
  localStorage.setItem('app-theme', nextDark ? 'dark' : 'light')
  document.documentElement.classList.toggle('dark', nextDark)
}

const showDialectSheet = ref(false)
const showLanguageSheet = ref(false)
const showPasswordSheet = ref(false)
const showDeleteSheet = ref(false)
const selectedLanguage = ref('zh-CN')
const passwordSubmitting = ref(false)
const deleteSubmitting = ref(false)
const passwordError = ref('')
const deleteError = ref('')
const passwordForm = ref({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const deleteForm = ref({
  currentPassword: ''
})

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
  { label: 'English(待添加)', value: 'en-US' }
]

const getDialectLabel = (val) => dialectOptions.find(o => o.value === val)?.label || '普通话'
const getLanguageLabel = (val) => languageOptions.find(o => o.value === val)?.label || '简体中文 (Chinese)'
const refreshCurrentUser = () => {
  currentUser.value = getStoredUser()
  isUserLoggedIn.value = !!currentUser.value?.userId
}

const setFeedback = (message, type = 'success') => {
  feedbackMessage.value = message
  feedbackType.value = type
}

const resetPasswordForm = () => {
  passwordForm.value = {
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  }
  passwordError.value = ''
}

const resetDeleteForm = () => {
  deleteForm.value = {
    currentPassword: ''
  }
  deleteError.value = ''
}

const openPasswordSheet = () => {
  resetPasswordForm()
  showPasswordSheet.value = true
}

const openDeleteSheet = () => {
  resetDeleteForm()
  showDeleteSheet.value = true
}

const validatePasswordForm = () => {
  if (!passwordForm.value.currentPassword || !passwordForm.value.newPassword || !passwordForm.value.confirmPassword) {
    return '请完整填写密码信息'
  }
  if (passwordForm.value.newPassword.length < 6) {
    return '新密码长度不能少于6位'
  }
  if (passwordForm.value.currentPassword === passwordForm.value.newPassword) {
    return '新密码不能与当前密码相同'
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    return '两次输入的新密码不一致'
  }
  return ''
}

const submitPasswordChange = async () => {
  const validationMessage = validatePasswordForm()
  if (validationMessage) {
    passwordError.value = validationMessage
    return
  }

  if (!currentUser.value?.userId) {
    passwordError.value = '当前未登录，无法修改密码'
    return
  }

  passwordSubmitting.value = true
  passwordError.value = ''

  try {
    currentUser.value = await changePassword({
      userId: currentUser.value.userId,
      currentPassword: passwordForm.value.currentPassword,
      newPassword: passwordForm.value.newPassword
    })
    isUserLoggedIn.value = true
    setFeedback('密码修改成功')
    showPasswordSheet.value = false
    resetPasswordForm()
  } catch (error) {
    passwordError.value = getAccountErrorMessage(error, '密码修改失败，请稍后重试')
  } finally {
    passwordSubmitting.value = false
  }
}

const submitDeleteAccount = async () => {
  if (!deleteForm.value.currentPassword) {
    deleteError.value = '请输入当前密码以完成账号注销'
    return
  }

  if (!currentUser.value?.userId) {
    deleteError.value = '当前未登录，无法注销账号'
    return
  }

  deleteSubmitting.value = true
  deleteError.value = ''

  try {
    await deleteAccount({
      userId: currentUser.value.userId,
      currentPassword: deleteForm.value.currentPassword
    })
    await clearUserSession({
      farmStore,
      favoritesStore
    })
    refreshCurrentUser()
    setFeedback('账号已注销，已切换到未登录状态')
    showDeleteSheet.value = false
    resetDeleteForm()

    setTimeout(() => {
      router.push('/')
    }, 160)
  } catch (error) {
    deleteError.value = getAccountErrorMessage(error, '账号注销失败，请稍后重试')
  } finally {
    deleteSubmitting.value = false
  }
}

onMounted(() => {
  refreshCurrentUser()
  syncDarkMode()

  themeObserver = new MutationObserver(syncDarkMode)
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class']
  })
})

onUnmounted(() => {
  themeObserver?.disconnect()
})

const goBack = () => {
  router.back()
}

const handleLogout = async () => {
  await clearUserSession({
    farmStore,
    favoritesStore
  })
  refreshCurrentUser()

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
  <div class="settings-page px-4 pt-1 pb-12 min-h-screen bg-slate-50 dark:bg-slate-950 flex flex-col relative selection:bg-transparent transition-colors">
    
    <!-- Toast Placeholder (Would use global store in real app) -->
    
    <!-- Header -->
    <div class="settings-header flex items-center mb-6 pt-4 sticky top-0 z-10 bg-slate-50/90 dark:bg-slate-950/90 backdrop-blur-md pb-4 transition-colors">
      <button @click="goBack" class="w-10 h-10 flex items-center justify-center bg-white dark:bg-slate-900 rounded-full shadow-sm dark:shadow-[0_10px_24px_rgba(2,6,23,0.35)] text-slate-600 dark:text-slate-300 active:scale-90 transition-transform relative z-10">
        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-5 h-5">
          <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 19.5L8.25 12l7.5-7.5" />
        </svg>
      </button>
      <h2 class="text-xl font-bold text-slate-800 absolute left-1/2 -translate-x-1/2 w-full text-center pointer-events-none">系统设置</h2>
    </div>

    <div
      v-if="feedbackMessage"
      class="mx-1 mb-4 rounded-2xl border px-4 py-3 text-sm font-bold"
      :class="feedbackType === 'success'
        ? 'border-emerald-100 bg-emerald-50 text-emerald-600 dark:border-emerald-500/20 dark:bg-emerald-500/10 dark:text-emerald-300'
        : 'border-rose-100 bg-rose-50 text-rose-500 dark:border-rose-500/20 dark:bg-rose-500/10 dark:text-rose-300'"
    >
      {{ feedbackMessage }}
    </div>

    <div class="settings-scroll flex-1 overflow-y-auto custom-scrollbar -mx-4 px-4 pb-20 space-y-6">

      <!-- Section: 账号与安全 -->
      <div v-show="isUserLoggedIn" v-motion-slide-visible-once-bottom :delay="50">
        <h3 class="text-xs font-bold text-slate-400 pl-4 mb-2 tracking-widest uppercase">账号与安全</h3>
        <div class="settings-card bg-white dark:bg-slate-900 rounded-3xl p-2 shadow-[0_4px_20px_rgb(0,0,0,0.03)] dark:shadow-[0_18px_40px_rgba(2,6,23,0.28)] border border-slate-50 dark:border-slate-800 overflow-hidden flex flex-col justify-center transition-colors">
          <button @click="openPasswordSheet" class="w-full h-14 flex items-center justify-between px-4 active:bg-slate-50 transition-colors">
            <span class="text-[15px] font-bold text-slate-700">修改密码</span>
            <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M9 5l7 7-7 7" /></svg>
          </button>
          <div class="h-px bg-slate-100 mx-4"></div>
          <button @click="openDeleteSheet" class="w-full h-14 flex items-center justify-between px-4 active:bg-slate-50 transition-colors">
            <span class="text-[15px] font-bold text-slate-700">注销账号</span>
            <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4 text-slate-300" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M9 5l7 7-7 7" /></svg>
          </button>
        </div>
      </div>

      <!-- Section: 通用偏好 -->
      <div v-motion-slide-visible-once-bottom :delay="100">
        <h3 class="text-xs font-bold text-slate-400 pl-4 mb-2 tracking-widest uppercase">设备与交互</h3>
        <div class="settings-card bg-white dark:bg-slate-900 rounded-3xl p-2 shadow-[0_4px_20px_rgb(0,0,0,0.03)] dark:shadow-[0_18px_40px_rgba(2,6,23,0.28)] border border-slate-50 dark:border-slate-800 overflow-hidden transition-colors">
          
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
          <button @click="toggleTheme" class="w-full h-14 flex items-center justify-between px-4 active:bg-slate-50 transition-colors">
            <div class="flex items-center">
              <span class="text-xl mr-3 opacity-50">🌙</span>
              <span class="text-[15px] font-bold text-slate-700">深色模式</span>
            </div>
            <div class="w-12 h-6 rounded-full p-1 transition-colors duration-300 ease-in-out relative flex items-center" :class="darkMode ? 'bg-emerald-400' : 'bg-slate-200 dark:bg-slate-700'">
              <div class="w-4 h-4 rounded-full bg-white dark:bg-slate-950 shadow-sm transform transition-transform duration-300 ease-in-out" :class="darkMode ? 'translate-x-6' : 'translate-x-0'"></div>
            </div>
          </button>
          
        </div>
      </div>

      <!-- Section: 数据维护 -->
      <div v-motion-slide-visible-once-bottom :delay="150">
        <h3 class="text-xs font-bold text-slate-400 pl-4 mb-2 tracking-widest uppercase">数据维护与系统</h3>
        <div class="settings-card bg-white dark:bg-slate-900 rounded-3xl p-2 shadow-[0_4px_20px_rgb(0,0,0,0.03)] dark:shadow-[0_18px_40px_rgba(2,6,23,0.28)] border border-slate-50 dark:border-slate-800 overflow-hidden transition-colors">
          
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
          <div v-if="showDialectSheet" class="settings-sheet relative z-10 w-full sm:w-[400px] bg-white dark:bg-slate-900 rounded-t-[2rem] sm:rounded-3xl pt-6 pb-8 px-6 shadow-2xl dark:shadow-[0_24px_60px_rgba(2,6,23,0.5)] flex flex-col max-h-[85vh] transition-colors">
            <div class="w-12 h-1.5 bg-slate-200 rounded-full mx-auto mb-6 shrink-0"></div>
            <h3 class="text-xl font-bold text-slate-800 text-center mb-6">选择识别方言</h3>
            <div class="flex-1 overflow-y-auto custom-scrollbar space-y-2">
              <button 
                v-for="opt in dialectOptions" 
                :key="opt.value"
                @click="settingsStore.selectedDialect = opt.value; showDialectSheet = false"
                class="w-full flex items-center justify-between p-4 rounded-2xl border-2 transition-all active:scale-[0.98]"
                :class="settingsStore.selectedDialect === opt.value ? 'border-green-500 bg-green-50/50 dark:border-emerald-400 dark:bg-emerald-500/10' : 'border-transparent bg-slate-50 dark:bg-slate-800 hover:bg-slate-100 dark:hover:bg-slate-700/70'"
              >
                <span class="font-bold text-[15px]" :class="settingsStore.selectedDialect === opt.value ? 'text-green-600 dark:text-emerald-300' : 'text-slate-700 dark:text-slate-200'">{{ opt.label }}</span>
                <div 
                  class="w-6 h-6 rounded-full border-2 flex items-center justify-center transition-colors"
                  :class="settingsStore.selectedDialect === opt.value ? 'border-green-500 bg-green-500 text-white dark:border-emerald-400 dark:bg-emerald-400 dark:text-slate-950' : 'border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-900 text-transparent'"
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
          <div v-if="showLanguageSheet" class="settings-sheet relative z-10 w-full sm:w-[400px] bg-white dark:bg-slate-900 rounded-t-[2rem] sm:rounded-3xl pt-6 pb-8 px-6 shadow-2xl dark:shadow-[0_24px_60px_rgba(2,6,23,0.5)] flex flex-col max-h-[85vh] transition-colors">
            <div class="w-12 h-1.5 bg-slate-200 rounded-full mx-auto mb-6 shrink-0"></div>
            <h3 class="text-xl font-bold text-slate-800 text-center mb-6">切换界面语言</h3>
            <div class="flex-1 overflow-y-auto custom-scrollbar space-y-2">
              <button 
                v-for="opt in languageOptions" 
                :key="opt.value"
                @click="selectedLanguage = opt.value; showLanguageSheet = false"
                class="w-full flex items-center justify-between p-4 rounded-2xl border-2 transition-all active:scale-[0.98]"
                :class="selectedLanguage === opt.value ? 'border-green-500 bg-green-50/50 dark:border-emerald-400 dark:bg-emerald-500/10' : 'border-transparent bg-slate-50 dark:bg-slate-800 hover:bg-slate-100 dark:hover:bg-slate-700/70'"
              >
                <span class="font-bold text-[15px]" :class="selectedLanguage === opt.value ? 'text-green-600 dark:text-emerald-300' : 'text-slate-700 dark:text-slate-200'">{{ opt.label }}</span>
                <div 
                  class="w-6 h-6 rounded-full border-2 flex items-center justify-center transition-colors"
                  :class="selectedLanguage === opt.value ? 'border-green-500 bg-green-500 text-white dark:border-emerald-400 dark:bg-emerald-400 dark:text-slate-950' : 'border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-900 text-transparent'"
                >
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-4 h-4"><path fill-rule="evenodd" d="M16.704 4.153a.75.75 0 01.143 1.052l-8 10.5a.75.75 0 01-1.127.075l-4.5-4.5a.75.75 0 011.06-1.06l3.894 3.893 7.48-9.817a.75.75 0 011.05-.143z" clip-rule="evenodd" /></svg>
                </div>
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>

    <Transition
      enter-active-class="transition duration-300 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-200 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div v-if="showPasswordSheet" class="fixed inset-0 z-[110] flex items-end justify-center sm:items-center">
        <div class="absolute inset-0 bg-slate-900/40 backdrop-blur-sm" @click="showPasswordSheet = false"></div>

        <Transition
          enter-active-class="transition duration-300 ease-out"
          enter-from-class="translate-y-full sm:translate-y-4 sm:opacity-0"
          enter-to-class="translate-y-0 sm:translate-y-0 sm:opacity-100"
          leave-active-class="transition duration-200 ease-in"
          leave-from-class="translate-y-0 sm:translate-y-0 sm:opacity-100"
          leave-to-class="translate-y-full sm:translate-y-4 sm:opacity-0"
        >
          <div v-if="showPasswordSheet" class="settings-sheet relative z-10 w-full sm:w-[420px] rounded-t-[2rem] bg-white px-6 pt-6 pb-8 shadow-2xl sm:rounded-3xl dark:bg-slate-900">
            <div class="mb-6 h-1.5 w-12 rounded-full bg-slate-200 mx-auto"></div>
            <h3 class="text-center text-xl font-bold text-slate-800">修改密码</h3>
            <p class="mt-2 text-center text-sm text-slate-500 dark:text-slate-400">修改成功后将保持当前登录状态。</p>

            <div v-if="passwordError" class="mt-5 rounded-2xl border border-rose-100 bg-rose-50 px-4 py-3 text-sm font-bold text-rose-500 dark:border-rose-500/20 dark:bg-rose-500/10 dark:text-rose-300">
              {{ passwordError }}
            </div>

            <div class="mt-5 space-y-4">
              <input
                v-model="passwordForm.currentPassword"
                type="password"
                placeholder="当前密码"
                class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-sm font-medium text-slate-800 outline-none transition focus:border-emerald-400 focus:bg-white dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100"
              />
              <input
                v-model="passwordForm.newPassword"
                type="password"
                placeholder="新密码（至少 6 位）"
                class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-sm font-medium text-slate-800 outline-none transition focus:border-emerald-400 focus:bg-white dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100"
              />
              <input
                v-model="passwordForm.confirmPassword"
                type="password"
                placeholder="确认新密码"
                class="w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-sm font-medium text-slate-800 outline-none transition focus:border-emerald-400 focus:bg-white dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100"
              />
            </div>

            <div class="mt-6 grid grid-cols-2 gap-3">
              <button
                class="rounded-2xl border border-slate-200 px-4 py-3 text-sm font-bold text-slate-600 transition hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
                @click="showPasswordSheet = false"
              >
                取消
              </button>
              <button
                class="rounded-2xl bg-emerald-500 px-4 py-3 text-sm font-bold text-white transition hover:bg-emerald-600 disabled:cursor-not-allowed disabled:bg-emerald-300"
                :disabled="passwordSubmitting"
                @click="submitPasswordChange"
              >
                {{ passwordSubmitting ? '提交中...' : '确认修改' }}
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>

    <Transition
      enter-active-class="transition duration-300 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-200 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div v-if="showDeleteSheet" class="fixed inset-0 z-[110] flex items-end justify-center sm:items-center">
        <div class="absolute inset-0 bg-slate-900/50 backdrop-blur-sm" @click="showDeleteSheet = false"></div>

        <Transition
          enter-active-class="transition duration-300 ease-out"
          enter-from-class="translate-y-full sm:translate-y-4 sm:opacity-0"
          enter-to-class="translate-y-0 sm:translate-y-0 sm:opacity-100"
          leave-active-class="transition duration-200 ease-in"
          leave-from-class="translate-y-0 sm:translate-y-0 sm:opacity-100"
          leave-to-class="translate-y-full sm:translate-y-4 sm:opacity-0"
        >
          <div v-if="showDeleteSheet" class="settings-sheet relative z-10 w-full sm:w-[420px] rounded-t-[2rem] bg-white px-6 pt-6 pb-8 shadow-2xl sm:rounded-3xl dark:bg-slate-900">
            <div class="mb-6 h-1.5 w-12 rounded-full bg-slate-200 mx-auto"></div>
            <h3 class="text-center text-xl font-bold text-slate-800">注销账号</h3>
            <p class="mt-2 text-sm leading-6 text-slate-500 dark:text-slate-400">
              注销后将删除云端农场、识别记录、知识收藏等账号数据，且无法恢复。
            </p>

            <div class="mt-4 rounded-2xl border border-rose-100 bg-rose-50 px-4 py-3 text-sm text-rose-500 dark:border-rose-500/20 dark:bg-rose-500/10 dark:text-rose-300">
              请输入当前密码确认这是一项主动操作。
            </div>

            <div v-if="deleteError" class="mt-4 rounded-2xl border border-rose-100 bg-rose-50 px-4 py-3 text-sm font-bold text-rose-500 dark:border-rose-500/20 dark:bg-rose-500/10 dark:text-rose-300">
              {{ deleteError }}
            </div>

            <input
              v-model="deleteForm.currentPassword"
              type="password"
              placeholder="当前密码"
              class="mt-5 w-full rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-sm font-medium text-slate-800 outline-none transition focus:border-rose-400 focus:bg-white dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100"
            />

            <div class="mt-6 grid grid-cols-2 gap-3">
              <button
                class="rounded-2xl border border-slate-200 px-4 py-3 text-sm font-bold text-slate-600 transition hover:bg-slate-50 dark:border-slate-700 dark:text-slate-300 dark:hover:bg-slate-800"
                @click="showDeleteSheet = false"
              >
                取消
              </button>
              <button
                class="rounded-2xl bg-rose-500 px-4 py-3 text-sm font-bold text-white transition hover:bg-rose-600 disabled:cursor-not-allowed disabled:bg-rose-300"
                :disabled="deleteSubmitting"
                @click="submitDeleteAccount"
              >
                {{ deleteSubmitting ? '注销中...' : '确认注销' }}
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

:global(.dark .settings-page) {
  color: #e2e8f0;
}

:global(.dark .settings-header h2),
:global(.dark .settings-sheet h3) {
  color: #f8fafc;
}

:global(.dark .settings-page h3.text-slate-400) {
  color: #64748b;
}

:global(.dark .settings-card button) {
  color: #e2e8f0;
}

:global(.dark .settings-card button:active) {
  background: rgba(30, 41, 59, 0.65);
}

:global(.dark .settings-card .text-slate-700) {
  color: #e2e8f0;
}

:global(.dark .settings-card .text-slate-500),
:global(.dark .settings-card .text-slate-400) {
  color: #94a3b8;
}

:global(.dark .settings-card .text-slate-300) {
  color: #64748b;
}

:global(.dark .settings-card .bg-slate-100),
:global(.dark .settings-sheet .bg-slate-200) {
  background: #334155;
}

:global(.dark .settings-card .h-px.bg-slate-100) {
  background: #1e293b;
}

:global(.dark .settings-card svg.text-slate-300) {
  color: #64748b;
}

:global(.dark .settings-sheet) {
  border: 1px solid #1e293b;
  color: #e2e8f0;
}
</style>
