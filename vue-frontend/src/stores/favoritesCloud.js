import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import axios from 'axios'
import { getStoredUser } from '../utils/accountSecurity'

const buildFavoriteKey = (itemType, itemId) => `${itemType}_${itemId}`

const normalizeFavoritePayload = (item) => {
  if (!item) return null

  const itemType = item.itemType || item.type
  const rawItemId = item.itemId ?? item.id
  if (!itemType || rawItemId === undefined || rawItemId === null || rawItemId === '') {
    return null
  }

  const itemId = String(rawItemId)

  return {
    itemType,
    itemId,
    title: item.title || item.name || (item.content ? item.content.substring(0, 20) : ''),
    imageUrl: item.image || item.imageUrl || (Array.isArray(item.images) ? item.images[0] : ''),
    description: item.desc || item.description || ''
  }
}

const dedupeFavoriteItems = (items = []) => {
  const deduped = []
  const seenKeys = new Set()

  items.forEach((item) => {
    const normalized = normalizeFavoritePayload(item)
    if (!normalized) return

    const key = buildFavoriteKey(normalized.itemType, normalized.itemId)
    if (seenKeys.has(key)) return

    seenKeys.add(key)
    deduped.push({
      ...item,
      ...normalized
    })
  })

  return deduped
}

export const useFavoritesStore = defineStore('favorites', () => {
  // Use a reactive plain object instead of ref(new Set()) for proper Vue reactivity
  const favoritesMap = reactive({})
  const favoriteItems = ref([])
  const isFavoriteSubmitting = ref(false)

  const syncFavoritesState = (items = []) => {
    const dedupedItems = dedupeFavoriteItems(items)

    // Clear current map
    Object.keys(favoritesMap).forEach(k => delete favoritesMap[k])

    dedupedItems.forEach((item) => {
      favoritesMap[buildFavoriteKey(item.itemType, item.itemId)] = true
    })

    favoriteItems.value = dedupedItems
  }

  const isFavorite = (item) => {
    const normalized = normalizeFavoritePayload(item)
    if (!normalized) return false

    return !!favoritesMap[buildFavoriteKey(normalized.itemType, normalized.itemId)]
  }

  const loadFavorites = async (userId) => {
    if (!userId) {
      syncFavoritesState([])
      return
    }

    try {
      const response = await axios.get(`/api/favorite/list?userId=${userId}`)
      if (response.data?.code === 200) {
        syncFavoritesState(response.data.data || [])
      }
    } catch (error) {
      console.error('Failed to load favorites from cloud:', error)
    }
  }

  const toggleFavorite = async (item) => {
    const user = getStoredUser()
    const normalized = normalizeFavoritePayload(item)

    if (!user?.userId || !normalized || isFavoriteSubmitting.value) {
      return false
    }

    isFavoriteSubmitting.value = true

    // Optimistic UI update
    const key = buildFavoriteKey(normalized.itemType, normalized.itemId)
    const wasFavorited = !!favoritesMap[key]

    if (wasFavorited) {
      delete favoritesMap[key]
      favoriteItems.value = favoriteItems.value.filter(
        fi => buildFavoriteKey(fi.itemType, fi.itemId) !== key
      )
    } else {
      favoritesMap[key] = true
      favoriteItems.value.unshift({ ...normalized, createTime: new Date().toISOString() })
    }

    try {
      const response = await axios.post('/api/favorite/toggle', {
        userId: user.userId,
        ...normalized
      })

      if (response.data?.code !== 200) {
        throw new Error(response.data?.message || 'Favorite toggle failed')
      }

      // Trust the optimistic update on success — avoid full reload
      // which causes visual glitches with transition-group animations
      return !wasFavorited
    } catch (error) {
      console.error('Failed to sync favorite with cloud:', error)
      // Revert optimistic update on failure
      if (wasFavorited) {
        favoritesMap[key] = true
      } else {
        delete favoritesMap[key]
      }
      await loadFavorites(user.userId)
      return false
    } finally {
      isFavoriteSubmitting.value = false
    }
  }

  /**
   * Dedicated unfavorite action for the Favorites page.
   * Always removes the item (no toggle ambiguity).
   * The item is removed from local state immediately and deleted on the server.
   */
  const removeFavorite = async (item) => {
    const user = getStoredUser()
    if (!user?.userId || isFavoriteSubmitting.value) return false

    const itemType = item.itemType || item.type
    const itemId = String(item.itemId ?? item.id)
    if (!itemType || !itemId) return false

    const key = buildFavoriteKey(itemType, itemId)

    // Only proceed if the item is actually favorited
    if (!favoritesMap[key]) return false

    isFavoriteSubmitting.value = true

    // Optimistic removal
    delete favoritesMap[key]
    const previousItems = [...favoriteItems.value]
    favoriteItems.value = favoriteItems.value.filter(
      fi => buildFavoriteKey(fi.itemType, fi.itemId) !== key
    )

    try {
      const response = await axios.post('/api/favorite/toggle', {
        userId: user.userId,
        itemType,
        itemId,
        title: item.title || '',
        imageUrl: item.imageUrl || item.image || '',
        description: item.description || item.desc || ''
      })

      if (response.data?.code !== 200) {
        throw new Error(response.data?.message || 'Unfavorite failed')
      }

      return true
    } catch (error) {
      console.error('Failed to remove favorite:', error)
      // Revert on failure
      favoritesMap[key] = true
      favoriteItems.value = previousItems
      return false
    } finally {
      isFavoriteSubmitting.value = false
    }
  }

  return {
    favoritesMap,
    favoriteItems,
    isFavoriteSubmitting,
    toggleFavorite,
    removeFavorite,
    isFavorite,
    loadFavorites
  }
})
