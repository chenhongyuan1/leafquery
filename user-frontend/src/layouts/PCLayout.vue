<script setup>
import { ref, computed, onMounted, onUnmounted, markRaw, nextTick } from 'vue'
import { userApi } from '../api/index.js'
import { useFavoritesStore } from '../stores/favoritesStore'
import { useFarmStore } from '../stores/farmStore'
import {
  AUTH_CHANGE_EVENT,
  clearUserSession,
  getStoredUser,
  persistUser
} from '../utils/accountSecurity'
import {
  applyThemePreference,
  syncThemePreference
} from '../utils/themePreference'
import {
  fetchAnnouncements,
  getLocalDismissedPopupIds,
  dismissPopupAnnouncement
} from '../utils/announcementReadState'

// 导入电脑端页面组件
import PCIntro from '../views/pc/PCIntro.vue'
import WorkspaceView from '../views/pc/WorkspaceView.vue'
import DataCenterView from '../views/pc/DataCenterView.vue'
import KnowledgeView from '../views/pc/KnowledgeView.vue'
import SettingsView from '../views/pc/SettingsView.vue'

// 导入通用组件
import ThemeToggle from '../components/common/ThemeToggle.vue'
import PCNotifications from '../components/pc/PCNotifications.vue'

const favStore = useFavoritesStore()
const farmStore = useFarmStore()

// ============ 主题 ============
const isDark = ref(false)
const isThemeTransitioning = ref(false)
const handleThemeSync = () => {
  isDark.value = document.documentElement.classList.contains('dark')
}

const applyTheme = (nextDark) => {
  isDark.value = applyThemePreference(nextDark ? 'dark' : 'light') === 'dark'
}

const toggleTheme = (origin) => {
  if (isThemeTransitioning.value) return

  const nextDark = !isDark.value
  const startViewTransition = document.startViewTransition?.bind(document)
  const prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches

  if (!startViewTransition || !origin || prefersReducedMotion) {
    applyTheme(nextDark)
    return
  }

  const endRadius = Math.hypot(
    Math.max(origin.x, window.innerWidth - origin.x),
    Math.max(origin.y, window.innerHeight - origin.y)
  )

  isThemeTransitioning.value = true

  const transition = startViewTransition(async () => {
    applyTheme(nextDark)
    await nextTick()
  })

  transition.ready.then(() => {
    const clipPath = [
      `circle(0px at ${origin.x}px ${origin.y}px)`,
      `circle(${endRadius}px at ${origin.x}px ${origin.y}px)`
    ]

    document.documentElement.animate(
      {
        clipPath: nextDark ? clipPath : [...clipPath].reverse()
      },
      {
        duration: 560,
        easing: 'cubic-bezier(0.22, 1, 0.36, 1)',
        fill: 'both',
        pseudoElement: `::view-transition-${nextDark ? 'new' : 'old'}(root)`
      }
    )
  }).catch(() => {
    if (isDark.value !== nextDark) {
      applyTheme(nextDark)
    }
  })

  transition.finished.finally(() => {
    isThemeTransitioning.value = false
  })
}

// ============ 登录与账号 ============
const currentUser = ref(null)
const showLoginModal = ref(false)
const isLoginMode = ref(true)
const isLoading = ref(false)
const errorMessage = ref('')
const loginForm = ref({ username: '', password: '', confirmPassword: '', phoneNumber: '' })
const handleAuthChange = () => {
  currentUser.value = getStoredUser()
  refreshPopupAnnouncement()
}

// ==== 弹出公告 ====
const popupAnnouncement = ref(null)

const refreshPopupAnnouncement = async () => {
  try {
    const popupAnnouncements = await fetchAnnouncements({ popup: true })
    if (currentUser.value?.userId) {
      popupAnnouncement.value = popupAnnouncements.find(item => !Boolean(item.read)) || null
    } else {
      const dismissedIds = getLocalDismissedPopupIds()
      popupAnnouncement.value = popupAnnouncements.find(item => !dismissedIds.includes(Number(item.id))) || null
    }
  } catch (error) {
    console.error('Failed to refresh popup announcement state', error)
    popupAnnouncement.value = null
  }
}

