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
  <div class="bg-white rounded-xl overflow-hidden shadow-sm border border-slate-100 flex flex-col h-full transform transition-transform active:scale-95">
    <div class="relative h-32 bg-slate-200 overflow-hidden">
        <!-- Image placeholder with gradient -->
       <div class="absolute inset-0 bg-gradient-to-br from-green-100 to-blue-100"></div>
       <div class="absolute top-2 left-2 bg-black/50 backdrop-blur-sm text-white text-[10px] px-2 py-1 rounded-md">
         {{ tag }}
       </div>
    </div>
    <div class="p-3 flex-1 flex flex-col justify-between">
      <h3 class="font-medium text-slate-800 text-sm line-clamp-2 leading-snug mb-2">{{ title }}</h3>
      <div class="flex justify-between items-center text-[10px] text-slate-400">
        <span>{{ date }}</span>
        <span class="flex items-center space-x-1">
          <span>👁️</span>
          <span>{{ views }}</span>
        </span>
      </div>
    </div>
  </div>
</template>
