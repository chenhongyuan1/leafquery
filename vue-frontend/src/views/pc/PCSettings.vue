<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'
import { useRouter } from 'vue-router'
import { useFarmStore } from '../../stores/farmCloud'

const router = useRouter()
const farmStore = useFarmStore()

// State
const currentUser = ref(null)
const activeMenu = ref('profile')
const recordCount = ref(0)
const diseaseCount = ref(0)

const coveredAreaCount = computed(() => {
  return new Set(farmStore.crops.map(c => c.region)).size
})

const fetchStats = async () => {
  if (currentUser.value && currentUser.value.userId) {
    try {
      const res = await axios.get(`/api/record/list?userId=${currentUser.value.userId}`)
      if (res.data?.code === 200) {
        const history = res.data.data
        recordCount.value = history.length
        const pests = history
          .filter(h => h.pestName && !h.pestName.includes('健康') && !h.pestName.includes('Healthy') && !h.pestName.includes('未发现'))
          .map(h => h.pestName)
        diseaseCount.value = new Set(pests).size
      }
    } catch (e) {
      console.error('Failed to get stats', e)
    }
  }
}

onMounted(() => {
  const savedUser = localStorage.getItem('user')
  if (savedUser) {
    currentUser.value = JSON.parse(savedUser)
    fetchStats()
  }
})

const handleLogout = () => {
  localStorage.removeItem('user')
  currentUser.value = null
  window.location.reload()
}
</script>

