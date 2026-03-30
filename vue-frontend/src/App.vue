<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import MobileLayout from './layouts/MobileLayout.vue'
import PCLayout from './layouts/PCLayout.vue'
import { useFavoritesStore } from './stores/favorites'
import { useFarmStore } from './stores/farmCloud'

const isMobile = ref(true)

const checkDevice = () => {
    // Check width or user agent. 
    // Usually mobile layout is wanted for anything narrower than a tablet/small laptop
    isMobile.value = window.innerWidth < 1024
}

onMounted(() => {
    checkDevice()
    window.addEventListener('resize', checkDevice)

    // Apply mobile app theme
    const savedTheme = localStorage.getItem('app-theme')
    if (savedTheme === 'dark') {
      document.documentElement.classList.add('dark')
    }

    const farmStore = useFarmStore()

    // Load favorites and farm data if user is already logged in
    const userStr = localStorage.getItem('user')
    if (userStr) {
        try {
            const user = JSON.parse(userStr)
            if (user && user.userId) {
                const favStore = useFavoritesStore()
                favStore.loadFavorites(user.userId)
            }
        } catch (e) {
            console.error('Failed to parse user on App mount:', e)
        }
    }

    farmStore.initialize()
})

onUnmounted(() => {
    window.removeEventListener('resize', checkDevice)
})
</script>

<template>
  <div class="w-full h-full bg-slate-900">
    <component :is="isMobile ? MobileLayout : PCLayout" />
  </div>
</template>
