<script setup>
import { ref, reactive, onMounted, computed, watch, onUnmounted } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'
import { useFarmStore } from '../../stores/farmCloud'

const router = useRouter()
const farmStore = useFarmStore()

const menuItems = [
  { label: '我的农场', icon: '🏡', route: '/farm' },
  { label: '我的收藏', icon: '⭐', route: '/favorites' },
  { label: '病害诊断记录', icon: '📝', route: '/records' },
  { label: '系统设置', icon: '⚙️', route: '/settings' },
  { label: '关于我们', icon: 'ℹ️', route: '/about' },
]

const handleMenuClick = (item) => {
  if (item.route) {
    router.push(item.route)
  } else {
    // Other items placeholder
    console.log('Clicked', item.label)
  }
}


// 用户状态
const currentUser = ref(null)

// 认证遮罩层状态
const showAuthOverlay = ref(false)
const isLoginMode = ref(true)
const isLoading = ref(false)
const errorMessage = ref('')

// Scroll locking using Javascript to avoid layout thrashing during animation
watch(showAuthOverlay, (val) => {
  const container = document.querySelector('.overflow-y-auto.scrollbar-hide')
  if (container) {
    container.style.overflowY = val ? 'hidden' : 'auto'
  }
})

onUnmounted(() => {
  const container = document.querySelector('.overflow-y-auto.scrollbar-hide')
  if (container) {
    container.style.overflowY = 'auto'
  }
})

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  phoneNumber: '',
  idCard: '',
  avatarUrl: ''
})

const recordCount = ref(0)
const diseaseCount = ref(0)
const coveredAreaCount = computed(() => {
  return new Set(farmStore.crops.map(c => c.region)).size
})

const fetchStats = async () => {
  if (currentUser.value && currentUser.value.userId) {
    try {
      const res = await axios.get(`/api/record/list?userId=${currentUser.value.userId}`)
      if (res.data && res.data.code === 200) {
        const history = res.data.data
        recordCount.value = history.length
        const pests = history
          .filter(h => h.pestName && !h.pestName.includes('健康') && !h.pestName.includes('Healthy') && !h.pestName.includes('未发现'))
          .map(h => h.pestName)
        diseaseCount.value = new Set(pests).size
      }
    } catch (e) {
      console.error('获取统计数据失败', e)
    }
  }
}

onMounted(async () => {
  // 从本地存储读取用户信息
  const savedUser = localStorage.getItem('user')
  if (savedUser) {
    currentUser.value = JSON.parse(savedUser)
    fetchStats()
  }
  await farmStore.initialize()
})

// 默认显示头像
const displayAvatar = computed(() => {
  if (currentUser.value && currentUser.value.avatarUrl) {
    return currentUser.value.avatarUrl
  }
  return null
})

const userRoleName = computed(() => {
  if (!currentUser.value) return ''
  const r = currentUser.value.role
  if (r === 'expert') return '专家'
  if (r === 'admin' || r === 'super_admin') return '管理员'
  return '普通用户'
})

const openAuth = () => {
  showAuthOverlay.value = true
  isLoginMode.value = true
  errorMessage.value = ''
}

const closeAuth = () => {
  showAuthOverlay.value = false
}

const toggleMode = () => {
  isLoginMode.value = !isLoginMode.value
  errorMessage.value = ''
  form.password = ''
  form.confirmPassword = ''
}

const handleSubmit = async () => {
  if (isLoginMode.value) {
    if (!form.username || !form.password) {
      errorMessage.value = '账号和密码不能为空'
      return
    }
  } else {
    if (!form.username || !form.password || !form.confirmPassword || !form.phoneNumber) {
      errorMessage.value = '请填写完整的必填注册信息'
      return
    }
    if (form.password !== form.confirmPassword) {
      errorMessage.value = '两次输入的密码不一致'
      return
    }
  }

  isLoading.value = true
  errorMessage.value = ''

  try {
    if (isLoginMode.value) {
      // 登录请求
      const res = await axios.post('/api/user/login', {
        username: form.username,
        password: form.password
      })
      if (res.data.code === 200) {
        currentUser.value = res.data.data
        localStorage.setItem('user', JSON.stringify(res.data.data))
        await farmStore.initialize({ force: true })
        fetchStats()
        closeAuth()
      }
    } else {
      // 注册请求
      const res = await axios.post('/api/user/register', {
        username: form.username,
        password: form.password,
        phoneNumber: form.phoneNumber,
        idCard: form.idCard || null,
        avatarUrl: form.avatarUrl || 'https://api.dicebear.com/7.x/avataaars/svg?seed=' + form.username
      })
      if (res.data.code === 200) {
        isLoginMode.value = true
        errorMessage.value = '注册成功，请使用新账号登录！'
      }
    }
  } catch (err) {
    if (err.response && err.response.data && err.response.data.message) {
      errorMessage.value = err.response.data.message
    } else {
      errorMessage.value = isLoginMode.value ? '登录失败，请检查账号或密码' : '注册失败，请稍后重试'
    }
  } finally {
    isLoading.value = false
  }
}

