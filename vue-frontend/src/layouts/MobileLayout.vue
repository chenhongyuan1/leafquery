<script setup>
import { RouterView, useRoute } from 'vue-router'
import { ref, watch, nextTick } from 'vue'
import TabBar from '../components/mobile/TabBar.vue'

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
  <div class="mobile-theme-shell relative w-full h-full max-w-[480px] bg-slate-50 dark:bg-slate-950 flex flex-col overflow-hidden shadow-2xl dark:shadow-black/40 sm:rounded-[3rem] sm:h-[95vh] sm:border-[8px] sm:border-slate-900 dark:sm:border-slate-700 mx-auto transition-colors">
     

     <!-- Content Area -->
     <div ref="scrollContainer" class="mobile-theme-content flex-1 w-full overflow-y-auto scrollbar-hide relative z-10 bg-slate-50 dark:bg-slate-950 transition-colors" :class="{ 'pb-[100px]': !route.meta.hideTabBar }">
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

<style>
.dark .mobile-theme-shell {
  background:
    radial-gradient(circle at top, rgba(16, 185, 129, 0.16), transparent 34%),
    linear-gradient(180deg, #020617 0%, #0f172a 42%, #111827 100%);
  color: #e2e8f0;
}

.dark .mobile-theme-shell .bg-white,
.dark .mobile-theme-shell [class*="bg-white/"] {
  background-color: rgba(15, 23, 42, 0.88) !important;
}

.dark .mobile-theme-shell .bg-slate-50,
.dark .mobile-theme-shell [class*="bg-slate-50/"] {
  background-color: rgba(30, 41, 59, 0.78) !important;
}

.dark .mobile-theme-shell .bg-slate-100,
.dark .mobile-theme-shell [class*="bg-slate-100/"] {
  background-color: rgba(51, 65, 85, 0.82) !important;
}

.dark .mobile-theme-shell .bg-slate-200,
.dark .mobile-theme-shell [class*="bg-slate-200/"] {
  background-color: rgba(71, 85, 105, 0.82) !important;
}

.dark .mobile-theme-shell .bg-green-50,
.dark .mobile-theme-shell [class*="bg-green-50/"] {
  background-color: rgba(16, 185, 129, 0.16) !important;
}

.dark .mobile-theme-shell .bg-blue-50,
.dark .mobile-theme-shell [class*="bg-blue-50/"] {
  background-color: rgba(59, 130, 246, 0.16) !important;
}

.dark .mobile-theme-shell .bg-red-50,
.dark .mobile-theme-shell [class*="bg-red-50/"] {
  background-color: rgba(239, 68, 68, 0.16) !important;
}

.dark .mobile-theme-shell .bg-amber-50,
.dark .mobile-theme-shell [class*="bg-amber-50/"] {
  background-color: rgba(245, 158, 11, 0.16) !important;
}

.dark .mobile-theme-shell .bg-orange-50,
.dark .mobile-theme-shell [class*="bg-orange-50/"] {
  background-color: rgba(249, 115, 22, 0.16) !important;
}

.dark .mobile-theme-shell .bg-purple-50,
.dark .mobile-theme-shell [class*="bg-purple-50/"] {
  background-color: rgba(168, 85, 247, 0.16) !important;
}

.dark .mobile-theme-shell .border-slate-50,
.dark .mobile-theme-shell .border-slate-100,
.dark .mobile-theme-shell .border-slate-200,
.dark .mobile-theme-shell [class*="border-slate-50/"],
.dark .mobile-theme-shell [class*="border-slate-100/"],
.dark .mobile-theme-shell [class*="border-slate-200/"] {
  border-color: rgba(71, 85, 105, 0.76) !important;
}

.dark .mobile-theme-shell .border-green-100,
.dark .mobile-theme-shell .border-green-200,
.dark .mobile-theme-shell [class*="border-green-100/"],
.dark .mobile-theme-shell [class*="border-green-200/"] {
  border-color: rgba(52, 211, 153, 0.28) !important;
}

.dark .mobile-theme-shell .border-blue-100,
.dark .mobile-theme-shell .border-blue-200,
.dark .mobile-theme-shell [class*="border-blue-100/"],
.dark .mobile-theme-shell [class*="border-blue-200/"] {
  border-color: rgba(96, 165, 250, 0.28) !important;
}

.dark .mobile-theme-shell .border-red-100,
.dark .mobile-theme-shell .border-red-200,
.dark .mobile-theme-shell [class*="border-red-100/"],
.dark .mobile-theme-shell [class*="border-red-200/"] {
  border-color: rgba(248, 113, 113, 0.28) !important;
}

.dark .mobile-theme-shell .border-amber-100,
.dark .mobile-theme-shell .border-amber-200,
.dark .mobile-theme-shell [class*="border-amber-100/"],
.dark .mobile-theme-shell [class*="border-amber-200/"] {
  border-color: rgba(251, 191, 36, 0.28) !important;
}

.dark .mobile-theme-shell .border-orange-100,
.dark .mobile-theme-shell .border-orange-200,
.dark .mobile-theme-shell [class*="border-orange-100/"],
.dark .mobile-theme-shell [class*="border-orange-200/"] {
  border-color: rgba(251, 146, 60, 0.28) !important;
}

.dark .mobile-theme-shell .border-purple-100,
.dark .mobile-theme-shell .border-purple-200,
.dark .mobile-theme-shell [class*="border-purple-100/"],
.dark .mobile-theme-shell [class*="border-purple-200/"] {
  border-color: rgba(192, 132, 252, 0.28) !important;
}

.dark .mobile-theme-shell .text-slate-900,
.dark .mobile-theme-shell .text-slate-800 {
  color: #f8fafc !important;
}

.dark .mobile-theme-shell .text-slate-700,
.dark .mobile-theme-shell .text-slate-600 {
  color: #e2e8f0 !important;
}

.dark .mobile-theme-shell .text-slate-500,
.dark .mobile-theme-shell .text-slate-400,
.dark .mobile-theme-shell .text-slate-300 {
  color: #94a3b8 !important;
}

.dark .mobile-theme-shell input,
.dark .mobile-theme-shell textarea,
.dark .mobile-theme-shell select {
  color: #f8fafc;
}

.dark .mobile-theme-shell input::placeholder,
.dark .mobile-theme-shell textarea::placeholder {
  color: #94a3b8;
}

.dark .mobile-theme-shell .prose,
.dark .mobile-theme-shell .prose p,
.dark .mobile-theme-shell .prose li,
.dark .mobile-theme-shell .prose strong {
  color: #e2e8f0 !important;
}
</style>
