import { createRouter, createWebHistory } from 'vue-router'
import Identification from '../views/Identification.vue'
import Discovery from '../views/Discovery.vue'
import Prediction from '../views/Prediction.vue'
import Profile from '../views/Profile.vue'
import About from '../views/About.vue'
import Records from '../views/Records.vue'

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
            component: () => import('../views/Favorites.vue'),
            meta: { hideTabBar: true }
        },
        {
            path: '/farm',
            name: 'farm',
            component: () => import('../views/Farm.vue'),
            meta: { hideTabBar: true }
        },
        {
            path: '/settings',
            name: 'settings',
            component: () => import('../views/Settings.vue'),
            meta: { hideTabBar: true }
        },
        {
            path: '/notifications',
            name: 'notifications',
            component: () => import('../views/Notifications.vue'),
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