async function dismissPopup() {
  if (!popupAnnouncement.value) return
  try {
    await dismissPopupAnnouncement(popupAnnouncement.value.id)
  } catch (error) {
    console.error('Failed to dismiss popup announcement', error)
  } finally {
    popupAnnouncement.value = null
  }
}

onMounted(() => {
  isDark.value = syncThemePreference('pc-theme') === 'dark'
  window.addEventListener('storage', handleThemeSync)
  window.addEventListener(AUTH_CHANGE_EVENT, handleAuthChange)
  currentUser.value = getStoredUser()
  if (currentUser.value?.userId) {
    favStore.loadFavorites(currentUser.value.userId)
  }
  farmStore.initialize()
  refreshPopupAnnouncement()
})

onUnmounted(() => {
  window.removeEventListener('storage', handleThemeSync)
  window.removeEventListener(AUTH_CHANGE_EVENT, handleAuthChange)
})

const handleAuthSubmit = async () => {
  if (isLoginMode.value) {
    if (!loginForm.value.username || !loginForm.value.password) { errorMessage.value = '账号和密码不能为空'; return }
  } else {
    if (!loginForm.value.username || !loginForm.value.password || !loginForm.value.confirmPassword || !loginForm.value.phoneNumber) { errorMessage.value = '请填写完整'; return }
    if (loginForm.value.password !== loginForm.value.confirmPassword) { errorMessage.value = '两次密码不一致'; return }
  }
  isLoading.value = true
  errorMessage.value = ''
  try {
    if (isLoginMode.value) {
      const res = await userApi.login({ username: loginForm.value.username, password: loginForm.value.password })
      if (res.data.code === 200) {
        currentUser.value = res.data.data
        persistUser(res.data.data)
        if (res.data.data?.userId) {
          favStore.loadFavorites(res.data.data.userId)
        }
        await farmStore.initialize({ force: true })
        showLoginModal.value = false
        loginForm.value = { username: '', password: '', confirmPassword: '', phoneNumber: '' }
      }
    } else {
      const res = await userApi.register({
        username: loginForm.value.username, password: loginForm.value.password,
        phoneNumber: loginForm.value.phoneNumber,
        avatarUrl: 'https://api.dicebear.com/7.x/avataaars/svg?seed=' + loginForm.value.username
      })
      if (res.data.code === 200) { isLoginMode.value = true; errorMessage.value = '注册成功，请登录！' }
    }
  } catch (err) {
    errorMessage.value = err.response?.data?.message || (isLoginMode.value ? '登录失败' : '注册失败')
  } finally { isLoading.value = false }
}

const handleLogout = async () => {
  currentUser.value = null
  await clearUserSession({
    farmStore,
    favoritesStore: favStore
  })
}

// ============ 电脑端导航页签 ============
const activeTab = ref('intro')

const enterWorkspace = () => {
  activeTab.value = 'workspace'
}

const goToIntro = () => {
  activeTab.value = 'intro'
}

const navItems = [
  { id: 'workspace', label: '工作台', icon: '💻', component: markRaw(WorkspaceView) },
  { id: 'datacenter', label: '数据中心', icon: '📊', component: markRaw(DataCenterView) },
  { id: 'knowledge', label: '知识图谱', icon: '🕸️', component: markRaw(KnowledgeView) },
  { id: 'settings', label: '系统设置', icon: '⚙️', component: markRaw(SettingsView) }
]

const isIntroScreen = computed(() => activeTab.value === 'intro')

const currentComponent = computed(() => {
  const tab = navItems.find(t => t.id === activeTab.value)
  return tab ? tab.component : WorkspaceView
})

</script>

