import http from './http.js'

export const farmApi = {
  list: userId => http.get('/api/farm/crops', { params: { userId } }),
  create: payload => http.post('/api/farm/crops', payload),
  update: (cropId, payload) => http.put(`/api/farm/crops/${encodeURIComponent(cropId)}`, payload),
  remove: (cropId, userId) => http.delete(`/api/farm/crops/${encodeURIComponent(cropId)}`, { params: { userId } }),
  activate: (cropId, payload) => http.put(`/api/farm/crops/${encodeURIComponent(cropId)}/active`, payload),
  estimate: payload => http.post('/api/farm/phenology/estimate', payload),
  searchLocations: query => http.get('/api/location/search', { params: { query } })
}
