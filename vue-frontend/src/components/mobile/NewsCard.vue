<script setup>
defineProps({
  title: String,
  image: String,
  tag: String,
  date: String,
  views: Number
})
</script>

<template>
  <div class="flex h-full flex-col overflow-hidden rounded-xl border border-slate-100 bg-white shadow-sm transform transition-transform active:scale-95 dark:border-slate-800 dark:bg-slate-900 dark:shadow-[0_12px_36px_rgba(2,6,23,0.22)]">
    <div class="relative h-32 overflow-hidden bg-slate-200 dark:bg-slate-800">
        <!-- Image placeholder with gradient -->
       <div class="absolute inset-0 bg-gradient-to-br from-green-100 to-blue-100 dark:from-emerald-900/60 dark:to-sky-900/60"></div>
       <div class="absolute top-2 left-2 bg-black/50 backdrop-blur-sm text-white text-[10px] px-2 py-1 rounded-md">
         {{ tag }}
       </div>
    </div>
    <div class="p-3 flex-1 flex flex-col justify-between">
      <h3 class="mb-2 line-clamp-2 text-sm font-medium leading-snug text-slate-800 dark:text-slate-100">{{ title }}</h3>
      <div class="flex items-center justify-between text-[10px] text-slate-400 dark:text-slate-500">
        <span>{{ date }}</span>
        <span class="flex items-center space-x-1">
          <span>👁️</span>
          <span>{{ views }}</span>
        </span>
      </div>
    </div>
  </div>
</template>