<template>
  <div class="h-full flex flex-col p-8 bg-slate-50/50 dark:bg-slate-900/50">
    <!-- Header -->
    <div class="mb-8 shrink-0">
      <h2 class="text-3xl font-black text-slate-800 dark:text-slate-100 tracking-tight">账户中心</h2>
      <p class="text-slate-500 dark:text-slate-400 mt-1 font-medium">管理您的个人资料、农场以及系统设置</p>
    </div>
    
    <div class="flex-1 bg-white dark:bg-slate-900 rounded-[2rem] shadow-sm border border-slate-100 dark:border-slate-800 p-8 flex flex-col lg:flex-row gap-12 overflow-hidden">
      
      <!-- Left Sidebar Menu -->
      <div class="w-full lg:w-64 shrink-0 flex flex-col space-y-2 border-b lg:border-b-0 lg:border-r border-slate-100 dark:border-slate-800 pb-8 lg:pb-0 lg:pr-8">
         <button @click="activeMenu = 'profile'"
                 class="text-left py-3 px-5 rounded-xl font-bold transition-colors"
                 :class="activeMenu === 'profile' ? 'bg-slate-900 text-white shadow-md' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:bg-slate-800'">
            🙎‍♂️ 个人资料
         </button>
         <button @click="activeMenu = 'farm'"
                 class="text-left py-3 px-5 rounded-xl font-bold transition-colors"
                 :class="activeMenu === 'farm' ? 'bg-slate-900 text-white shadow-md' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:bg-slate-800'">
            🏡 我的农场
         </button>
         <button @click="activeMenu = 'favorites'"
                 class="text-left py-3 px-5 rounded-xl font-bold transition-colors"
                 :class="activeMenu === 'favorites' ? 'bg-slate-900 text-white shadow-md' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:bg-slate-800'">
            ⭐ 知识收藏
         </button>
         <button @click="activeMenu = 'records'"
                 class="text-left py-3 px-5 rounded-xl font-bold transition-colors"
                 :class="activeMenu === 'records' ? 'bg-slate-900 text-white shadow-md' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:bg-slate-800'">
            📝 诊断历史
         </button>
         <button @click="activeMenu = 'preferences'"
                 class="text-left py-3 px-5 rounded-xl font-bold transition-colors"
                 :class="activeMenu === 'preferences' ? 'bg-slate-900 text-white shadow-md' : 'text-slate-600 dark:text-slate-300 hover:bg-slate-100 dark:bg-slate-800'">
            ⚙️ 系统偏好
         </button>
         
         <div class="pt-8 mt-auto">
            <button @click="handleLogout" class="text-left py-3 px-5 rounded-xl text-red-500 hover:bg-red-50 font-bold w-full border border-transparent hover:border-red-100 transition-colors">
               登出当前账号
            </button>
         </div>
      </div>
      
      <!-- Right Content Area -->
      <div class="flex-1 overflow-y-auto custom-scrollbar lg:pr-6">
         
         <!-- Profile Tab -->
         <div v-if="activeMenu === 'profile'" class="space-y-8 animate-fade-in">
            <!-- Avatar Section -->
            <div class="flex items-center space-x-6 bg-slate-50 dark:bg-slate-800 p-6 rounded-2xl border border-slate-100 dark:border-slate-800">
               <div class="w-24 h-24 rounded-[1.5rem] bg-green-500 flex items-center justify-center text-white text-3xl font-bold overflow-hidden shadow-inner border-4 border-white">
                  <img v-if="currentUser?.avatarUrl" :src="currentUser.avatarUrl" class="w-full h-full object-cover" />
                  <span v-else>{{ currentUser?.username?.[0]?.toUpperCase() || '?' }}</span>
               </div>
               <div>
                  <h3 class="text-2xl font-black text-slate-800 dark:text-slate-100">{{ currentUser?.username || '未登录访客' }}</h3>
                  <p class="text-slate-500 dark:text-slate-400 text-sm mt-1 mb-4">加入于 2026 年</p>
                  <div class="flex space-x-3">
                     <button class="px-5 py-2.5 bg-slate-900 hover:bg-black text-white rounded-xl font-bold text-sm shadow-md transition-transform active:scale-95 disabled:opacity-50" :disabled="!currentUser">更换头像</button>
                     <button class="px-5 py-2.5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:bg-slate-800 text-slate-700 dark:text-slate-200 rounded-xl font-bold text-sm transition-colors disabled:opacity-50" :disabled="!currentUser">移除</button>
                  </div>
               </div>
            </div>

            <!-- Stats Grid -->
            <div class="grid grid-cols-1 md:grid-cols-3 gap-6">
               <div class="bg-white dark:bg-slate-900 p-6 rounded-2xl border border-slate-100 dark:border-slate-800 shadow-sm">
                  <div class="w-12 h-12 bg-green-50 dark:bg-green-500/10 text-green-500 dark:text-green-400 rounded-xl flex items-center justify-center text-2xl mb-4">🔬</div>
                  <div class="text-3xl font-black text-slate-800 dark:text-slate-100">{{ recordCount }}</div>
                  <div class="text-sm font-bold text-slate-400 mt-1">累计识别次数</div>
               </div>
               <div class="bg-white dark:bg-slate-900 p-6 rounded-2xl border border-slate-100 dark:border-slate-800 shadow-sm">
                  <div class="w-12 h-12 bg-blue-50 dark:bg-blue-500/10 text-blue-500 dark:text-blue-400 rounded-xl flex items-center justify-center text-2xl mb-4">🦠</div>
                  <div class="text-3xl font-black text-slate-800 dark:text-slate-100">{{ diseaseCount }}</div>
                  <div class="text-sm font-bold text-slate-400 mt-1">发现病害种类</div>
               </div>
               <div class="bg-white dark:bg-slate-900 p-6 rounded-2xl border border-slate-100 dark:border-slate-800 shadow-sm">
                  <div class="w-12 h-12 bg-amber-50 dark:bg-amber-500/10 text-amber-500 dark:text-amber-400 rounded-xl flex items-center justify-center text-2xl mb-4">📍</div>
                  <div class="text-3xl font-black text-slate-800 dark:text-slate-100">{{ coveredAreaCount }}</div>
                  <div class="text-sm font-bold text-slate-400 mt-1">农场覆盖区域</div>
               </div>
            </div>

            <!-- Profile Form -->
            <div class="space-y-6">
               <h3 class="text-xl font-bold text-slate-800 dark:text-slate-100 border-b border-slate-100 dark:border-slate-800 pb-4">基本信息</h3>
               <div class="grid grid-cols-2 gap-6">
                  <div>
                     <label class="block text-sm font-bold text-slate-600 dark:text-slate-300 mb-2">用户名</label>
                     <input type="text" :value="currentUser?.username" disabled class="w-full bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl px-5 py-3.5 text-slate-500 dark:text-slate-400 font-medium" />
                  </div>
                  <div>
                     <label class="block text-sm font-bold text-slate-600 dark:text-slate-300 mb-2">手机号码</label>
                     <input type="text" :value="currentUser?.phoneNumber || '未绑定'" disabled class="w-full bg-slate-50 dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl px-5 py-3.5 text-slate-500 dark:text-slate-400 font-medium" />
                  </div>
                  <div class="col-span-2">
                     <label class="block text-sm font-bold text-slate-600 dark:text-slate-300 mb-2">邮箱地址</label>
                     <input type="email" placeholder="example@email.com" class="w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl px-5 py-3.5 text-slate-800 dark:text-slate-100 font-medium focus:ring-2 focus:ring-green-400 focus:outline-none transition-shadow" />
                  </div>
               </div>
               <div class="pt-4">
                  <button class="px-8 py-3.5 bg-green-500 hover:bg-green-600 rounded-xl text-white font-bold shadow-lg shadow-green-500/20 active:scale-95 transition-all">保存修改</button>
               </div>
            </div>
         </div>

         <!-- Placeholder for other tabs -->
         <div v-else class="h-full flex flex-col items-center justify-center text-slate-400 space-y-4 animate-fade-in">
             <div class="text-6xl grayscale opacity-30">🚧</div>
             <h3 class="text-xl font-bold text-slate-500 dark:text-slate-400">模块正在迁移至工作台...</h3>
             <p class="text-sm">您可以通过移动端继续访问全部功能</p>
         </div>

      </div>
    </div>
  </div>
</template>

<style scoped>
.custom-scrollbar::-webkit-scrollbar { width: 6px; }
.custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
.custom-scrollbar::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }
.custom-scrollbar::-webkit-scrollbar-thumb:hover { background: #94a3b8; }

@keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
.animate-fade-in { animation: fadeIn 0.3s cubic-bezier(0.16, 1, 0.3, 1) forwards; }
</style>
