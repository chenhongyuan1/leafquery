<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
const router = useRouter()
const route = useRoute()

const tabs = [
  { name: 'home', label: '识别', type: 'camera' },
  { name: 'discovery', label: '发现', type: 'compass' },
  { name: 'prediction', label: '预测', type: 'chart' },
  { name: 'profile', label: '我的', type: 'user' },
]

const activeIndex = computed(() => {
  return tabs.findIndex(tab => tab.name === route.name)
})
</script>

<template>
  <div class="relative w-full pb-safe pointer-events-none">
    
    <!-- Gooey Filter Definition -->
    <!-- Adjusted Matrix for sharper, thinner liquid edges -->
    <svg style="position: absolute; width: 0; height: 0; pointer-events: none;">
      <defs>
        <filter id="goo">
          <feGaussianBlur in="SourceGraphic" stdDeviation="10" result="blur" />
          <feColorMatrix in="blur" mode="matrix" values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 20 -9" result="goo" />
          <feComposite in="SourceGraphic" in2="goo" operator="atop"/>
        </filter>
      </defs>
    </svg>

    <!-- Main Bar Container (The "Liquid" Body) -->
    <div class="relative h-[80px] w-full pointer-events-auto">
        
        <!-- The Shape Layer (Filtered) -->
        <div class="absolute inset-0 w-full h-full filter-goo z-10 pointer-events-none">
            <!-- Base Bar -->
            <div class="absolute bottom-0 left-0 w-full h-[60px] bg-white shadow-lg dark:bg-slate-900 dark:shadow-[0_18px_40px_rgba(2,6,23,0.48)]"></div>
            
            <!-- The Moving Hump (White Circle) -->
            <!-- Reduced size and position to create a subtler rise -->
            <div 
              class="absolute bottom-[20px] w-[80px] h-[80px] bg-white dark:bg-slate-900 rounded-full transition-all duration-500 cubic-bezier-spring"
              :style="{ 
                 left: `calc((100% / 4 * ${activeIndex}) + 12.5% - 40px)`
              }"
            ></div>
        </div>

        <!-- Real Content Layer (Icons, Labels, Green Circle) -->
        <div class="absolute inset-0 w-full h-full z-20">
            
            <!-- Active Green Circle -->
            <!-- Sunk deeper: bottom-25px means about 2/3rds is inside the 60px base bar + hump -->
            <div 
               class="absolute bottom-[28px] w-14 h-14 bg-green-500 rounded-full flex items-center justify-center text-white shadow-lg shadow-green-500/30 dark:shadow-green-500/20 transition-all duration-500 cubic-bezier-spring pointer-events-none"
               :style="{ 
                  left: `calc((100% / 4 * ${activeIndex}) + 12.5% - 28px)`
               }"
            ></div>

            <!-- Tab Buttons Container -->
            <div class="w-full h-full flex items-end">
                <button 
                  v-for="(tab, index) in tabs" 
                  :key="tab.name"
                  @click="router.push({ name: tab.name })"
                  class="flex-1 h-[80px] flex flex-col items-center justify-end pb-4 relative cursor-pointer"
                  style="min-width: 0;"
                >
                   <!-- Icon Wrapper -->
                   <!-- Adjusted jump height to match new circle position -->
                   <div 
                     class="absolute z-30 transition-all duration-500 cubic-bezier-spring"
                     :class="activeIndex === index ? 'bottom-[44px] text-white' : 'bottom-[32px] text-slate-400 dark:text-slate-500'"
                   >
                      <!-- CAMERA -->
                      <svg v-if="tab.type === 'camera'" viewBox="0 0 24 24" class="w-6 h-6">
                         <path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                         <circle cx="12" cy="13" r="4" fill="none" stroke="currentColor" stroke-width="2" :class="activeIndex === index ? 'animate-lens' : ''" />
                         <circle cx="12" cy="13" r="1.5" fill="currentColor" class="opacity-0 transition-opacity duration-300 delay-200" :class="activeIndex === index ? 'opacity-100' : 'opacity-0'" />
                      </svg>
                      
                      <!-- COMPASS -->
                      <svg v-if="tab.type === 'compass'" viewBox="0 0 24 24" class="w-6 h-6">
                         <circle cx="12" cy="12" r="10" fill="none" stroke="currentColor" stroke-width="2" />
                         <path d="M16.24 7.76l-2.12 6.36-6.36 2.12 2.12-6.36 6.36-2.12z" fill="currentColor" stroke="none" class="origin-center transition-transform duration-700 ease-elastic" :class="activeIndex === index ? 'rotate-[360deg]' : 'rotate-0'" />
                         <path d="M12 2a10 10 0 1 0 10 10A10 10 0 0 0 12 2zm0 18a8 8 0 1 1 8-8 8 8 0 0 1-8 8z" fill="currentColor" opacity="0.2" />
                      </svg>
                      
                      <!-- CHART -->
                      <svg v-if="tab.type === 'chart'" viewBox="0 0 24 24" class="w-6 h-6">
                         <rect x="18" y="10" width="4" height="10" rx="1" fill="currentColor" class="transition-all duration-300 delay-75 origin-bottom" :class="activeIndex === index ? 'scale-y-100 opacity-100' : 'scale-y-75 opacity-50'" />
                         <rect x="10" y="5" width="4" height="15" rx="1" fill="currentColor" class="transition-all duration-300 delay-150 origin-bottom" :class="activeIndex === index ? 'scale-y-100 opacity-100' : 'scale-y-75 opacity-50'" />
                         <rect x="2" y="14" width="4" height="6" rx="1" fill="currentColor" class="transition-all duration-300 delay-225 origin-bottom" :class="activeIndex === index ? 'scale-y-100 opacity-100' : 'scale-y-75 opacity-50'" />
                      </svg>
                      
                      <!-- USER -->
                      <svg v-if="tab.type === 'user'" viewBox="0 0 24 24" class="w-6 h-6">
                         <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                         <circle cx="12" cy="7" r="4" fill="none" stroke="currentColor" stroke-width="2" class="origin-bottom transition-transform duration-500 ease-in-out" :class="activeIndex === index ? 'rotate-12 translate-y-[-2px]' : 'rotate-0 translate-y-0'" />
                         <path d="M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z" fill="currentColor" class="opacity-0 transition-opacity" :class="activeIndex === index ? 'opacity-30' : 'opacity-0'" />
                      </svg>
                   </div>
                   
                   <!-- Label -->
                   <span 
                      class="text-[10px] font-bold tracking-wide transition-all duration-300 transform block"
                      :class="activeIndex === index ? 'opacity-0 translate-y-4' : 'opacity-100 translate-y-0 text-slate-500 dark:text-slate-400'"
                   >
                      {{ tab.label }}
                   </span>
                </button>
            </div>
        </div>
    </div>
  </div>
</template>

<style scoped>
.pb-safe { padding-bottom: max(env(safe-area-inset-bottom), 0px); }

/* The magic Gooey Filter class */
.filter-goo {
  filter: url('#goo');
  -webkit-filter: url('#goo');
  backface-visibility: hidden;
  transform: translateZ(0);
}

.cubic-bezier-spring {
  transition-timing-function: cubic-bezier(0.5, 1.2, 0.5, 1);
}

/* Optimized Keyframes */
@keyframes lens {
  0% { transform: scale(1); stroke-width: 2; }
  50% { transform: scale(0.8); stroke-width: 3; }
  100% { transform: scale(1); stroke-width: 2; }
}
.animate-lens { animation: lens 0.4s ease-in-out; transform-origin: center; }

.ease-elastic { transition-timing-function: cubic-bezier(0.34, 1.56, 0.64, 1); }
</style>