const handleLogout = () => {
  currentUser.value = null
  localStorage.removeItem('user')
  farmStore.resetToLocal()
  recordCount.value = 0
  diseaseCount.value = 0
}
</script>

<template>
  <div class="profile-page min-h-full lg:min-h-screen pb-8 lg:pb-12 bg-slate-50 dark:bg-slate-950 relative overflow-hidden lg:flex lg:flex-col lg:items-center transition-colors">
    
    <div class="w-full lg:max-w-6xl lg:grid lg:grid-cols-12 lg:gap-8 lg:px-8 lg:mt-12">
      <!-- Immersive Header (Mobile) / Profile Card (PC) -->
      <div class="profile-hero-shell lg:col-span-4 lg:bg-white dark:lg:bg-slate-900 lg:rounded-3xl lg:shadow-xl dark:lg:shadow-[0_18px_50px_rgba(2,6,23,0.4)] lg:overflow-hidden lg:h-max lg:border lg:border-slate-100 dark:lg:border-slate-800 relative group transition-colors">
        <div 
          class="relative transition-all duration-700 ease-[cubic-bezier(0.25,1,0.5,1)] origin-top overflow-hidden w-full lg:rounded-none"
          :class="showAuthOverlay ? 'h-[100vh] rounded-none z-50 lg:h-[600px] lg:rounded-2xl' : 'h-80 lg:h-auto lg:py-16 rounded-b-[3rem] shadow-2xl shadow-green-900/20 lg:shadow-none z-10'"
        >
          <!-- Background Gradients -->
          <div class="absolute inset-0 bg-gradient-to-br from-green-600 to-emerald-800 dark:from-slate-950 dark:via-emerald-950 dark:to-slate-900 transition-colors duration-700"></div>
          <div class="absolute -bottom-20 -right-20 w-80 h-80 bg-white/10 rounded-full blur-3xl transition-transform duration-700 group-hover:scale-110" :class="showAuthOverlay ? 'scale-150' : ''"></div>
          <div class="absolute top-10 left-10 w-40 h-40 bg-yellow-300/20 rounded-full blur-2xl transition-transform duration-700 group-hover:scale-110" :class="showAuthOverlay ? 'scale-[2]' : ''"></div>
      
      <!-- DEFAULT PROFILE HEADER CONTENT (Only visible when NOT in Auth Mode) -->
      <transition
        enter-active-class="transition duration-500 ease-out delay-200"
        enter-from-class="opacity-0 -translate-y-8"
        enter-to-class="opacity-100 translate-y-0"
        leave-active-class="transition duration-300 ease-in"
        leave-from-class="opacity-100 translate-y-0"
        leave-to-class="opacity-0 -translate-y-8 absolute inset-x-0"
      >
        <div v-show="!showAuthOverlay" class="profile-hero-content relative z-10 pt-16 px-8 text-center flex flex-col items-center justify-center">
           
           <div class="w-24 h-24 bg-white p-1 rounded-full shadow-lg mb-6 cursor-pointer transform hover:scale-105 transition-transform" @click="!currentUser && openAuth()">
             <div class="w-full h-full rounded-full bg-slate-200 dark:bg-slate-700 overflow-hidden relative border-2 border-white dark:border-slate-800">
                <template v-if="displayAvatar">
                  <img :src="displayAvatar" class="w-full h-full object-cover" />
                </template>
                <template v-else>
                  <div class="absolute inset-0 bg-gradient-to-tr from-green-400 to-blue-500"></div>
                  <div class="absolute inset-0 flex items-center justify-center text-4xl">👨‍🌾</div>
                </template>
             </div>
           </div>
           
           <template v-if="currentUser">
             <h2 class="text-2xl font-bold text-white mb-1">{{ currentUser.username }}</h2>
             <div class="inline-flex items-center px-3 py-1 bg-white/20 backdrop-blur-md rounded-full border border-white/20">
               <span class="w-2 h-2 bg-yellow-400 rounded-full mr-2"></span>
               <span class="text-xs font-medium text-white">{{ userRoleName }} ID: {{ currentUser.id || currentUser.userId }}</span>
             </div>
           </template>
           
           <template v-else>
             <button @click="openAuth" class="inline-flex items-center px-6 py-2.5 bg-white/20 border border-white/30 backdrop-blur-md text-white font-bold rounded-full shadow-lg hover:bg-white/30 hover:scale-105 active:scale-95 transition-all group">
               <span class="text-xl mr-3 font-extrabold tracking-wide">未登录</span>
               <span class="w-[1px] h-4 bg-white/50 mr-3"></span>
               <span class="text-sm font-medium">点击登录 / 注册</span>
               <span class="ml-2 group-hover:translate-x-1 transition-transform">👉</span>
             </button>
           </template>
        </div>
      </transition>

      <!-- AUTH FORM OVERLAY CONTENT (Only visible when Auth Mode IS active) -->
      <transition
        enter-active-class="transition duration-700 ease-[cubic-bezier(0.25,1,0.5,1)] delay-100"
        enter-from-class="opacity-0 translate-y-12"
        enter-to-class="opacity-100 translate-y-0"
        leave-active-class="transition duration-300 ease-in"
        leave-from-class="opacity-100 translate-y-0"
        leave-to-class="opacity-0 translate-y-12"
      >
        <div v-show="showAuthOverlay" class="profile-auth-overlay absolute inset-0 z-20 flex flex-col items-center justify-center px-6 overflow-y-auto pt-10 pb-32 custom-scrollbar">
          
          <!-- 关闭按钮 -->
          <button @click="closeAuth" class="absolute top-8 right-6 w-10 h-10 bg-white/20 backdrop-blur-md rounded-full text-white flex items-center justify-center hover:bg-white/30 transition-colors z-50">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>

          <!-- 顶部文字 -->
          <div class="text-center mb-8">
            <div class="w-16 h-16 bg-white text-green-500 rounded-2xl mx-auto flex items-center justify-center text-3xl shadow-lg shadow-black/10 mb-6">
              🌱
            </div>
            <h1 class="text-3xl font-extrabold text-white tracking-tight">LeafQuery</h1>
            <p class="text-green-100 font-medium mt-2">{{ isLoginMode ? '欢迎回来，登录您的账号' : '创建新账号，开启智能诊断' }}</p>
          </div>

          <!-- 表单主容器 -->
          <div class="profile-auth-card w-full max-w-md bg-white dark:bg-slate-900 rounded-[2rem] p-8 shadow-2xl shadow-green-900/50 dark:shadow-[0_24px_60px_rgba(2,6,23,0.5)] transition-colors">
            <form @submit.prevent="handleSubmit" class="space-y-4">
              
              <!-- 错误提示 -->
              <div v-if="errorMessage" class="p-3 bg-red-50 dark:bg-red-500/10 text-red-500 dark:text-red-300 text-sm rounded-xl text-center font-bold animate-pulse">
                {{ errorMessage }}
              </div>

              <!-- 账号 (邮箱/手机号/用户名) -->
              <div class="space-y-1.5">
                <label class="text-sm font-bold text-slate-700 ml-1">账号 <span v-if="!isLoginMode" class="text-red-500">*</span></label>
                <input 
                  v-model="form.username"
                  type="text" 
                  placeholder="用户名 / 手机号 / 邮箱" 
                  class="w-full bg-slate-50 border border-slate-200 text-slate-800 rounded-xl px-5 py-3.5 focus:outline-none focus:ring-2 focus:ring-green-400 focus:bg-white transition-all font-medium"
                  required
                />
              </div>

              <!-- 密码 -->
              <div class="space-y-1.5">
                <label class="text-sm font-bold text-slate-700 ml-1">密码 <span v-if="!isLoginMode" class="text-red-500">*</span></label>
                <input 
                  v-model="form.password"
                  type="password" 
                  placeholder="请输入密码" 
                  class="w-full bg-slate-50 border border-slate-200 text-slate-800 rounded-xl px-5 py-3.5 focus:outline-none focus:ring-2 focus:ring-green-400 focus:bg-white transition-all font-medium"
                  required
                />
              </div>

              <!-- 仅注册时显示的扩展表单区 -->
              <transition
                enter-active-class="transition-all duration-300 ease-out"
                enter-from-class="opacity-0 -translate-y-4 max-h-0"
                enter-to-class="opacity-100 translate-y-0 max-h-[500px]"
                leave-active-class="transition-all duration-200 ease-in"
                leave-from-class="opacity-100 translate-y-0 max-h-[500px]"
                leave-to-class="opacity-0 -translate-y-4 max-h-0"
              >
                <div v-if="!isLoginMode" class="space-y-4 overflow-hidden pt-1">
                  <!-- 确认密码 -->
                  <div class="space-y-1.5">
                    <label class="text-sm font-bold text-slate-700 ml-1">确认密码 <span class="text-red-500">*</span></label>
                    <input 
                      v-model="form.confirmPassword"
                      type="password" 
                      placeholder="请再次输入密码" 
                      class="w-full bg-slate-50 border border-slate-200 text-slate-800 rounded-xl px-5 py-3.5 focus:outline-none focus:ring-2 focus:ring-green-400 focus:bg-white transition-all font-medium"
                      :required="!isLoginMode"
                    />
                  </div>

                  <!-- 手机号码 -->
                  <div class="space-y-1.5">
                    <label class="text-sm font-bold text-slate-700 ml-1">手机号 <span class="text-red-500">*</span></label>
                    <input 
                      v-model="form.phoneNumber"
                      type="tel" 
                      placeholder="请输入真实手机号码" 
                      class="w-full bg-slate-50 border border-slate-200 text-slate-800 rounded-xl px-5 py-3.5 focus:outline-none focus:ring-2 focus:ring-green-400 focus:bg-white transition-all font-medium"
                      :required="!isLoginMode"
                    />
                  </div>
                </div>
              </transition>

              <!-- 提交按钮 -->
              <button 
                type="submit" 
                :disabled="isLoading"
                class="w-full bg-green-500 hover:bg-green-600 dark:bg-emerald-400 dark:hover:bg-emerald-300 active:scale-[0.98] disabled:bg-green-300 dark:disabled:bg-emerald-300/60 text-white dark:text-slate-950 py-4 rounded-xl font-extrabold text-lg shadow-lg shadow-green-500/30 dark:shadow-emerald-500/20 transition-all mt-6 flex items-center justify-center space-x-2"
              >
                <svg v-if="isLoading" class="animate-spin h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                  <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                <span>{{ isLoginMode ? '立即登录' : '提交注册' }}</span>
              </button>
            </form>

            <!-- 切换模式按钮 -->
            <div class="mt-6 text-center">
              <button @click="toggleMode" class="text-slate-500 dark:text-slate-400 font-medium text-sm hover:text-green-600 dark:hover:text-emerald-300 transition-colors">
                {{ isLoginMode ? '还没有账号？点击这里注册' : '已有账号？返回登录' }}
              </button>
            </div>
            
          </div>
        </div>
      </transition>
      </div> <!-- Close immersive header -->
      </div> <!-- Close col-span-4 -->

      <!-- Right Content Area (PC) -->
      <div class="lg:col-span-8 flex flex-col gap-6 lg:gap-8 lg:mt-0 relative z-20">
        <!-- Stats Card (Floating Overlap on mobile, Normal Grid on PC) -->
        <transition
          enter-active-class="transition duration-500 ease-out delay-300"
          enter-from-class="opacity-0 translate-y-8"
          enter-to-class="opacity-100 translate-y-0"
          leave-active-class="transition duration-300 ease-in"
          leave-from-class="opacity-100 translate-y-0"
          leave-to-class="opacity-0 translate-y-8"
        >
          <div v-show="!showAuthOverlay" class="px-6 lg:px-0 -mt-12 lg:mt-0 relative z-20 mb-8 lg:mb-0" v-motion-slide-visible-once-bottom>
            <div class="profile-stats-card bg-white dark:bg-slate-900 rounded-[2rem] p-6 lg:p-8 shadow-[0_8px_30px_rgb(0,0,0,0.06)] dark:shadow-[0_18px_50px_rgba(2,6,23,0.36)] flex justify-between divide-x divide-slate-100 dark:divide-slate-800 relative lg:hover:shadow-lg transition-colors transition-shadow border border-transparent dark:border-slate-800">
            <div class="flex-1 text-center px-2">
              <div class="text-2xl font-black text-slate-800 dark:text-slate-100 mb-1">{{ recordCount }}</div>
              <div class="text-xs font-bold text-slate-400 uppercase tracking-wide">识别次数</div>
            </div>
            <div class="flex-1 text-center px-2">
              <div class="text-2xl font-black text-slate-800 dark:text-slate-100 mb-1">{{ diseaseCount }}</div>
              <div class="text-xs font-bold text-slate-400 uppercase tracking-wide">发现病害</div>
            </div>
            <div class="flex-1 text-center px-2">
              <div class="text-2xl font-black text-slate-800 dark:text-slate-100 mb-1">{{ coveredAreaCount }}</div>
              <div class="text-xs font-bold text-slate-400 uppercase tracking-wide">覆盖区域</div>
            </div>
          </div>
        </div>
      </transition>

    <!-- Menu List -->
    <transition
      enter-active-class="transition duration-500 ease-out delay-300"
      enter-from-class="opacity-0 translate-y-8"
      enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition duration-300 ease-in"
      leave-from-class="opacity-100 translate-y-0"
      leave-to-class="opacity-0 translate-y-8"
    >
      <div v-show="!showAuthOverlay" class="profile-menu px-6 lg:px-0 space-y-4 lg:space-y-0 lg:grid lg:grid-cols-2 lg:gap-5 pb-20 lg:pb-0 relative z-[5]">
        <button 
          v-for="(item, index) in menuItems" 
          :key="index"
          @click="handleMenuClick(item)"
          class="profile-menu-item w-full bg-white dark:bg-slate-900 p-5 lg:p-6 rounded-2xl lg:rounded-[1.5rem] shadow-[0_4px_20px_rgb(0,0,0,0.03)] dark:shadow-[0_18px_40px_rgba(2,6,23,0.28)] border border-slate-50 dark:border-slate-800 flex justify-between items-center active:scale-[0.98] lg:hover:-translate-y-1 lg:hover:shadow-green-900/10 transition-all hover:shadow-lg hover:shadow-green-900/5 group"
        >
          <div class="flex items-center space-x-4 lg:space-x-5">
            <div class="w-10 h-10 lg:w-12 lg:h-12 rounded-xl bg-slate-50 dark:bg-slate-800 flex items-center justify-center text-xl lg:text-2xl group-hover:bg-green-50 dark:group-hover:bg-emerald-500/10 group-hover:scale-110 transition-all duration-300">
               {{ item.icon }}
            </div>
            <span class="font-bold text-slate-700 dark:text-slate-100 lg:text-lg">{{ item.label }}</span>
          </div>
          <div class="flex items-center space-x-3">
            <span v-if="item.badge" class="bg-red-500 text-white text-[10px] lg:text-xs font-bold px-2 py-0.5 rounded-full shadow-red-500/30 shadow-lg">{{ item.badge }}</span>
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5 text-slate-300 dark:text-slate-600 transform group-hover:translate-x-1 lg:group-hover:translate-x-2 lg:group-hover:text-green-500 dark:lg:group-hover:text-emerald-300 transition-all" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 5l7 7-7 7" />
            </svg>
          </div>
        </button>

        <button 
          v-if="currentUser"
          @click="handleLogout"
          class="profile-logout-button w-full lg:col-span-2 bg-white dark:bg-slate-900 text-red-500 dark:text-rose-300 py-4 lg:py-5 rounded-[1.25rem] lg:rounded-[1.5rem] font-black text-[15px] lg:text-base shadow-[0_4px_20px_rgb(0,0,0,0.03)] dark:shadow-[0_18px_40px_rgba(2,6,23,0.28)] border border-slate-50 dark:border-slate-800 active:scale-[0.98] lg:hover:shadow-red-500/10 transition-all hover:bg-red-50 dark:hover:bg-rose-500/10 mt-8 lg:mt-4"
        >
          退出当前账号
        </button>
        </div>
      </transition>
      </div> <!-- End Right Content Area -->
    </div> <!-- End Grid Container -->
  </div>
