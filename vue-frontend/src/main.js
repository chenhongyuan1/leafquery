import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { MotionPlugin } from '@vueuse/motion'
import router from './router'
import './style.css'
import App from './App.vue'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(MotionPlugin)

app.mount('#app')

import axios from 'axios'
// 如果是开发环境 (npm run dev)，这里是 "/"，继续走 Vite 代理；
// 如果是生产环境 (npm run build 部署后)，这里就会变成您的云端真实域名。
axios.defaults.baseURL = import.meta.env.VITE_API_BASE_URL || ''
