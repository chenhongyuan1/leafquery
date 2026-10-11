import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '../api.js'

export const useAdminStore = defineStore('admin', () => {
  const admin = ref(null)

  // 旧的本地角色信息不再代表登录；令牌与展示信息只保存在当前标签页。
  localStorage.removeItem('admin')
  const saved = sessionStorage.getItem('admin')
  if (saved && sessionStorage.getItem('admin_token')) {
    try { admin.value = JSON.parse(saved) } catch (e) { /* ignore */ }
  }

  const isLoggedIn = computed(() => !!admin.value)
  const isSuperAdmin = computed(() => admin.value?.role === 'super_admin')
  const adminId = computed(() => admin.value?.adminId)
  const nickname = computed(() => admin.value?.nickname || admin.value?.username || '')

  function login(data, token) {
    admin.value = data
    sessionStorage.setItem('admin', JSON.stringify(data))
    sessionStorage.setItem('admin_token', token)
  }

  async function logout() {
    try {
      await api.post('/logout')
    } finally {
      admin.value = null
      sessionStorage.removeItem('admin')
      sessionStorage.removeItem('admin_token')
      localStorage.removeItem('admin')
    }
  }

  return { admin, isLoggedIn, isSuperAdmin, adminId, nickname, login, logout }
})
