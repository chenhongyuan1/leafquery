import http from './http.js'

// These endpoints return domain JSON or audio rather than code/message/data.
export const diagnosisApi = {
  detect: (formData, config = {}) => http.post('/api/pest/detect', formData, { timeout: 30000, ...config }),
  identify: (formData, config = {}) => http.post('/api/pest/identify', formData, { timeout: 120000, ...config }),
  diagnose: (payload, config = {}) => http.post('/api/pest/diagnose', payload, { timeout: 300000, ...config }),
  chat: payload => http.post('/api/ai/chat', payload, { timeout: 300000 }),
  textToSpeech: (payload, config = {}) => http.post('/api/ai/text-to-speech', payload, { timeout: 120000, responseType: 'blob', ...config }),
  speechToText: (formData, config = {}) => http.post('/api/ai/speech-to-text', formData, { timeout: 120000, ...config })
}