</template>

<style scoped>
/* 隐藏密码默认的眼睛图标 */
input[type="password"]::-ms-reveal,
input[type="password"]::-ms-clear {
  display: none;
}

/* 隐藏滚动条但保留滚动功能 */
.custom-scrollbar::-webkit-scrollbar {
  display: none;
}
.custom-scrollbar {
  -ms-overflow-style: none;  /* IE and Edge */
  scrollbar-width: none;  /* Firefox */
}

:global(.dark .profile-page) {
  color: #e2e8f0;
}

:global(.dark .profile-hero-content > .bg-white) {
  background: rgba(15, 23, 42, 0.9);
  box-shadow: 0 18px 40px rgba(2, 6, 23, 0.3);
}

:global(.dark .profile-auth-card) {
  border: 1px solid #1e293b;
}

:global(.dark .profile-auth-card label) {
  color: #cbd5e1;
}

:global(.dark .profile-auth-card input) {
  background: #1e293b;
  border-color: #1e293b;
  color: #f8fafc;
}

:global(.dark .profile-auth-card input::placeholder) {
  color: #64748b;
}

:global(.dark .profile-auth-card .text-slate-500) {
  color: #94a3b8;
}

:global(.dark .profile-stats-card .text-xs) {
  color: #64748b;
}

:global(.dark .profile-menu-item:hover) {
  background: #0f172a;
}
</style>
