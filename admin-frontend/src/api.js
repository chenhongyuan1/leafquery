import axios from 'axios'

const api = axios.create({
  baseURL: 'http://localhost:8080/api/admin',
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截器: 自动附带管理员角色
api.interceptors.request.use(config => {
  const adminStr = localStorage.getItem('admin')
  if (adminStr) {
    try {
      const admin = JSON.parse(adminStr)
      config.headers['X-Admin-Role'] = admin.role || ''
      config.headers['X-Admin-Id'] = admin.adminId || ''
    } catch (e) { /* ignore */ }
  }
  return config
})

export default api
