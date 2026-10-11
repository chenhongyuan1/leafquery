import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/Login.vue'),
      meta: { guest: true }
    },
    {
      path: '/',
      name: 'dashboard',
      component: () => import('../views/Dashboard.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/announcements',
      name: 'announcements',
      component: () => import('../views/Announcements.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/users',
      name: 'users',
      component: () => import('../views/Users.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/discovery',
      name: 'discovery',
      component: () => import('../views/DiscoveryMgmt.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/system',
      name: 'system',
      component: () => import('../views/SystemManagementView.vue'),
      meta: { requiresAuth: true, superOnly: true }
    },
    {
      path: '/models',
      name: 'models',
      component: () => import('../views/ModelManagementView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/logs',
      name: 'logs',
      component: () => import('../views/Logs.vue'),
      meta: { requiresAuth: true }
    }
  ]
})

router.beforeEach((to, from, next) => {
  const token = sessionStorage.getItem('admin_token')
  let admin = null
  try { admin = JSON.parse(sessionStorage.getItem('admin') || 'null') } catch { /* invalid display data */ }
  const loggedIn = !!token && !!admin
  if (to.meta.requiresAuth && !loggedIn) {
    next('/login')
  } else if (to.meta.superOnly && admin?.role !== 'super_admin') {
    next('/')
  } else if (to.meta.guest && loggedIn) {
    next('/')
  } else {
    next()
  }
})

export default router