<template>
  <div class="flex h-screen w-full overflow-hidden"
       :class="isDark ? 'bg-slate-950 text-white' : 'bg-slate-50 text-slate-900'">
    <template v-if="isIntroScreen">
      <div class="relative h-full w-full overflow-auto">
        <div class="absolute right-6 top-6 z-20 md:right-8 md:top-8">
          <ThemeToggle :isDark="isDark" :disabled="isThemeTransitioning" @toggle="toggleTheme" />
        </div>
        <PCIntro :isDark="isDark" @enter-workspace="enterWorkspace" />
      </div>
    </template>

    <template v-else>
    
    <!-- 左侧边栏 -->
    <aside class="w-64 flex flex-col shrink-0 border-r"
           :class="isDark ? 'bg-slate-900 border-slate-800' : 'bg-white border-slate-200 shadow-sm'">
      
      <!-- 品牌标识 -->
      <div class="h-16 flex items-center px-6 border-b" :class="isDark ? 'border-slate-800' : 'border-slate-100'">
        <div class="flex items-center space-x-2.5 group cursor-pointer" @click="goToIntro">
          <span class="text-2xl group-hover:rotate-12 transition-transform duration-300">🌱</span>
          <span class="text-xl font-black tracking-tight">
            <span class="text-green-500">Leaf</span><span :class="isDark ? 'text-white' : 'text-slate-800'">Query</span>
          </span>
        </div>
      </div>

      <!-- 导航区 -->
      <nav class="flex-1 px-4 py-6 space-y-2">
        <button v-for="item in navItems" :key="item.id"
                @click="activeTab = item.id"
                class="w-full flex items-center space-x-3 px-4 py-3 rounded-xl font-bold transition-all text-left"
                :class="activeTab === item.id
                  ? (isDark ? 'bg-green-500/10 text-green-400' : 'bg-green-50 text-green-600')
                  : (isDark ? 'text-slate-400 hover:bg-slate-800 hover:text-white' : 'text-slate-500 hover:bg-slate-100/50 hover:text-slate-900')">
           <span class="text-xl">{{ item.icon }}</span>
           <span>{{ item.label }}</span>
        </button>
      </nav>

      <!-- 底部用户区 -->
      <div class="p-4 border-t" :class="isDark ? 'border-slate-800' : 'border-slate-100'">
         <button v-if="!currentUser" @click="showLoginModal = true"
                 class="w-full bg-green-500 hover:bg-green-600 text-white font-bold py-3 rounded-xl shadow-lg shadow-green-500/20 active:scale-95 transition-all">
            点击登录系统
         </button>
         <div v-else class="flex items-center justify-between p-2 rounded-xl" :class="isDark ? 'bg-slate-800' : 'bg-slate-50'">
            <div class="flex items-center space-x-3 truncate">
               <div class="w-8 h-8 rounded-full bg-green-500 flex items-center justify-center text-white font-bold overflow-hidden shrink-0">
                  <img v-if="currentUser.avatarUrl" :src="currentUser.avatarUrl" class="w-full h-full object-cover" />
                  <span v-else>{{ currentUser.username?.[0]?.toUpperCase() }}</span>
               </div>
               <span class="font-bold text-sm truncate" :class="isDark ? 'text-slate-200' : 'text-slate-700'">{{ currentUser.username }}</span>
            </div>
            <button @click="handleLogout" class="p-2 text-slate-400 hover:text-red-500 transition-colors" title="退出">
               <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 9V5.25A2.25 2.25 0 0 0 13.5 3h-6a2.25 2.25 0 0 0-2.25 2.25v13.5A2.25 2.25 0 0 0 7.5 21h6a2.25 2.25 0 0 0 2.25-2.25V15m3 0 3-3m0 0-3-3m3 3H9" /></svg>
            </button>
         </div>
      </div>
    </aside>

    <!-- 右侧主内容区 -->
    <main class="flex-1 flex flex-col overflow-hidden relative">
      <!-- 顶部栏 -->
      <header class="h-16 flex items-center justify-end border-b px-8 shrink-0"
              :class="isDark ? 'bg-slate-900/50 border-slate-800' : 'bg-slate-50 border-slate-200'">
         <div class="flex items-center space-x-4">
            <PCNotifications :isDark="isDark" />
            <!-- 主题切换 -->
             <ThemeToggle :isDark="isDark" :disabled="isThemeTransitioning" @toggle="toggleTheme" />
         </div>
      </header>
      
      <!-- 页面内容区 -->
      <div class="flex-1 overflow-y-auto w-full relative">
        <Transition
          enter-active-class="transition ease-out duration-300"
          enter-from-class="opacity-0 translate-y-4"
          enter-to-class="opacity-100 translate-y-0"
          leave-active-class="transition ease-in duration-200"
          leave-from-class="opacity-100"
          leave-to-class="opacity-0"
          mode="out-in"
        >
           <component :is="currentComponent" />
        </Transition>
      </div>
    </main>
    </template>

    <!-- ============ 登录弹窗 ============ -->
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div v-if="showLoginModal" class="fixed inset-0 z-[100] flex items-center justify-center">
        <div class="absolute inset-0 bg-black/50 backdrop-blur-sm" @click="showLoginModal = false"></div>
        <div class="relative z-10 w-full max-w-md mx-4 rounded-3xl overflow-hidden shadow-2xl"
             :class="isDark ? 'bg-slate-900' : 'bg-white'">

          <!-- 头部 -->
          <div class="bg-gradient-to-br from-green-500 to-emerald-600 px-8 py-8 text-center text-white">
            <div class="w-14 h-14 bg-white/20 backdrop-blur-md rounded-2xl mx-auto flex items-center justify-center text-3xl mb-4">🌱</div>
            <h2 class="text-2xl font-extrabold">LeafQuery Workspace</h2>
            <p class="text-green-100 text-sm mt-1">{{ isLoginMode ? '欢迎回来，登录您的工作台账号' : '创建工作台账号' }}</p>
          </div>

          <!-- 表单 -->
          <div class="p-8">
            <form @submit.prevent="handleAuthSubmit" class="space-y-4">
              <div
                v-if="errorMessage"
                class="rounded-xl border p-3 text-center text-sm font-bold"
                :class="isDark ? 'border-red-500/20 bg-red-500/10 text-red-200' : 'border-red-100 bg-red-50 text-red-500'"
              >
                {{ errorMessage }}
              </div>

              <input v-model="loginForm.username" type="text" placeholder="用户名 / 手机号" required
                class="w-full rounded-xl px-5 py-3.5 text-sm font-medium outline-none transition-all"
                :class="isDark ? 'bg-slate-800 text-white border border-slate-700 focus:border-green-500' : 'bg-slate-50 border border-slate-200 text-slate-800 focus:ring-2 focus:ring-green-400'" />

              <input v-model="loginForm.password" type="password" placeholder="密码" required
                class="w-full rounded-xl px-5 py-3.5 text-sm font-medium outline-none transition-all"
                :class="isDark ? 'bg-slate-800 text-white border border-slate-700 focus:border-green-500' : 'bg-slate-50 border border-slate-200 text-slate-800 focus:ring-2 focus:ring-green-400'" />

              <template v-if="!isLoginMode">
                <input v-model="loginForm.confirmPassword" type="password" placeholder="确认密码" required
                  class="w-full rounded-xl px-5 py-3.5 text-sm font-medium outline-none transition-all"
                  :class="isDark ? 'bg-slate-800 text-white border border-slate-700 focus:border-green-500' : 'bg-slate-50 border border-slate-200 text-slate-800 focus:ring-2 focus:ring-green-400'" />
                <input v-model="loginForm.phoneNumber" type="tel" placeholder="手机号码" required
                  class="w-full rounded-xl px-5 py-3.5 text-sm font-medium outline-none transition-all"
                  :class="isDark ? 'bg-slate-800 text-white border border-slate-700 focus:border-green-500' : 'bg-slate-50 border border-slate-200 text-slate-800 focus:ring-2 focus:ring-green-400'" />
              </template>

              <button type="submit" :disabled="isLoading"
                class="w-full bg-green-500 hover:bg-green-600 disabled:bg-green-300 text-white py-3.5 rounded-xl font-extrabold text-base shadow-lg shadow-green-500/25 transition-all flex items-center justify-center space-x-2">
                <svg v-if="isLoading" class="animate-spin h-5 w-5" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"/><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"/></svg>
                <span>{{ isLoginMode ? '立即登录' : '提交注册' }}</span>
              </button>
            </form>
            <div class="mt-4 text-center">
              <button @click="isLoginMode = !isLoginMode; errorMessage = ''" class="text-sm font-medium transition-colors"
                :class="isDark ? 'text-slate-400 hover:text-green-400' : 'text-slate-500 hover:text-green-600'">
                {{ isLoginMode ? '还没有账号？点击注册' : '已有账号？返回登录' }}
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <!-- ============ 弹出公告窗 ============ -->
    <Transition
      enter-active-class="transition duration-300 ease-out"
      enter-from-class="opacity-0 scale-95"
      enter-to-class="opacity-100 scale-100"
      leave-active-class="transition duration-200 ease-in"
      leave-from-class="opacity-100 scale-100"
      leave-to-class="opacity-0 scale-95"
    >
      <div v-if="popupAnnouncement" class="fixed inset-0 z-[200] flex items-center justify-center px-4">
        <div class="absolute inset-0 bg-black/60 backdrop-blur-sm" @click="dismissPopup"></div>
        <div class="relative w-full max-w-md bg-white dark:bg-slate-900 rounded-[2rem] overflow-hidden shadow-2xl flex flex-col z-10 border dark:border-slate-800 border-slate-100">
          <!-- 顶部横幅 -->
          <div class="bg-gradient-to-br from-indigo-500 to-purple-600 px-6 py-8 flex items-center justify-between z-0 relative overflow-hidden">
            <div class="absolute inset-0 opacity-20 bg-[url('data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIyMCIgaGVpZ2h0PSIyMCI+PGNpcmNsZSBjeD0iMiIgY3k9IjIiIHI9IjIiIGZpbGw9IiNmZmYiLz48L3N2Zz4=')]"></div>
            <div class="relative z-10 pr-2">
              <span class="inline-block px-2 py-1 bg-white/20 text-white text-[10px] uppercase font-bold rounded-lg mb-2 backdrop-blur-md border border-white/20">系统公告</span>
              <h2 class="text-2xl font-bold text-white leading-tight drop-shadow-md break-words">{{ popupAnnouncement.title }}</h2>
            </div>
            <button @click="dismissPopup" class="relative z-10 w-8 h-8 shrink-0 bg-white/10 hover:bg-white/20 backdrop-blur-sm rounded-full flex items-center justify-center text-white font-bold active:scale-90 transition-all border border-white/20">✕</button>
          </div>
          <!-- 内容区 -->
          <div class="p-6 max-h-[40vh] overflow-y-auto">
            <p class="text-slate-600 dark:text-slate-300 text-sm leading-relaxed whitespace-pre-wrap">{{ popupAnnouncement.content }}</p>
          </div>
          <!-- 操作区 -->
          <div class="px-6 py-4 border-t dark:border-slate-800 border-slate-100 bg-slate-50 dark:bg-slate-900/50">
            <button @click="dismissPopup" class="w-full bg-slate-900 dark:bg-green-500 hover:bg-slate-800 dark:hover:bg-green-600 text-white py-3.5 rounded-2xl font-bold text-sm shadow-lg shadow-slate-900/20 active:scale-[0.98] transition-all">我知道了</button>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
:global(::view-transition-old(root)),
:global(::view-transition-new(root)) {
  animation: none;
  mix-blend-mode: normal;
}

:global(::view-transition-group(theme-toggle-knob)) {
  z-index: 40;
  animation-duration: 320ms;
  animation-timing-function: cubic-bezier(0.22, 1, 0.36, 1);
}

:global(::view-transition-old(theme-toggle-knob)),
:global(::view-transition-new(theme-toggle-knob)) {
  mix-blend-mode: normal;
}

:global(::view-transition-old(root)) {
  z-index: 20;
}

:global(::view-transition-new(root)) {
  z-index: 10;
}

:global(.dark::view-transition-old(root)) {
  z-index: 10;
}

:global(.dark::view-transition-new(root)) {
  z-index: 20;
}

/* 更顺滑的滚动条 */
::-webkit-scrollbar { width: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }
::-webkit-scrollbar-thumb:hover { background: #94a3b8; }
:global(.dark ::-webkit-scrollbar-thumb) { background: #334155; }
:global(.dark ::-webkit-scrollbar-thumb:hover) { background: #475569; }
</style>
