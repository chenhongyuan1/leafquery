import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useAdminStore = defineStore('admin', () => {
  const admin = ref(null)

  // 初始化时从 localStorage 恢复
  const saved = localStorage.getItem('admin')
  if (saved) {
    try { admin.value = JSON.parse(saved) } catch (e) { /* ignore */ }
  }

  const isLoggedIn = computed(() => !!admin.value)
  const isSuperAdmin = computed(() => admin.value?.role === 'super_admin')
  const adminId = computed(() => admin.value?.adminId)
  const nickname = computed(() => admin.value?.nickname || admin.value?.username || '')

  function login(data) {
    admin.value = data
    localStorage.setItem('admin', JSON.stringify(data))
  }

  function logout() {
    admin.value = null
    localStorage.removeItem('admin')
  }

  return { admin, isLoggedIn, isSuperAdmin, adminId, nickname, login, logout }
})
