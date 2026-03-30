<script setup>
import { ref } from 'vue'
import axios from 'axios'

const props = defineProps({
  id: Number,
  userId: Number,
  currentUserId: Number,
  user: String,
  time: String,
  userAvatar: String,
  content: String,
  images: Array,
  expertReply: Object,
  expertId: Number,
  likes: { type: Number, default: 0 },
  comments: { type: Array, default: () => [] }
})

const emit = defineEmits(['delete', 'refresh'])

const isLiked = ref(false)
const localLikes = ref(props.likes)
const localExpertReply = ref(props.expertReply)
const showComments = ref(false)
const newComment = ref('')
const previewImage = ref(null)

import { watch } from 'vue'
watch(() => props.expertReply, (val) => {
  localExpertReply.value = val
})

const toggleLike = async () => {
  isLiked.value = !isLiked.value
  localLikes.value += isLiked.value ? 1 : -1
  if (props.id) {
    try {
      await axios.put(`/api/discovery/qna/${props.id}/like`, { likes: localLikes.value })
    } catch (e) {
      console.error('点赞同步失败', e)
      isLiked.value = !isLiked.value
      localLikes.value += isLiked.value ? 1 : -1
    }
  }
}

const submitComment = async () => {
  if (!newComment.value.trim()) return
  
  const userStr = localStorage.getItem('user')
  if (!userStr) {
    alert('请先登录后再发表评论！')
    return
  }
  const user = JSON.parse(userStr)

  try {
    const res = await axios.post(`/api/discovery/qna/${props.id}/comment`, {
      userId: user.userId,
      content: newComment.value
    })
    
    if (res.data.code === 200) {
      const respData = res.data.data
      
      if (respData.type === 'expert_reply') {
        localExpertReply.value = {
          expertName: respData.expertName,
          content: respData.content
        }
      } else if (respData.type === 'normal_comment') {
        const newCmt = respData.data
        props.comments.push({
          commentId: newCmt.commentId,
          userId: user.userId,
          user: newCmt.userName || user.username,
          userAvatar: newCmt.userAvatar || user.avatarUrl || '',
          content: newCmt.content,
          time: newCmt.createdAt || '刚刚'
        })
      }
      
      newComment.value = ''
    } else {
      alert('评论发表失败：' + (res.data.message || '未知错误'))
    }
  } catch (error) {
    console.error('评论提交异常', error)
    alert('网络错误或评论提交失败')
  }
}

const deleteCommentObj = async (commentId, idx) => {
  if (!confirm('确定要删除这条评论吗？')) return
  try {
    await axios.delete(`/api/discovery/qna/comment/${commentId}`)
    props.comments.splice(idx, 1)
  } catch (e) {
    alert('删除评论失败')
  }
}

const deleteExpertReplyObj = async () => {
  if (!confirm('确定要撤销这条专家解答吗？')) return
  try {
    await axios.delete(`/api/discovery/qna/${props.id}/expert-reply`)
    localExpertReply.value = null
    emit('refresh')
  } catch (e) {
    alert('撤销解答失败')
  }
}

const getInitial = (name) => name?.charAt(0)?.toUpperCase() || '?'
</script>

