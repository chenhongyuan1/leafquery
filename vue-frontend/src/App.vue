<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import MobileLayout from './layouts/MobileLayout.vue'
import PCLayout from './layouts/PCLayout.vue'
import { useFavoritesStore } from './stores/favoritesCloud'
import { useFarmStore } from './stores/farmCloud'
import { AUTH_CHANGE_EVENT, getStoredUser } from './utils/accountSecurity'
import { syncThemePreference } from './utils/themePreference'

const getIsMobileViewport = () => {
  if (typeof window === 'undefined') return true
  return window.innerWidth < 1024
}

const isMobile = ref(getIsMobileViewport())
const favStore = useFavoritesStore()
const farmStore = useFarmStore()

const checkDevice = () => {
    // Check width or user agent.
    // Usually mobile layout is wanted for anything narrower than a tablet/small laptop
    const nextIsMobile = getIsMobileViewport()
    const layoutChanged = nextIsMobile !== isMobile.value
    isMobile.value = nextIsMobile
    return layoutChanged
}

const handleViewportChange = () => {
    checkDevice()
}

const syncFavoritesForCurrentUser = async () => {
    const user = getStoredUser()
    await favStore.loadFavorites(user?.userId)
}

const syncFarmForCurrentUser = async () => {
    await farmStore.initialize({ force: true })
}

const handleAuthChange = () => {
    syncFavoritesForCurrentUser()
    syncFarmForCurrentUser()
}

syncThemePreference(isMobile.value ? 'app-theme' : 'pc-theme')

onMounted(() => {
    handleViewportChange()
    window.addEventListener('resize', handleViewportChange)
    window.addEventListener(AUTH_CHANGE_EVENT, handleAuthChange)

    syncFavoritesForCurrentUser()
    syncFarmForCurrentUser()
})

onUnmounted(() => {
    window.removeEventListener('resize', handleViewportChange)
    window.removeEventListener(AUTH_CHANGE_EVENT, handleAuthChange)
})
</script>

<template>
  <div class="w-full h-full bg-slate-50 transition-colors dark:bg-slate-900">
    <MobileLayout v-if="isMobile" key="mobile-layout" />
    <PCLayout v-else key="pc-layout" />
  </div>
</template>
