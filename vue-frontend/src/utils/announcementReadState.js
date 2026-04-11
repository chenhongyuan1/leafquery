import axios from 'axios'
import { getStoredUser } from './accountSecurity'

const READ_STORAGE_KEY = 'read_announcements'
const DISMISSED_POPUP_STORAGE_KEY = 'dismissed_popups'

const normalizeIds = (value) => {
  if (!Array.isArray(value)) {
    return []
  }

  return [...new Set(value
    .map(item => Number(item))
    .filter(item => Number.isFinite(item)))]
}

const readIdList = (storageKey) => {
  try {
    return normalizeIds(JSON.parse(localStorage.getItem(storageKey) || '[]'))
  } catch (error) {
    console.error(`Failed to parse ${storageKey}`, error)
    return []
  }
}

const writeIdList = (storageKey, ids) => {
  const normalized = normalizeIds(ids)
  localStorage.setItem(storageKey, JSON.stringify(normalized))
  return normalized
}

export const getLocalAnnouncementReadIds = () => readIdList(READ_STORAGE_KEY)

export const getLocalDismissedPopupIds = () => readIdList(DISMISSED_POPUP_STORAGE_KEY)

export const fetchAnnouncements = async (path = '/api/discovery/announcements') => {
  const user = getStoredUser()
  const params = user?.userId ? { userId: user.userId } : {}
  const { data } = await axios.get(path, { params })

  if (data?.code !== 200) {
    throw new Error(data?.message || '加载公告失败')
  }

  return Array.isArray(data.data) ? data.data : []
}

export const isAnnouncementRead = (announcement, localReadIds = []) => {
  const user = getStoredUser()
  if (user?.userId) {
    return Boolean(announcement?.read)
  }

  return normalizeIds(localReadIds).includes(Number(announcement?.id))
}

export const markAnnouncementAsRead = async (announcementId) => {
  const id = Number(announcementId)
  if (!Number.isFinite(id)) {
    return []
  }

  const user = getStoredUser()
  if (user?.userId) {
    const { data } = await axios.post(`/api/discovery/announcements/${id}/read`, {
      userId: user.userId
    })

    if (data?.code !== 200) {
      throw new Error(data?.message || '标记通知已读失败')
    }
    return []
  }

  const nextReadIds = writeIdList(READ_STORAGE_KEY, [...getLocalAnnouncementReadIds(), id])
  writeIdList(DISMISSED_POPUP_STORAGE_KEY, [...getLocalDismissedPopupIds(), id])
  return nextReadIds
}

export const dismissPopupAnnouncement = async (announcementId) => {
  const id = Number(announcementId)
  if (!Number.isFinite(id)) {
    return []
  }

  const user = getStoredUser()
  if (user?.userId) {
    await markAnnouncementAsRead(id)
    return []
  }

  return writeIdList(DISMISSED_POPUP_STORAGE_KEY, [...getLocalDismissedPopupIds(), id])
}
