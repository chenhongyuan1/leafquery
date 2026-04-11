import axios from 'axios'

const USER_STORAGE_KEY = 'user'
export const AUTH_CHANGE_EVENT = 'leafquery-auth-changed'

const emitAuthChange = (user) => {
  if (typeof window === 'undefined' || typeof window.dispatchEvent !== 'function') {
    return
  }

  window.dispatchEvent(new CustomEvent(AUTH_CHANGE_EVENT, {
    detail: { user }
  }))
}

export const getStoredUser = () => {
  try {
    const raw = localStorage.getItem(USER_STORAGE_KEY)
    return raw ? JSON.parse(raw) : null
  } catch (error) {
    console.error('Failed to parse stored user', error)
    return null
  }
}

export const requireLoggedInUser = (message = '请先登录后再继续操作。') => {
  const user = getStoredUser()
  if (user?.userId) {
    return user
  }

  if (typeof window !== 'undefined' && typeof window.alert === 'function') {
    window.alert(message)
  }
  return null
}

export const persistUser = (user) => {
  if (!user) {
    localStorage.removeItem(USER_STORAGE_KEY)
    emitAuthChange(null)
    return null
  }

  localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user))
  emitAuthChange(user)
  return user
}

export const getAccountErrorMessage = (error, fallback = '操作失败，请稍后重试') => {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.message || fallback
  }

  if (error instanceof Error && error.message) {
    return error.message
  }

  return fallback
}

export const changePassword = async ({ userId, currentPassword, newPassword }) => {
  const response = await axios.put('/api/user/password', {
    userId,
    currentPassword,
    newPassword
  })

  if (response.data?.code !== 200) {
    throw new Error(response.data?.message || '密码修改失败')
  }

  const user = response.data?.data || null
  if (user) {
    persistUser(user)
  }
  return user
}

export const deleteAccount = async ({ userId, currentPassword }) => {
  const response = await axios.post('/api/user/delete-account', {
    userId,
    currentPassword
  })

  if (response.data?.code !== 200) {
    throw new Error(response.data?.message || '账号注销失败')
  }

  return true
}

export const clearUserSession = async ({ farmStore, favoritesStore } = {}) => {
  persistUser(null)
  localStorage.setItem('selected_tab', 'home')

  farmStore?.resetToLocal?.()

  if (favoritesStore?.loadFavorites) {
    await favoritesStore.loadFavorites()
  }
}
