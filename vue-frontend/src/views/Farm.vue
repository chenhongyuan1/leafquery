<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useFarmStore } from '../stores/farm'

const router = useRouter()
const farmStore = useFarmStore()

const showAddModal = ref(false)
const selectedNewCrop = ref(null)

const editingCrop = ref(null)

// 城市搜索相关的状态
const locationQuery = ref('')
const searchResults = ref([])
const isSearching = ref(false)
const selectedLocation = ref(null) // 选中的地点对象

// 可添加的作物（排除已添加的）
const availableCrops = computed(() => {
  const added = new Set(farmStore.crops.map(c => c.name))
  return farmStore.cropLibrary.filter(c => !added.has(c.name))
})

const openAddModal = () => {
  selectedNewCrop.value = null
  locationQuery.value = ''
  searchResults.value = []
  selectedLocation.value = null
  showAddModal.value = true
}

const confirmAdd = () => {
  if (!selectedNewCrop.value || !selectedLocation.value) return
  farmStore.addCrop(selectedNewCrop.value.name, selectedLocation.value)
  showAddModal.value = false
}

const openEdit = (crop) => {
  editingCrop.value = { ...crop }
  // 回显当前已选城市信息
  selectedLocation.value = {
    id: crop.locationId,
    city: crop.city,
    province: crop.province,
    region: crop.region
  }
  locationQuery.value = crop.city
  searchResults.value = []
}

const confirmEdit = () => {
  if (!editingCrop.value || !selectedLocation.value) return
  farmStore.updateCrop(editingCrop.value.id, {
    city: selectedLocation.value.city,
    province: selectedLocation.value.province,
    region: selectedLocation.value.region,
    locationId: selectedLocation.value.id
  })
  editingCrop.value = null
}

const deleteCrop = (cropId) => {
  farmStore.removeCrop(cropId)
  editingCrop.value = null
}

// 模糊搜索调用后端 API
const handleSearchLocation = async () => {
  if (!locationQuery.value.trim()) return
  
  isSearching.value = true
  searchResults.value = []
  
  try {
    const res = await fetch(`/api/location/search?query=${encodeURIComponent(locationQuery.value.trim())}`)
    if (res.ok) {
        searchResults.value = await res.json()
    }
  } catch (e) {
    console.error('搜索城市失败:', e)
  } finally {
    isSearching.value = false
  }
}

const selectLocation = (loc) => {
    selectedLocation.value = loc
    // 选择后清空搜索列表，并将输入框改为选中城市的名称
    locationQuery.value = `${loc.province} ${loc.city}`
    searchResults.value = []
}

</script>

