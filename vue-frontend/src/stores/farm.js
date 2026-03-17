import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useFarmStore = defineStore('farm', () => {
    // 可选作物库 (V1 竞赛版聚焦三大主粮)
    const cropLibrary = [
        { name: '水稻', icon: '🌾', diseases: ['稻瘟病', '纹枯病'] },
        { name: '玉米', icon: '🌽', diseases: ['大斑病', '锈病'] },
        { name: '小麦', icon: '🌾', diseases: ['白粉病', '条锈病'] },
    ]

    // 用户农场作物
    const crops = ref([])
    const activeCropId = ref(null)

    // 识别历史（从 Identification 页面积累）
    const identificationHistory = ref([])

    // 初始化：从 localStorage 恢复
    const init = () => {
        try {
            const saved = localStorage.getItem('farm_data')
            if (saved) {
                const data = JSON.parse(saved)
                crops.value = data.crops || []
                activeCropId.value = data.activeCropId || null
                identificationHistory.value = data.identificationHistory || []
            }
        } catch (e) {
            console.error('Failed to load farm data:', e)
        }
    }

    // 持久化
    const save = () => {
        localStorage.setItem('farm_data', JSON.stringify({
            crops: crops.value,
            activeCropId: activeCropId.value,
            identificationHistory: identificationHistory.value
        }))
    }

    // 添加作物
    const addCrop = (cropName, locationData) => {
        const lib = cropLibrary.find(c => c.name === cropName)
        if (!lib) return false

        // 避免重复
        if (crops.value.some(c => c.name === cropName)) return false

        const newCrop = {
            id: Date.now(),
            name: lib.name,
            icon: lib.icon,
            // 默认设置为第一个物候期（可以后续在预测页修改），此字段暂时留作展示用
            stage: '拔节期', 
            city: locationData.city,           // 城市名称，如 "海淀"
            region: locationData.region,       // 农业生态区，如 "华北区"
            locationId: locationData.id,       // 城市ID，给和风天气用的
            province: locationData.province,   // 省份
            diseases: lib.diseases,
            addedAt: new Date().toISOString()
        }
        crops.value.push(newCrop)

        // 如果是第一个，默认选中
        if (crops.value.length === 1) {
            activeCropId.value = newCrop.id
        }
        save()
        return true
    }

    // 删除作物
    const removeCrop = (cropId) => {
        crops.value = crops.value.filter(c => c.id !== cropId)
        if (activeCropId.value === cropId) {
            activeCropId.value = crops.value.length > 0 ? crops.value[0].id : null
        }
        save()
    }

    // 更新作物信息
    const updateCrop = (cropId, updates) => {
        const crop = crops.value.find(c => c.id === cropId)
        if (crop) {
            Object.assign(crop, updates)
            save()
        }
    }

    // 设置当前选中作物
    const setActiveCrop = (cropId) => {
        activeCropId.value = cropId
        save()
    }

    // 添加识别记录
    const addIdentification = (record) => {
        identificationHistory.value.unshift({
            id: Date.now(),
            pestName: record.pestName,
            confidence: record.confidence,
            time: new Date().toISOString(),
            cropId: record.cropId || activeCropId.value,
            imageUrl: record.imageUrl || ''
        })
        // 最多保留 50 条
        if (identificationHistory.value.length > 50) {
            identificationHistory.value = identificationHistory.value.slice(0, 50)
        }
        save()
    }

    // 计算属性
    const activeCrop = computed(() => {
        return crops.value.find(c => c.id === activeCropId.value) || null
    })

    // 获取指定作物的识别历史
    const getHistoryByCrop = (cropId) => {
        return identificationHistory.value.filter(h => h.cropId === cropId)
    }

    // 获取指定作物的高频病害统计
    const getTopDiseases = (cropId) => {
        const history = getHistoryByCrop(cropId)
        const counts = {}
        history.forEach(h => {
            counts[h.pestName] = (counts[h.pestName] || 0) + 1
        })
        return Object.entries(counts)
            .sort((a, b) => b[1] - a[1])
            .slice(0, 3)
            .map(([name, count]) => ({ name, count }))
    }

    init()

    return {
        cropLibrary,
        crops,
        activeCropId,
        activeCrop,
        identificationHistory,
        addCrop,
        removeCrop,
        updateCrop,
        setActiveCrop,
        addIdentification,
        getHistoryByCrop,
        getTopDiseases
    }
})
