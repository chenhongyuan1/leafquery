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
      component: () => import('../views/SystemMgmt.vue'),
      meta: { requiresAuth: true, superOnly: true }
    },
    {
      path: '/models',
      name: 'models',
      component: () => import('../views/ModelMgmt.vue'),
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
  const admin = localStorage.getItem('admin')
  if (to.meta.requiresAuth && !admin) {
    next('/login')
  } else if (to.meta.guest && admin) {
    next('/')
  } else {
    next()
  }
})

export default router
