import http from './http.js'

export const recordApi = {
  list: userId => http.get('/api/record/list', { params: { userId } }),
  add: (payload, config = {}) => http.post('/api/record/add', payload, config),
  remove: (recordId, config = {}) => http.delete(`/api/record/${encodeURIComponent(recordId)}`, config),
  clear: userId => http.delete('/api/record/clear', { params: { userId } })
}
