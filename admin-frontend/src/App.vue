<script setup>
import { computed, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAdminStore } from './stores/admin'

const router = useRouter()
const route = useRoute()
const store = useAdminStore()

const isLoginPage = computed(() => route.path === '/login')

const menuItems = computed(() => {
  const items = [
    { path: '/', label: '仪表盘', icon: '📊' },
    { path: '/announcements', label: '公告管理', icon: '📢' },
    { path: '/users', label: '用户管理', icon: '👥' },
    { path: '/discovery', label: '发现管理', icon: '🔍' },
    { path: '/models', label: '模型管理', icon: '🤖' },
    { path: '/logs', label: '操作日志', icon: '📋' },
  ]
  if (store.isSuperAdmin) {
    items.splice(5, 0, { path: '/system', label: '系统管理', icon: '⚙️' })
  }
  return items
})

function logout() {
  store.logout()
  router.push('/login')
}
</script>

<template>
  <div v-if="isLoginPage">
    <router-view />
  </div>
  <div v-else class="admin-layout">
    <!-- Sidebar -->
    <aside class="sidebar">
      <div class="sidebar-logo">
        <div class="logo-icon">LQ</div>
        <h1>LeafQuery管理系统</h1>
      </div>
      <nav class="sidebar-nav">
        <div class="nav-section">功能菜单</div>
        <router-link
          v-for="item in menuItems"
          :key="item.path"
          :to="item.path"
          custom
          v-slot="{ isActive, navigate }"
        >
          <button 
            class="nav-item"
            :class="{ active: isActive || (item.path === '/' && route.path === '/') }"
            @click="navigate"
          >
            <span class="icon">{{ item.icon }}</span>
            {{ item.label }}
          </button>
        </router-link>
      </nav>
      <div class="sidebar-footer">
        <div class="admin-info">
          <div class="avatar">{{ store.nickname?.charAt(0) || 'A' }}</div>
          <div>
            <div class="name">{{ store.nickname }}</div>
            <div class="role">{{ store.isSuperAdmin ? '系统管理员' : '普通管理员' }}</div>
          </div>
        </div>
        <button class="btn-logout" @click="logout">退出登录</button>
      </div>
    </aside>

    <!-- Main Content -->
    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>