<template>
  <div class="min-h-full bg-slate-50 pb-8 relative">
    <!-- Header -->
    <div class="bg-gradient-to-br from-green-600 to-emerald-700 px-6 pt-4 pb-12 rounded-b-[2.5rem] shadow-xl shadow-green-900/20 relative overflow-hidden">
      <div class="absolute -top-10 -right-10 w-40 h-40 bg-white/10 rounded-full blur-2xl"></div>
      <div class="absolute bottom-0 left-0 w-32 h-32 bg-black/10 rounded-full -ml-10 -mb-10 blur-xl"></div>
      
      <div class="relative z-10">
        <div class="flex items-center mb-6">
          <button @click="router.back()" class="w-10 h-10 bg-white/20 backdrop-blur-md rounded-xl flex items-center justify-center text-white mr-4 active:scale-95 transition-transform">
            <svg xmlns="http://www.w3.org/2000/svg" class="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <div>
            <h1 class="text-2xl font-bold text-white tracking-tight">我的农场</h1>
            <p class="text-green-100 text-xs font-medium mt-0.5">管理作物，精准气象预测</p>
          </div>
        </div>

        <!-- Stats -->
        <div class="flex space-x-4">
          <div class="flex-1 bg-white/15 backdrop-blur-md rounded-2xl p-4 border border-white/10">
            <div class="text-2xl font-black text-white">{{ farmStore.crops.length }}</div>
            <div class="text-green-100 text-xs font-bold mt-1">种植作物</div>
          </div>
          <div class="flex-1 bg-white/15 backdrop-blur-md rounded-2xl p-4 border border-white/10">
            <div class="text-2xl font-black text-white">{{ farmStore.identificationHistory.length }}</div>
            <div class="text-green-100 text-xs font-bold mt-1">识别记录</div>
          </div>
          <div class="flex-1 bg-white/15 backdrop-blur-md rounded-2xl p-4 border border-white/10">
            <!-- 统计覆盖的生态区数量 -->
            <div class="text-2xl font-black text-white">{{ new Set(farmStore.crops.map(c => c.region)).size }}</div>
            <div class="text-green-100 text-xs font-bold mt-1">覆盖区域</div>
          </div>
        </div>
      </div>
    </div>

    <!-- Crop List -->
    <div class="px-6 -mt-6 relative z-10">
      <div class="space-y-4">
        <!-- Crop Cards -->
        <div 
          v-for="crop in farmStore.crops" 
          :key="crop.id"
          class="bg-white rounded-[1.5rem] p-5 shadow-[0_4px_20px_rgb(0,0,0,0.04)] border border-slate-100 active:scale-[0.98] transition-all group cursor-pointer"
          @click="openEdit(crop)"
        >
          <div class="flex items-start justify-between">
            <div class="flex items-center space-x-4">
              <div class="w-14 h-14 rounded-2xl flex items-center justify-center text-3xl shadow-sm"
                   :class="farmStore.activeCropId === crop.id ? 'bg-green-50 ring-2 ring-green-500' : 'bg-slate-50'">
                {{ crop.icon }}
              </div>
              <div>
                <h3 class="font-bold text-slate-800 text-lg flex items-center space-x-2">
                  <span>{{ crop.name }}</span>
                  <span v-if="farmStore.activeCropId === crop.id" class="text-[10px] font-bold bg-green-100 text-green-600 px-2 py-0.5 rounded-full">当前</span>
                </h3>
                <div class="flex items-center space-x-3 mt-1.5">
                  <span class="text-xs font-medium text-slate-400 bg-slate-50 px-2 py-0.5 rounded-lg">📍 {{ crop.city }} ({{ crop.region }})</span>
                </div>
              </div>
            </div>
            <button
              @click.stop="farmStore.setActiveCrop(crop.id)"
              class="px-3 py-1.5 rounded-xl text-xs font-bold transition-all"
              :class="farmStore.activeCropId === crop.id ? 'bg-green-500 text-white shadow-lg shadow-green-500/30' : 'bg-slate-100 text-slate-500 hover:bg-green-50 hover:text-green-600'"
            >
              {{ farmStore.activeCropId === crop.id ? '已选中' : '选为当前' }}
            </button>
          </div>

          <!-- Common Diseases -->
          <div class="mt-4 pt-3 border-t border-slate-50">
            <div class="text-[10px] font-bold text-slate-400 mb-2 uppercase tracking-wider">防范病害</div>
            <div class="flex flex-wrap gap-2">
              <span 
                v-for="disease in crop.diseases" 
                :key="disease"
                class="text-[10px] font-medium text-orange-600 bg-orange-50 px-2 py-1 rounded-lg border border-orange-100/50"
              >
                {{ disease }}
              </span>
            </div>
          </div>
        </div>

        <!-- Add Button -->
        <button 
          @click="openAddModal"
          :disabled="availableCrops.length === 0"
          class="w-full bg-white rounded-[1.5rem] p-6 shadow-[0_4px_20px_rgb(0,0,0,0.04)] border-2 border-dashed border-slate-200 flex flex-col items-center justify-center space-y-2 hover:border-green-400 hover:bg-green-50/30 transition-all active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed"
        >
          <div class="w-12 h-12 bg-green-50 rounded-full flex items-center justify-center text-green-500 text-2xl font-bold">+</div>
          <span class="text-sm font-bold text-slate-500">添加作物</span>
          <span class="text-[10px] text-slate-400">{{ availableCrops.length }} 种可选</span>
        </button>
      </div>
    </div>

    <!-- Empty State -->
    <div v-if="farmStore.crops.length === 0" class="text-center mt-8 px-6 relative z-10">
      <div class="bg-white rounded-[2rem] p-10 shadow-sm border border-slate-100">
        <div class="text-5xl mb-4">🌱</div>
        <h3 class="font-bold text-slate-800 text-lg mb-2">还没有添加作物</h3>
        <p class="text-sm text-slate-400 mb-6">绑定您的农场所在城市，获取精准气象关联预测</p>
        <button @click="openAddModal" class="bg-green-500 text-white px-6 py-3 rounded-xl font-bold shadow-lg shadow-green-500/30 active:scale-95 transition-transform">
          立即添加
        </button>
      </div>
    </div>

    <!-- Add Crop Modal -->
    <div v-if="showAddModal" class="fixed inset-0 z-50 flex items-end justify-center">
      <div class="absolute inset-0 bg-slate-900/60 backdrop-blur-sm" @click="showAddModal = false"></div>
      <div class="bg-white w-full rounded-t-[2rem] p-6 relative z-10 shadow-2xl animate-slide-up max-h-[90%] flex flex-col">
        <div class="w-12 h-1.5 bg-slate-100 rounded-full mx-auto mb-6 flex-shrink-0"></div>
        <h3 class="text-xl font-bold text-slate-800 mb-6 flex-shrink-0">添加作物</h3>
        
        <div class="flex-1 overflow-y-auto overflow-x-hidden p-1 min-h-0">
          <!-- Crop Grid -->
          <label class="text-sm font-bold text-slate-700 mb-3 block">选择作物</label>
          <div class="grid grid-cols-3 gap-3 mb-6">
            <button 
              v-for="crop in availableCrops" 
              :key="crop.name"
              @click="selectedNewCrop = crop"
              class="flex flex-col items-center py-4 rounded-2xl border-2 transition-all active:scale-95"
              :class="selectedNewCrop?.name === crop.name ? 'border-green-500 bg-green-50 shadow-lg shadow-green-500/20' : 'border-slate-100 bg-white'"
            >
              <span class="text-3xl mb-2">{{ crop.icon }}</span>
              <span class="text-xs font-bold" :class="selectedNewCrop?.name === crop.name ? 'text-green-700' : 'text-slate-600'">{{ crop.name }}</span>
            </button>
          </div>

          <!-- Region Selection (City Search) -->
          <div class="mb-6">
            <label class="text-sm font-bold text-slate-700 mb-3 block">农场所在地</label>
            <div class="flex space-x-2">
              <input 
                v-model="locationQuery" 
                @keyup.enter="handleSearchLocation"
                placeholder="输入城市名，如 '北京' 或 '朝阳'" 
                class="flex-1 px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-medium focus:border-green-500 focus:ring-2 focus:ring-green-100 transition-all outline-none"
              >
              <button 
                @click="handleSearchLocation"
                class="px-5 bg-slate-100 text-slate-600 rounded-xl text-sm font-bold hover:bg-slate-200 active:scale-95 transition-all outline-none"
              >
                搜索
              </button>
            </div>
            
            <div v-if="isSearching" class="text-xs text-slate-400 mt-3 text-center">正在搜索...</div>
            
            <!-- 搜索结果列表 -->
            <div v-if="searchResults.length > 0" class="mt-3 bg-white border border-slate-100 shadow-lg rounded-xl overflow-hidden max-h-48 overflow-y-auto">
                <button 
                    v-for="loc in searchResults" :key="loc.id"
                    @click="selectLocation(loc)"
                    class="w-full text-left px-4 py-3 border-b border-slate-50 last:border-0 hover:bg-slate-50 active:bg-slate-100 transition-colors flex justify-between items-center"
                >
                    <div>
                        <div class="text-sm font-bold text-slate-800">{{ loc.province }} {{ loc.city }}</div>
                        <div class="text-[10px] text-slate-400 mt-0.5">区划代码: {{ loc.id }}</div>
                    </div>
                    <span class="text-[10px] font-bold text-blue-500 bg-blue-50 px-2 py-1 rounded-md">{{ loc.region }}</span>
                </button>
            </div>
            
            <!-- 选中的提示 -->
            <div v-if="selectedLocation && searchResults.length === 0" class="mt-4 p-4 bg-green-50 border border-green-100 rounded-xl flex items-center justify-between">
                <div>
                    <div class="text-xs font-bold text-green-700 mb-0.5">已选择地理位置</div>
                    <div class="text-sm font-bold text-slate-800">{{ selectedLocation.province }} {{ selectedLocation.city }}</div>
                </div>
                <div class="flex flex-col items-end">
                    <span class="text-[10px] font-bold bg-white text-green-600 px-2 py-1 rounded shadow-sm">{{ selectedLocation.region }}</span>
                </div>
            </div>
          </div>
        </div>

        <!-- Confirm -->
        <button 
          @click="confirmAdd"
          :disabled="!selectedNewCrop || !selectedLocation"
          class="w-full bg-slate-900 text-white py-4 rounded-xl font-bold shadow-lg shadow-slate-900/20 active:scale-[0.98] transition-transform disabled:opacity-50 disabled:cursor-not-allowed mt-4 flex-shrink-0"
        >
          确认添加
        </button>
      </div>
    </div>

    <!-- Edit Crop Modal -->
    <div v-if="editingCrop" class="fixed inset-0 z-50 flex items-end justify-center">
      <div class="absolute inset-0 bg-slate-900/60 backdrop-blur-sm" @click="editingCrop = null"></div>
      <div class="bg-white w-full rounded-t-[2rem] p-6 relative z-10 shadow-2xl animate-slide-up flex flex-col max-h-[90%]">
        <div class="w-12 h-1.5 bg-slate-100 rounded-full mx-auto mb-6 flex-shrink-0"></div>
        
        <div class="flex-1 overflow-y-auto p-1 min-h-0">
          <div class="flex items-center space-x-4 mb-6">
            <div class="w-14 h-14 bg-green-50 rounded-2xl flex items-center justify-center text-3xl">{{ editingCrop.icon }}</div>
            <div>
              <h3 class="text-xl font-bold text-slate-800">{{ editingCrop.name }}</h3>
              <p class="text-xs text-slate-400 font-medium">修改农场所在地以更新气象预测</p>
            </div>
          </div>

          <!-- Region Selection (City Search) -->
          <div class="mb-6">
            <label class="text-sm font-bold text-slate-700 mb-3 block">重新选择所在地</label>
            <div class="flex space-x-2">
              <input 
                v-model="locationQuery" 
                @keyup.enter="handleSearchLocation"
                placeholder="输入城市名，如 '北京'" 
                class="flex-1 px-4 py-3 bg-slate-50 border border-slate-200 rounded-xl text-sm font-medium focus:border-green-500 focus:ring-2 focus:ring-green-100 transition-all outline-none"
              >
              <button 
                @click="handleSearchLocation"
                class="px-5 bg-slate-100 text-slate-600 rounded-xl text-sm font-bold hover:bg-slate-200 active:scale-95 transition-all outline-none"
              >
                搜索
              </button>
            </div>
            
            <div v-if="isSearching" class="text-xs text-slate-400 mt-3 text-center">正在搜索...</div>
            
            <!-- 搜索结果列表 -->
            <div v-if="searchResults.length > 0" class="mt-3 bg-white border border-slate-100 shadow-lg rounded-xl overflow-hidden max-h-48 overflow-y-auto">
                <button 
                    v-for="loc in searchResults" :key="loc.id"
                    @click="selectLocation(loc)"
                    class="w-full text-left px-4 py-3 border-b border-slate-50 last:border-0 hover:bg-slate-50 active:bg-slate-100 transition-colors flex justify-between items-center"
                >
                    <div>
                        <div class="text-sm font-bold text-slate-800">{{ loc.province }} {{ loc.city }}</div>
                        <div class="text-[10px] text-slate-400 mt-0.5">区划代码: {{ loc.id }}</div>
                    </div>
                    <span class="text-[10px] font-bold text-blue-500 bg-blue-50 px-2 py-1 rounded-md">{{ loc.region }}</span>
                </button>
            </div>

            <!-- 选中的提示 -->
            <div v-if="selectedLocation && searchResults.length === 0" class="mt-4 p-4 bg-green-50 border border-green-100 rounded-xl flex items-center justify-between">
                <div>
                    <div class="text-xs font-bold text-green-700 mb-0.5">当前选择地理位置</div>
                    <div class="text-sm font-bold text-slate-800">{{ selectedLocation.province }} {{ selectedLocation.city }}</div>
                </div>
                <div class="flex flex-col items-end">
                    <span class="text-[10px] font-bold bg-white text-green-600 px-2 py-1 rounded shadow-sm">{{ selectedLocation.region }}</span>
                </div>
            </div>
          </div>
        </div>

        <div class="flex flex-shrink-0 space-x-3 mt-4">
          <button 
            @click="deleteCrop(editingCrop.id)"
            class="flex-shrink-0 bg-red-50 text-red-500 px-5 py-4 rounded-xl font-bold active:scale-95 transition-transform"
          >
            🗑️ 删除
          </button>
          <button 
            @click="confirmEdit"
            :disabled="!selectedLocation"
            class="flex-1 bg-slate-900 text-white py-4 rounded-xl font-bold shadow-lg shadow-slate-900/20 active:scale-[0.98] transition-transform disabled:opacity-50 disabled:cursor-not-allowed"
          >
            保存修改
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes slide-up { from { transform: translateY(100%); } to { transform: translateY(0); } }
.animate-slide-up { animation: slide-up 0.3s cubic-bezier(0.16, 1, 0.3, 1); }
</style>
