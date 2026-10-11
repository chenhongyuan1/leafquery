import axios from 'axios'

const environment = import.meta.env || {}
const api = axios.create({
  baseURL: (environment.VITE_API_BASE_URL || 'http://localhost:8080') + '/api/admin',
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' }
})

// 仅传递服务端签发的令牌；角色和管理员 ID 由服务端查询。
api.interceptors.request.use(config => {
  const token = sessionStorage.getItem('admin_token')
  if (token && config.url !== '/login') {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(response => response, error => {
  if (error.response?.status === 401 && error.config?.url !== '/login') {
    sessionStorage.removeItem('admin_token')
    sessionStorage.removeItem('admin')
    localStorage.removeItem('admin')
    const loginPath = `${environment.BASE_URL || '/'}login`
    if (window.location.pathname !== loginPath) window.location.assign(loginPath)
  }
  return Promise.reject(error)
})

export default api
