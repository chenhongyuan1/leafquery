import http from './http.js'

export const favoriteApi = {
  list: userId => http.get('/api/favorite/list', { params: { userId } }),
  toggle: payload => http.post('/api/favorite/toggle', payload)
}
