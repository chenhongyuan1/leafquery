import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export const useSettingsStore = defineStore('settings', () => {
    // 默认方言设为普通话
    const selectedDialect = ref(localStorage.getItem('leafquery_dialect') || 'mandarin')

    // 监听改变自动持久化
    watch(selectedDialect, (newVal) => {
        localStorage.setItem('leafquery_dialect', newVal)
    })

    return { selectedDialect }
})
