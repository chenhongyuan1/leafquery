import http from './http.js'

export const recordApi = {
  list: userId => http.get('/api/record/list', { params: { userId } }),
  add: payload => http.post('/api/record/add', payload),
  remove: recordId => http.delete(`/api/record/${encodeURIComponent(recordId)}`),
  clear: userId => http.delete('/api/record/clear', { params: { userId } })
}
