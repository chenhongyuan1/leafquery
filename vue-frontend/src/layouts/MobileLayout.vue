<script setup>
import { RouterView, useRoute } from 'vue-router'
import { ref, watch, nextTick } from 'vue'
import TabBar from '../components/TabBar.vue'

const route = useRoute()
const scrollContainer = ref(null)

// Manually reset scroll position for the custom container on route change
watch(() => route.path, () => {
  nextTick(() => {
    if (scrollContainer.value) {
      scrollContainer.value.scrollTop = 0
    }
  })
})
</script>

<template>
  <!-- Mobile Frame / Container -->
  <div class="relative w-full h-full max-w-[480px] bg-slate-50 flex flex-col overflow-hidden shadow-2xl sm:rounded-[3rem] sm:h-[95vh] sm:border-[8px] sm:border-slate-900 mx-auto">
     
     <!-- Status Bar Placeholder (Visual only) -->
     <div class="h-12 w-full bg-slate-50 flex items-end px-6 pb-2 justify-between z-20 select-none">
        <span class="text-xs font-semibold text-slate-900">9:41</span>
        <div class="flex space-x-1.5">
          <div class="w-4 h-4 bg-slate-900 rounded-full opacity-20"></div>
          <div class="w-4 h-4 bg-slate-900 rounded-full opacity-20"></div>
          <div class="w-4 h-4 bg-slate-900 rounded-full opacity-80"></div>
        </div>
     </div>

     <!-- Content Area -->
     <div ref="scrollContainer" class="flex-1 w-full overflow-y-auto scrollbar-hide relative z-10 bg-slate-50" :class="{ 'pb-[100px]': !route.meta.hideTabBar }">
       <RouterView v-slot="{ Component }">
          <transition 
            enter-active-class="transition ease-out duration-300 transform"
            enter-from-class="opacity-0 translate-y-4"
            enter-to-class="opacity-100 translate-y-0"
            leave-active-class="transition ease-in duration-200 transform"
            leave-from-class="opacity-100 translate-y-0"
            leave-to-class="opacity-0 -translate-y-4"
            mode="out-in"
          >
            <component :is="Component" />
          </transition>
       </RouterView>
     </div>
    
     <!-- TabBar: Absolute Bottom Overlay -->
     <div v-if="!route.meta.hideTabBar" class="absolute bottom-0 left-0 w-full z-50 transition-all duration-300">
        <TabBar />
     </div>
  </div>
</template>
