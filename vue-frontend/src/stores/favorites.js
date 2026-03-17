import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'

export const useFavoritesStore = defineStore('favorites', () => {
    // Format: "type_id" e.g., "news_1", "library_201"
    const favorites = ref(new Set())
    // 存储完整的云端实体快照
    const favoriteItems = ref([])

    const toggleFavorite = async (item) => {
        const key = `${item.type}_${item.id}`
        const isFav = favorites.value.has(key)

        // Optimistic UI update
        if (isFav) {
            favorites.value.delete(key)
            favoriteItems.value = favoriteItems.value.filter(fav => `${fav.itemType}_${fav.itemId}` !== key)
        } else {
            favorites.value.add(key)
            // 临时添加前端表示
            favoriteItems.value.unshift({
                itemType: item.type,
                itemId: String(item.id),
                title: item.title || item.name || (item.content ? item.content.substring(0, 20) : ''),
                imageUrl: item.image || item.imageUrl || (item.images ? item.images[0] : ''),
                description: item.desc || item.description || ''
            })
        }

        // Try to sync with server if logged in
        try {
            const userStr = localStorage.getItem('user')
            if (userStr) {
                const user = JSON.parse(userStr)
                if (user && user.userId) {
                    await axios.post('/api/favorite/toggle', {
                        userId: user.userId,
                        itemType: item.type,
                        itemId: String(item.id),
                        title: item.title || item.name || (item.content ? item.content.substring(0, 20) : ''),
                        imageUrl: item.image || item.imageUrl || (item.images ? item.images[0] : ''),
                        description: item.desc || item.description || ''
                    })
                }
            }
        } catch (e) {
            console.error('Failed to sync favorite with cloud:', e)
            // Revert on failure (simplified)
            if (isFav) { favorites.value.add(key) } else { favorites.value.delete(key) }
        }
    }

    const isFavorite = (item) => {
        if (!item) return false
        const key = `${item.type}_${item.id}`
        return favorites.value.has(key)
    }

    const loadFavorites = async (userId) => {
        if (!userId) {
            favorites.value.clear()
            favoriteItems.value = []
            return
        }
        try {
            const response = await axios.get(`/api/favorite/list?userId=${userId}`)
            if (response.data && response.data.code === 200) {
                const data = response.data.data || []
                const newSet = new Set()
                data.forEach(fav => {
                    newSet.add(`${fav.itemType}_${fav.itemId}`)
                })
                favorites.value = newSet
                favoriteItems.value = data
            }
        } catch (e) {
            console.error('Failed to load favorites from cloud:', e)
        }
    }

    return { favorites, favoriteItems, toggleFavorite, isFavorite, loadFavorites }
})