<template>
  <div class="mb-4 break-inside-avoid rounded-2xl border border-slate-50 bg-white p-4 shadow-[0_2px_15px_rgba(0,0,0,0.03)] dark:border-slate-800 dark:bg-slate-900 dark:shadow-[0_12px_36px_rgba(2,6,23,0.28)]">
    <!-- User Header -->
    <div class="flex items-center space-x-3 mb-3">
      <div class="h-10 w-10 overflow-hidden rounded-full bg-slate-100 dark:bg-slate-800">
        <img v-if="userAvatar" :src="userAvatar" class="w-full h-full object-cover" />
        <div v-else class="flex h-full w-full items-center justify-center bg-gradient-to-br from-blue-400 to-indigo-400 text-sm font-black text-white">
          {{ getInitial(user) }}
        </div>
      </div>
      <div>
        <div class="text-sm font-bold text-slate-800 dark:text-slate-100">{{ user }}</div>
        <div class="text-[10px] text-slate-400 dark:text-slate-500">{{ time }}</div>
      </div>
    </div>

    <!-- Question Content -->
    <p class="mb-3 rounded-lg text-sm leading-relaxed text-slate-700 dark:text-slate-300">{{ content }}</p>

    <!-- Images Grid -->
    <div v-if="images && images.length" class="grid gap-2 mb-4" :class="images.length === 1 ? 'grid-cols-1' : 'grid-cols-3'">
      <div 
        v-for="(img, idx) in images" 
        :key="idx" 
        class="relative cursor-pointer overflow-hidden rounded-xl bg-slate-100 dark:bg-slate-800"
        :class="images.length === 1 ? 'h-48' : 'h-24 aspect-square'"
      >
         <img v-if="img" :src="img" @click.stop="previewImage = img" class="w-full h-full object-cover absolute inset-0 z-10 hover:scale-105 transition-transform duration-300" />
         <!-- Placeholder color if no real src -->
         <div class="absolute inset-0 flex items-center justify-center bg-slate-200 text-slate-300 dark:bg-slate-700 dark:text-slate-500">
            🖼️
          </div>
      </div>
    </div>

    <!-- Expert Reply -->
    <div v-if="localExpertReply" class="group relative mb-4 rounded-xl border border-green-100 bg-green-50/50 p-3 dark:border-emerald-500/20 dark:bg-emerald-500/10">
      <div class="absolute -top-1.5 left-4 h-3 w-3 rotate-45 border-l border-t border-green-100 bg-green-50 transform dark:border-emerald-500/20 dark:bg-emerald-500/10"></div>
      
      <div class="flex items-center space-x-2 mb-1 pr-6">
        <div class="px-1.5 py-0.5 bg-green-500 text-white text-[10px] font-bold rounded-md flex items-center">
            <span class="mr-1">🎓</span> 
            <span>专家回答</span>
        </div>
        <span class="text-xs font-bold text-slate-700 dark:text-slate-200">{{ localExpertReply.expertName }}</span>
        
        <button v-if="expertId === currentUserId" @click.stop="deleteExpertReplyObj" class="absolute top-2 right-2 flex h-6 w-6 items-center justify-center rounded text-red-400 opacity-0 transition-colors hover:bg-red-200/50 hover:text-red-500 group-hover:opacity-100 dark:hover:bg-red-500/20" title="撤销解答">
            <span class="text-[12px]">🗑️</span>
        </button>
      </div>
      <p class="text-xs leading-relaxed text-slate-600 dark:text-slate-300">
        {{ localExpertReply.content }}
      </p>
    </div>

    <!-- Actions Bar -->
    <div class="mt-2 flex items-center justify-between border-t border-slate-50 pt-3 text-slate-400 dark:border-slate-800 dark:text-slate-500">
      <div class="flex space-x-4">
        <button v-if="userId === currentUserId" @click.stop="$emit('delete')" class="flex items-center space-x-1.5 text-red-400/80 transition-colors hover:text-red-500">
           <span class="text-lg">🗑️</span>
           <span class="text-xs font-medium">删除</span>
        </button>
        <button @click.stop class="flex items-center space-x-1.5 transition-colors hover:text-green-500">
          <span class="text-lg">↗️</span>
          <span class="text-xs font-medium">分享</span>
        </button>
      </div>
      <div class="flex space-x-6">
        <button @click.stop="showComments = !showComments" class="flex items-center space-x-1.5 hover:text-green-500 transition-colors" :class="{'text-green-500': showComments}">
          <span class="text-lg">💬</span>
          <span class="text-xs font-medium">{{ comments.length || '评论' }}</span>
        </button>
        <button @click.stop="toggleLike" class="flex items-center space-x-1.5 transition-colors group">
          <span class="text-xl transition-transform duration-300 group-active:scale-75" :class="isLiked ? 'grayscale-0 scale-110 drop-shadow-sm' : 'grayscale opacity-60 hover:scale-110'">👍</span>
          <span class="text-xs font-medium" :class="{'text-amber-500': isLiked}">{{ localLikes || '赞' }}</span>
        </button>
      </div>
    </div>

    <!-- Comments Section -->
    <div v-if="showComments" class="animate-fade-in relative z-20 mt-4 border-t border-slate-100 pt-3 dark:border-slate-800">
      <div class="space-y-3 mb-3 max-h-48 overflow-y-auto pr-2 scrollbar-thin scrollbar-thumb-slate-200">
        <div v-if="comments.length === 0" class="py-2 text-center text-xs text-slate-400 dark:text-slate-500">暂无评论，来做第一个发言的人吧！</div>
        <div v-for="(comment, index) in comments" :key="index" class="flex space-x-2 relative group pr-6">
           <div class="h-6 w-6 flex-shrink-0 overflow-hidden rounded-full bg-slate-200 dark:bg-slate-700">
              <img v-if="comment.userAvatar" :src="comment.userAvatar" class="h-full w-full object-cover" />
              <div v-else class="flex h-full w-full items-center justify-center bg-gradient-to-br from-sky-400 to-indigo-500 text-[10px] font-bold text-white">
                {{ getInitial(comment.user) }}
              </div>
           </div>
           <div>
              <div class="flex items-baseline space-x-2">
                 <span class="text-xs font-bold text-slate-700 dark:text-slate-200">{{ comment.user }}</span>
                 <span class="text-[10px] text-slate-400 dark:text-slate-500">{{ comment.time }}</span>
              </div>
              <p class="mt-0.5 text-xs text-slate-600 dark:text-slate-300">{{ comment.content }}</p>
            </div>
            <button v-if="comment.userId === currentUserId" @click.stop="deleteCommentObj(comment.commentId || comment.id, index)" class="absolute top-0 right-0 flex h-6 w-6 items-center justify-center rounded text-red-400 opacity-0 transition-colors hover:bg-red-50 hover:text-red-500 group-hover:opacity-100 dark:hover:bg-red-500/20" title="删除评论">
              <span class="text-[10px]">🗑️</span>
            </button>
         </div>
      </div>
      
      <!-- Comment Input -->
      <div class="flex space-x-2" @click.stop>
         <input 
           v-model="newComment" 
           @keyup.enter="submitComment"
           type="text" 
           placeholder="写下你的评论..." 
           class="flex-1 rounded-full border border-slate-100 bg-slate-50 px-4 py-2 text-xs transition-all focus:border-green-300 focus:outline-none focus:ring-1 focus:ring-green-300 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100 dark:focus:border-emerald-400 dark:focus:ring-emerald-400/60"
          />
         <button 
           @click.stop="submitComment"
           class="w-8 h-8 rounded-full bg-green-500 text-white flex items-center justify-center font-bold active:scale-90 transition-transform disabled:opacity-50 disabled:active:scale-100"
           :disabled="!newComment.trim()"
         >
           ↑
         </button>
      </div>
    </div>

    <!-- Image Preview Modal -->
    <Teleport to="body">
      <div v-if="previewImage" class="fixed inset-0 z-[100] flex items-center justify-center bg-black/95 backdrop-blur-sm animate-fade-in" @click="previewImage = null">
        <button class="absolute top-4 right-4 text-white/50 hover:text-white transition-colors w-10 h-10 flex items-center justify-center text-3xl font-bold z-10 active:scale-90">✕</button>
        <img :src="previewImage" class="max-w-full max-h-full object-contain p-4 select-none" @click.stop />
      </div>
    </Teleport>
  </div>
</template>
