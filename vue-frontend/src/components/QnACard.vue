<script setup>
import { ref } from 'vue'

const props = defineProps({
  user: String,
  userAvatar: String,
  time: String,
  content: String,
  images: Array,
  expertReply: Object,
  likes: { type: Number, default: 0 },
  comments: { type: Array, default: () => [] }
})

const isLiked = ref(false)
const localLikes = ref(props.likes)
const showComments = ref(false)
const newComment = ref('')

const toggleLike = () => {
  isLiked.value = !isLiked.value
  localLikes.value += isLiked.value ? 1 : -1
}

const submitComment = () => {
  if (!newComment.value.trim()) return
  // Unshift local comment directly for demo purposes
  props.comments.unshift({
    user: '我',
    content: newComment.value,
    time: '刚刚'
  })
  newComment.value = ''
}
</script>

<template>
  <div class="bg-white rounded-2xl p-4 shadow-[0_2px_15px_rgba(0,0,0,0.03)] border border-slate-50 mb-4 break-inside-avoid">
    <!-- User Header -->
    <div class="flex items-center space-x-3 mb-3">
      <div class="w-10 h-10 rounded-full bg-slate-100 overflow-hidden">
        <img v-if="userAvatar" :src="userAvatar" class="w-full h-full object-cover" />
        <div v-else class="w-full h-full bg-gradient-to-br from-blue-400 to-indigo-400"></div>
      </div>
      <div>
        <div class="text-sm font-bold text-slate-800">{{ user }}</div>
        <div class="text-[10px] text-slate-400">{{ time }}</div>
      </div>
    </div>

    <!-- Question Content -->
    <p class="text-slate-700 text-sm mb-3 leading-relaxed rounded-lg">{{ content }}</p>

    <!-- Images Grid -->
    <div v-if="images && images.length" class="grid gap-2 mb-4" :class="images.length === 1 ? 'grid-cols-1' : 'grid-cols-3'">
      <div 
        v-for="(img, idx) in images" 
        :key="idx" 
        class="rounded-xl overflow-hidden bg-slate-100 relative"
        :class="images.length === 1 ? 'h-48' : 'h-24 aspect-square'"
      >
         <img v-if="img" :src="img" class="w-full h-full object-cover absolute inset-0 z-10" />
         <!-- Placeholder color if no real src -->
         <div class="absolute inset-0 bg-slate-200 flex items-center justify-center text-slate-300">
           🖼️
         </div>
      </div>
    </div>

    <!-- Expert Reply -->
    <div v-if="expertReply" class="bg-green-50/50 rounded-xl p-3 border border-green-100 relative mb-4">
      <div class="absolute -top-1.5 left-4 w-3 h-3 bg-green-50 border-t border-l border-green-100 transform rotate-45"></div>
      
      <div class="flex items-center space-x-2 mb-1">
        <div class="px-1.5 py-0.5 bg-green-500 text-white text-[10px] font-bold rounded-md flex items-center">
            <span class="mr-1">🎓</span> 
            <span>专家回答</span>
        </div>
        <span class="text-xs font-bold text-slate-700">{{ expertReply.expertName }}</span>
      </div>
      <p class="text-xs text-slate-600 leading-relaxed">
        {{ expertReply.content }}
      </p>
    </div>

    <!-- Actions Bar -->
    <div class="flex justify-between items-center text-slate-400 mt-2 border-t border-slate-50 pt-3">
      <button class="flex items-center space-x-1.5 hover:text-green-500 transition-colors">
        <span class="text-lg">↗️</span>
        <span class="text-xs font-medium">分享</span>
      </button>
      <div class="flex space-x-6">
        <button @click="showComments = !showComments" class="flex items-center space-x-1.5 hover:text-green-500 transition-colors" :class="{'text-green-500': showComments}">
          <span class="text-lg">💬</span>
          <span class="text-xs font-medium">{{ comments.length || '评论' }}</span>
        </button>
        <button @click="toggleLike" class="flex items-center space-x-1.5 transition-colors group">
          <span class="text-lg transition-transform duration-300 group-active:scale-75" :class="isLiked ? 'text-amber-500 scale-110' : 'hover:scale-110'">{{ isLiked ? '⭐' : '☆' }}</span>
          <span class="text-xs font-medium" :class="{'text-amber-500': isLiked}">{{ localLikes || '赞' }}</span>
        </button>
      </div>
    </div>

    <!-- Comments Section -->
    <div v-if="showComments" class="mt-4 border-t border-slate-100 pt-3 animate-fade-in">
      <div class="space-y-3 mb-3 max-h-48 overflow-y-auto pr-2 scrollbar-thin scrollbar-thumb-slate-200">
        <div v-if="comments.length === 0" class="text-center text-xs text-slate-400 py-2">暂无评论，来做第一个发言的人吧！</div>
        <div v-for="(comment, index) in comments" :key="index" class="flex space-x-2">
           <div class="w-6 h-6 rounded-full bg-slate-200 flex-shrink-0"></div>
           <div>
             <div class="flex items-baseline space-x-2">
                <span class="text-xs font-bold text-slate-700">{{ comment.user }}</span>
                <span class="text-[10px] text-slate-400">{{ comment.time }}</span>
             </div>
             <p class="text-xs text-slate-600 mt-0.5">{{ comment.content }}</p>
           </div>
        </div>
      </div>
      
      <!-- Comment Input -->
      <div class="flex space-x-2">
         <input 
           v-model="newComment" 
           @keyup.enter="submitComment"
           type="text" 
           placeholder="写下你的评论..." 
           class="flex-1 bg-slate-50 text-xs rounded-full px-4 py-2 border border-slate-100 focus:outline-none focus:border-green-300 focus:ring-1 focus:ring-green-300 transition-all"
         />
         <button 
           @click="submitComment"
           class="w-8 h-8 rounded-full bg-green-500 text-white flex items-center justify-center font-bold active:scale-90 transition-transform disabled:opacity-50 disabled:active:scale-100"
           :disabled="!newComment.trim()"
         >
           ↑
         </button>
      </div>
    </div>
  </div>
</template>
