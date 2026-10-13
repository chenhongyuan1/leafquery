import http from './http.js'

export const userApi = {
  login: payload => http.post('/api/user/login', payload),
  register: payload => http.post('/api/user/register', payload),
  updateUsername: payload => http.put('/api/user/update-username', payload),
  changePassword: payload => http.put('/api/user/password', payload),
  deleteAccount: payload => http.post('/api/user/delete-account', payload)
}
