import http from './http.js'

const id = value => encodeURIComponent(value)
export const discoveryApi = {
  news: () => http.get('/api/discovery/news'),
  plants: () => http.get('/api/discovery/plants'),
  knowledge: plantId => http.get('/api/discovery/knowledge', { params: plantId ? { plantId } : {} }),
  posts: () => http.get('/api/discovery/qna'),
  likedPosts: userId => http.get('/api/discovery/qna/liked', { params: { userId } }),
  createPost: payload => http.post('/api/discovery/qna', payload),
  removePost: postId => http.delete(`/api/discovery/qna/${id(postId)}`),
  likePost: (postId, payload) => http.post(`/api/discovery/qna/${id(postId)}/like`, payload),
  comment: (postId, payload) => http.post(`/api/discovery/qna/${id(postId)}/comment`, payload),
  removeComment: commentId => http.delete(`/api/discovery/qna/comment/${id(commentId)}`),
  removeExpertReply: postId => http.delete(`/api/discovery/qna/${id(postId)}/expert-reply`),
  upload: (formData, config = {}) => http.post('/api/discovery/upload', formData, { timeout: 120000, ...config }),
  announcements: (userId, popup = false) => http.get(`/api/discovery/announcements${popup ? '/popup' : ''}`, { params: userId ? { userId } : {} }),
  markRead: (announcementId, payload) => http.post(`/api/discovery/announcements/${id(announcementId)}/read`, payload)
}
