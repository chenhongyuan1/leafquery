import { createRouter, createWebHistory } from 'vue-router'
import Identification from '../views/mobile/Identification.vue'
import Discovery from '../views/mobile/Discovery.vue'
import Prediction from '../views/mobile/Prediction.vue'
import Profile from '../views/mobile/Profile.vue'
import About from '../views/mobile/About.vue'
import Records from '../views/mobile/Records.vue'

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/',
            name: 'home',
            component: Identification
        },
        {
            path: '/discovery',
            name: 'discovery',
            component: Discovery
        },
        {
            path: '/prediction',
            name: 'prediction',
            component: Prediction
        },
        {
            path: '/profile',
            name: 'profile',
            component: Profile
        },
        {
            path: '/about',
            name: 'about',
            component: About,
            meta: { hideTabBar: true }
        },
        {
            path: '/records',
            name: 'records',
            component: Records,
            meta: { hideTabBar: true }
        },
        {
            path: '/favorites',
            name: 'favorites',
            component: () => import('../views/mobile/Favorites.vue'),
            meta: { hideTabBar: true }
        },
        {
            path: '/farm',
            name: 'farm',
            component: () => import('../views/mobile/Farm.vue'),
            meta: { hideTabBar: true }
        },
        {
            path: '/settings',
            name: 'settings',
            component: () => import('../views/mobile/Settings.vue'),
            meta: { hideTabBar: true }
        },
        {
            path: '/notifications',
            name: 'notifications',
            component: () => import('../views/mobile/Notifications.vue'),
            meta: { hideTabBar: true }
        }
    ],

    scrollBehavior(to, from, savedPosition) {
        if (savedPosition) {
            return savedPosition
        } else {
            return { top: 0 }
        }
    }
})

export default router
