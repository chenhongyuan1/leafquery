import http from './http.js'

export const trendApi = {
  targets: crop => http.get('/api/trend/targets', { params: { crop } }),
  forecast: payload => http.post('/api/trend/forecast', payload, { timeout: 60000 }),
  weatherFeatures: locationId => http.get('/api/weather/features', { params: { locationId } })
}
