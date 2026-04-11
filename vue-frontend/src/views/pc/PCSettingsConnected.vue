<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import axios from 'axios'
import { useFarmStore } from '../../stores/farmCloud'
import { useFavoritesStore } from '../../stores/favoritesCloud'
import PCFarmManager from '../../components/pc/PCFarmManager.vue'
import {
  AUTH_CHANGE_EVENT,
  changePassword,
  clearUserSession,
  deleteAccount,
  getStoredUser,
  getAccountErrorMessage,
  persistUser
} from '../../utils/accountSecurity'

const farmStore = useFarmStore()
const favoritesStore = useFavoritesStore()

const currentUser = ref(null)
const activeSection = ref('overview')
const loading = ref(true)
const historyRecords = ref([])
const isDark = ref(document.documentElement.classList.contains('dark'))
const accountFeedbackMessage = ref('')
const accountFeedbackType = ref('success')
const passwordSaving = ref(false)
const deleteSubmitting = ref(false)
const passwordError = ref('')
const deleteError = ref('')
const passwordForm = ref({
  currentPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const deleteForm = ref({
  currentPassword: ''
})

// 修改用户名状态
const isEditingUsername = ref(false)
const editUsername = ref('')
const editUsernameLoading = ref(false)
const editUsernameError = ref('')

const startEditUsername = () => {
  editUsername.value = currentUser.value?.username || ''
  editUsernameError.value = ''
  isEditingUsername.value = true
}

const cancelEditUsername = () => {
  isEditingUsername.value = false
  editUsernameError.value = ''
}

const submitEditUsername = async () => {
  const newName = editUsername.value.trim()
  if (!newName) {
    editUsernameError.value = '用户名不能为空'
    return
  }
  if (newName === currentUser.value?.username) {
    isEditingUsername.value = false
    return
  }
  editUsernameLoading.value = true
  editUsernameError.value = ''
  try {
    const res = await axios.put('/api/user/update-username', {
      userId: currentUser.value.userId,
      username: newName
    })
    if (res.data.code === 200) {
      currentUser.value = res.data.data
      persistUser(res.data.data)
      isEditingUsername.value = false
      setAccountFeedback('用户名修改成功')
    } else {
      editUsernameError.value = res.data.message || '修改失败'
    }
  } catch (err) {
    editUsernameError.value = err.response?.data?.message || '网络错误，请稍后重试'
  } finally {
    editUsernameLoading.value = false
  }
}

let themeObserver = null

const DISEASE_SAFE_KEYWORDS = ['健康', 'healthy', 'unknown', '未发现', '暂无异常']

const setAccountFeedback = (message, type = 'success') => {
  accountFeedbackMessage.value = message
  accountFeedbackType.value = type
}

const resetPasswordForm = () => {
  passwordForm.value = {
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  }
  passwordError.value = ''
}

const resetDeleteForm = () => {
  deleteForm.value = {
    currentPassword: ''
  }
  deleteError.value = ''
}

const validatePasswordForm = () => {
  if (!passwordForm.value.currentPassword || !passwordForm.value.newPassword || !passwordForm.value.confirmPassword) {
    return '请完整填写密码信息'
  }
  if (passwordForm.value.newPassword.length < 6) {
    return '新密码长度不能少于6位'
  }
  if (passwordForm.value.currentPassword === passwordForm.value.newPassword) {
    return '新密码不能与当前密码相同'
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    return '两次输入的新密码不一致'
  }
  return ''
}

const formatDateTime = (value) => {
  if (!value) return '未知时间'

  let date = new Date(value)
  if (Number.isNaN(date.getTime()) && !Number.isNaN(Number(value))) {
    date = new Date(Number(value))
  }

  if (Number.isNaN(date.getTime())) {
    return String(value)
  }

  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

const formatConfidence = (value) => `${(Number(value || 0) * 100).toFixed(1)}%`

const isDiseaseResult = (name) => {
  if (!name) return false
  const normalized = String(name).toLowerCase()
  return !DISEASE_SAFE_KEYWORDS.some(keyword => normalized.includes(keyword.toLowerCase()))
}

const currentThemeLabel = computed(() => (isDark.value ? '深色模式' : '浅色模式'))

const favoriteCount = computed(() => favoritesStore.favoriteItems.length)
const cropCount = computed(() => farmStore.crops.length)
const coveredAreaCount = computed(() => {
  return new Set(
    farmStore.crops
      .map(crop => crop.region || crop.city || crop.province || crop.name)
      .filter(Boolean)
  ).size
})
const recordCount = computed(() => historyRecords.value.length)
const diseaseCount = computed(() => {
  return new Set(
    historyRecords.value
      .map(item => item.pestName)
      .filter(isDiseaseResult)
  ).size
})
const activeCropSummary = computed(() => {
  const crop = farmStore.activeCrop
  if (!crop) return '未设定当前作物'
  const place = [crop.province, crop.city].filter(Boolean).join(' / ') || crop.region || '未设定地区'
  return `${crop.name} · ${place}`
})


const getFavoriteTypeLabel = (type) => {
  switch (type) {
    case 'news':
      return '资讯'
    case 'library':
      return '图鉴'
    case 'qna':
      return '问答'
    default:
      return '内容'
  }
}

const getFavoriteTypeClass = (type) => {
  switch (type) {
    case 'news':
      return 'bg-sky-100 text-sky-700 dark:bg-sky-500/10 dark:text-sky-300'
    case 'library':
      return 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300'
    case 'qna':
      return 'bg-amber-100 text-amber-700 dark:bg-amber-500/10 dark:text-amber-300'
    default:
      return 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300'
  }
}

const getConfidenceClass = (confidence) => {
  const numeric = Number(confidence || 0)
  if (numeric >= 0.8) {
    return 'bg-emerald-100 text-emerald-700 dark:bg-emerald-500/10 dark:text-emerald-300'
  }
  if (numeric >= 0.55) {
    return 'bg-amber-100 text-amber-700 dark:bg-amber-500/10 dark:text-amber-300'
  }
  return 'bg-rose-100 text-rose-700 dark:bg-rose-500/10 dark:text-rose-300'
}

const normalizeRecord = (item) => ({
  id: item.id ?? `${item.pestName || 'record'}-${item.createTime || Date.now()}`,
  pestName: item.pestName || '未知结果',
  cropName: item.cropName || '',
  confidence: Number(item.confidence || 0),
  createTime: item.createTime || item.time || '',
  city: item.city || '',
  region: item.region || '',
  imageUrl: item.imageUrl || ''
})

const loadRecords = async () => {
  if (!currentUser.value?.userId) {
    historyRecords.value = (farmStore.identificationHistory || []).map(normalizeRecord)
    return
  }

  try {
    const response = await axios.get(`/api/record/list?userId=${currentUser.value.userId}`)
    if (response.data?.code === 200) {
      const records = response.data.data || []
      farmStore.syncIdentificationHistory(records)
      historyRecords.value = records.map(normalizeRecord)
      return
    }
  } catch (error) {
    console.error('Failed to load record list for PC settings', error)
  }

  historyRecords.value = (farmStore.identificationHistory || []).map(normalizeRecord)
}

const loadPageData = async () => {
  loading.value = true
  currentUser.value = getStoredUser()

  await farmStore.initialize({ force: true })

  await Promise.all([
    loadRecords(),
    favoritesStore.loadFavorites(currentUser.value?.userId)
  ])

  loading.value = false
}

const toggleFavorite = async (item) => {
  await favoritesStore.removeFavorite(item)
}

const submitPasswordChange = async () => {
  const validationMessage = validatePasswordForm()
  if (validationMessage) {
    passwordError.value = validationMessage
    return
  }

  if (!currentUser.value?.userId) {
    passwordError.value = '当前未登录，无法修改密码'
    return
  }

  passwordSaving.value = true
  passwordError.value = ''

  try {
    currentUser.value = await changePassword({
      userId: currentUser.value.userId,
      currentPassword: passwordForm.value.currentPassword,
      newPassword: passwordForm.value.newPassword
    })
    resetPasswordForm()
    setAccountFeedback('密码修改成功')
  } catch (error) {
    passwordError.value = getAccountErrorMessage(error, '密码修改失败，请稍后重试')
  } finally {
    passwordSaving.value = false
  }
}

const submitDeleteAccount = async () => {
  if (!deleteForm.value.currentPassword) {
    deleteError.value = '请输入当前密码以完成账号注销'
    return
  }

  if (!currentUser.value?.userId) {
    deleteError.value = '当前未登录，无法注销账号'
    return
  }

  deleteSubmitting.value = true
  deleteError.value = ''

  try {
    await deleteAccount({
      userId: currentUser.value.userId,
      currentPassword: deleteForm.value.currentPassword
    })
    await clearUserSession({
      farmStore,
      favoritesStore
    })
    activeSection.value = 'account-security'
    resetDeleteForm()
    setAccountFeedback('账号已注销，当前页面已切换到未登录状态')
    await loadPageData()
  } catch (error) {
    deleteError.value = getAccountErrorMessage(error, '账号注销失败，请稍后重试')
  } finally {
    deleteSubmitting.value = false
  }
}

const handleLogout = async () => {
  await clearUserSession({
    farmStore,
    favoritesStore
  })
  currentUser.value = null
  activeSection.value = 'overview'
  setAccountFeedback('已退出当前账户')
  await loadPageData()
}

onMounted(async () => {
  themeObserver = new MutationObserver(() => {
    isDark.value = document.documentElement.classList.contains('dark')
  })
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class']
  })
  window.addEventListener(AUTH_CHANGE_EVENT, loadPageData)

  await loadPageData()
})

onBeforeUnmount(() => {
  themeObserver?.disconnect()
  window.removeEventListener(AUTH_CHANGE_EVENT, loadPageData)
})
</script>

<template>
  <div class="flex h-full flex-col bg-slate-50/60 p-8 dark:bg-slate-950/40">
    <div class="mb-8 shrink-0">
      <h2 class="text-3xl font-black tracking-tight text-slate-900 dark:text-slate-100">系统设置</h2>
    </div>

    <div class="pc-settings-shell flex min-h-0 flex-1 flex-col gap-8 overflow-hidden rounded-[2rem] border border-white/60 bg-white/90 p-8 shadow-[0_24px_80px_rgba(15,23,42,0.08)] backdrop-blur-xl lg:flex-row dark:border-slate-800/80 dark:bg-slate-900/[0.92]">
      <aside class="flex w-full shrink-0 flex-col gap-2 border-b border-slate-100 pb-8 lg:w-72 lg:border-b-0 lg:border-r lg:pb-0 lg:pr-8 dark:border-slate-800">
        <button
          class="rounded-2xl px-5 py-3 text-left text-sm font-bold transition-colors"
          :class="activeSection === 'overview'
            ? 'bg-slate-950 text-white shadow-lg shadow-slate-950/10 dark:bg-emerald-400 dark:text-slate-950'
            : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800/70'"
          @click="activeSection = 'overview'"
        >
          账户概览
        </button>
        <button
          class="rounded-2xl px-5 py-3 text-left text-sm font-bold transition-colors"
          :class="activeSection === 'account-security'
            ? 'bg-slate-950 text-white shadow-lg shadow-slate-950/10 dark:bg-emerald-400 dark:text-slate-950'
            : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800/70'"
          @click="activeSection = 'account-security'"
        >
          账户安全
        </button>
        <button
          class="rounded-2xl px-5 py-3 text-left text-sm font-bold transition-colors"
          :class="activeSection === 'farm'
            ? 'bg-slate-950 text-white shadow-lg shadow-slate-950/10 dark:bg-emerald-400 dark:text-slate-950'
            : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800/70'"
          @click="activeSection = 'farm'"
        >
          我的农场
        </button>
        <button
          class="rounded-2xl px-5 py-3 text-left text-sm font-bold transition-colors"
          :class="activeSection === 'favorites'
            ? 'bg-slate-950 text-white shadow-lg shadow-slate-950/10 dark:bg-emerald-400 dark:text-slate-950'
            : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800/70'"
          @click="activeSection = 'favorites'"
        >
          云端收藏
        </button>
        <button
          class="rounded-2xl px-5 py-3 text-left text-sm font-bold transition-colors"
          :class="activeSection === 'records'
            ? 'bg-slate-950 text-white shadow-lg shadow-slate-950/10 dark:bg-emerald-400 dark:text-slate-950'
            : 'text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800/70'"
          @click="activeSection = 'records'"
        >
          识别记录
        </button>

        <div class="pc-settings-soft mt-auto rounded-[1.75rem] border border-slate-100 bg-slate-50 p-5 dark:border-slate-800 dark:bg-slate-950/60">
          <div class="text-xs font-bold uppercase tracking-[0.18em] text-slate-400">当前作物</div>
          <div class="mt-3 text-lg font-black text-slate-900 dark:text-slate-100">{{ activeCropSummary }}</div>
          <button
            class="mt-5 w-full rounded-2xl border border-rose-200 px-4 py-3 text-sm font-bold text-rose-500 transition hover:bg-rose-50 dark:border-rose-500/20 dark:hover:bg-rose-500/10"
            @click="handleLogout"
          >
            退出当前账户
          </button>
        </div>
      </aside>

      <section class="custom-scrollbar min-h-0 flex-1 overflow-y-auto lg:pr-3">
        <div v-if="loading" class="flex h-full items-center justify-center text-sm font-bold text-slate-400">
          正在同步账户与农场数据...
        </div>

        <div v-else-if="activeSection === 'overview'" class="space-y-8">
          <div class="rounded-[2rem] border border-slate-100 bg-gradient-to-br from-slate-950 via-slate-900 to-emerald-900 p-7 text-white shadow-[0_20px_60px_rgba(15,23,42,0.24)] dark:border-slate-800">
            <div class="flex flex-col gap-6 xl:flex-row xl:items-center xl:justify-between">
              <div class="flex items-center gap-5">
                <div class="flex h-24 w-24 items-center justify-center overflow-hidden rounded-[1.75rem] bg-white/10 text-3xl font-black ring-1 ring-white/10">
                  <img v-if="currentUser?.avatarUrl" :src="currentUser.avatarUrl" class="h-full w-full object-cover" />
                  <span v-else>{{ currentUser?.username?.[0]?.toUpperCase() || '?' }}</span>
                </div>
                <div>
                  <div class="text-xs font-bold uppercase tracking-[0.24em] text-white/60">LeafQuery Account</div>
                  <div v-if="currentUser" class="mt-3">
                    <div v-if="!isEditingUsername" class="flex items-center gap-3">
                      <h3 class="text-3xl font-black">{{ currentUser.username }}</h3>
                      <button @click="startEditUsername" class="w-8 h-8 rounded-full bg-white/10 hover:bg-white/20 active:scale-90 backdrop-blur-sm flex items-center justify-center text-white/70 hover:text-white transition-all shadow-sm ring-1 ring-white/10" title="修改用户名">
                        <svg xmlns="http://www.w3.org/2000/svg" class="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2"><path stroke-linecap="round" stroke-linejoin="round" d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" /></svg>
                      </button>
                    </div>
                    <div v-else class="flex flex-col gap-2">
                      <div class="flex items-center gap-2">
                        <input
                          v-model="editUsername"
                          @keyup.enter="submitEditUsername"
                          @keyup.escape="cancelEditUsername"
                          type="text"
                          maxlength="20"
                          autofocus
                          class="w-48 bg-white/10 backdrop-blur-md border border-white/20 text-white placeholder-white/40 rounded-xl px-3 py-2 font-bold focus:outline-none focus:ring-2 focus:ring-emerald-400 focus:bg-white/20 transition-all text-base"
                          placeholder="输入新用户名"
                        />
                        <button @click="submitEditUsername" :disabled="editUsernameLoading" class="w-10 h-10 rounded-xl bg-emerald-500/80 hover:bg-emerald-500 active:scale-90 backdrop-blur-sm flex items-center justify-center text-white font-bold transition-all disabled:opacity-50 ring-1 ring-emerald-400/50">
                          <span v-if="editUsernameLoading" class="animate-spin text-sm">⏳</span>
                          <span v-else class="text-base">✓</span>
                        </button>
                        <button @click="cancelEditUsername" class="w-10 h-10 rounded-xl bg-white/10 hover:bg-rose-500/60 active:scale-90 backdrop-blur-sm flex items-center justify-center text-white/70 hover:text-white font-bold transition-all ring-1 ring-white/10">
                          <span class="text-base" v-text="'✕'"></span>
                        </button>
                      </div>
                      <p v-if="editUsernameError" class="text-rose-300 text-xs font-bold animate-pulse">{{ editUsernameError }}</p>
                    </div>
                  </div>
                  <h3 v-else class="mt-3 text-3xl font-black">未登录用户</h3>
                  <p class="mt-2 max-w-2xl text-sm text-white/70">
                    当前账户负责同步 PC 端农场档案、识别记录和收藏数据。退出登录后会自动回退到本地模式。
                  </p>
                </div>
              </div>

              <div class="grid grid-cols-2 gap-3 text-sm xl:min-w-[320px]">
                <div class="rounded-2xl bg-white/[0.08] px-4 py-3 ring-1 ring-white/10">
                  <div class="text-white/[0.55]">用户 ID</div>
                  <div class="mt-2 font-black">{{ currentUser?.userId || '-' }}</div>
                </div>
                <div class="rounded-2xl bg-white/[0.08] px-4 py-3 ring-1 ring-white/10">
                  <div class="text-white/[0.55]">主题</div>
                  <div class="mt-2 font-black">{{ currentThemeLabel }}</div>
                </div>
                <div class="col-span-2 rounded-2xl bg-white/[0.08] px-4 py-3 ring-1 ring-white/10">
                  <div class="text-white/[0.55]">当前作物</div>
                  <div class="mt-2 font-black">{{ farmStore.activeCrop?.name || '未设置' }}</div>
                </div>
              </div>
            </div>
          </div>

          <div class="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-4">
            <article class="pc-settings-soft rounded-[1.75rem] border border-slate-100 bg-slate-50 p-6 dark:border-slate-800 dark:bg-slate-950/[0.55]">
              <div class="text-xs font-bold uppercase tracking-[0.18em] text-slate-400">农场作物</div>
              <div class="mt-3 text-3xl font-black text-slate-900 dark:text-slate-100">{{ cropCount }}</div>
              <div class="mt-2 text-sm text-slate-500 dark:text-slate-400">已建档作物数量</div>
            </article>
            <article class="pc-settings-soft rounded-[1.75rem] border border-slate-100 bg-slate-50 p-6 dark:border-slate-800 dark:bg-slate-950/[0.55]">
              <div class="text-xs font-bold uppercase tracking-[0.18em] text-slate-400">覆盖区域</div>
              <div class="mt-3 text-3xl font-black text-slate-900 dark:text-slate-100">{{ coveredAreaCount }}</div>
              <div class="mt-2 text-sm text-slate-500 dark:text-slate-400">按地区去重后的作物覆盖范围</div>
            </article>
            <article class="pc-settings-soft rounded-[1.75rem] border border-slate-100 bg-slate-50 p-6 dark:border-slate-800 dark:bg-slate-950/[0.55]">
              <div class="text-xs font-bold uppercase tracking-[0.18em] text-slate-400">识别记录</div>
              <div class="mt-3 text-3xl font-black text-slate-900 dark:text-slate-100">{{ recordCount }}</div>
              <div class="mt-2 text-sm text-slate-500 dark:text-slate-400">本页已同步的历史识别条目</div>
            </article>
            <article class="pc-settings-soft rounded-[1.75rem] border border-slate-100 bg-slate-50 p-6 dark:border-slate-800 dark:bg-slate-950/[0.55]">
              <div class="text-xs font-bold uppercase tracking-[0.18em] text-slate-400">病害种类</div>
              <div class="mt-3 text-3xl font-black text-slate-900 dark:text-slate-100">{{ diseaseCount }}</div>
              <div class="mt-2 text-sm text-slate-500 dark:text-slate-400">剔除健康结果后的病害数</div>
            </article>
          </div>

          <div class="grid grid-cols-1 gap-6">
            <section class="pc-settings-panel rounded-[1.75rem] border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-950/[0.55]">
              <h4 class="text-lg font-black text-slate-900 dark:text-slate-100">账户信息</h4>
              <div class="mt-5 grid grid-cols-1 gap-4 md:grid-cols-2">
                <div class="pc-settings-soft rounded-2xl border border-slate-100 bg-slate-50 px-4 py-4 dark:border-slate-800 dark:bg-slate-900">
                  <div class="text-xs font-bold uppercase tracking-[0.16em] text-slate-400">用户名</div>
                  <div class="mt-2 text-sm font-bold text-slate-800 dark:text-slate-100">{{ currentUser?.username || '未登录' }}</div>
                </div>
                <div class="pc-settings-soft rounded-2xl border border-slate-100 bg-slate-50 px-4 py-4 dark:border-slate-800 dark:bg-slate-900">
                  <div class="text-xs font-bold uppercase tracking-[0.16em] text-slate-400">手机号</div>
                  <div class="mt-2 text-sm font-bold text-slate-800 dark:text-slate-100">{{ currentUser?.phoneNumber || '未绑定' }}</div>
                </div>
                <div class="pc-settings-soft rounded-2xl border border-slate-100 bg-slate-50 px-4 py-4 dark:border-slate-800 dark:bg-slate-900">
                  <div class="text-xs font-bold uppercase tracking-[0.16em] text-slate-400">当前作物</div>
                  <div class="mt-2 text-sm font-bold text-slate-800 dark:text-slate-100">{{ farmStore.activeCrop?.name || '未设置' }}</div>
                </div>
                <div class="pc-settings-soft rounded-2xl border border-slate-100 bg-slate-50 px-4 py-4 dark:border-slate-800 dark:bg-slate-900">
                  <div class="text-xs font-bold uppercase tracking-[0.16em] text-slate-400">当前物候期</div>
                  <div class="mt-2 text-sm font-bold text-slate-800 dark:text-slate-100">{{ farmStore.activeCrop?.stage || '未设置' }}</div>
                </div>
              </div>
            </section>
          </div>
        </div>

        <div v-else-if="activeSection === 'account-security'" class="space-y-6">
          <div class="flex flex-col gap-3">
            <h3 class="text-2xl font-black text-slate-900 dark:text-slate-100">账户安全</h3>
            <p class="text-sm text-slate-500 dark:text-slate-400">
              在这里修改登录密码，或执行不可恢复的账号注销操作。
            </p>
          </div>

          <div
            v-if="accountFeedbackMessage"
            class="rounded-2xl border px-4 py-3 text-sm font-bold"
            :class="accountFeedbackType === 'success'
              ? 'border-emerald-100 bg-emerald-50 text-emerald-600 dark:border-emerald-500/20 dark:bg-emerald-500/10 dark:text-emerald-300'
              : 'border-rose-100 bg-rose-50 text-rose-500 dark:border-rose-500/20 dark:bg-rose-500/10 dark:text-rose-300'"
          >
            {{ accountFeedbackMessage }}
          </div>

          <div
            v-if="!currentUser"
            class="rounded-[1.75rem] border border-dashed border-slate-200 bg-slate-50 px-6 py-16 text-center text-slate-400 dark:border-slate-700 dark:bg-slate-950/[0.55]"
          >
            当前未登录，登录后可在这里修改密码或注销账号。
          </div>

          <template v-else>
            <section class="pc-settings-panel rounded-[1.75rem] border border-slate-100 bg-white p-6 shadow-sm dark:border-slate-800 dark:bg-slate-950/[0.55]">
              <h4 class="text-lg font-black text-slate-900 dark:text-slate-100">修改密码</h4>
              <p class="mt-2 text-sm text-slate-500 dark:text-slate-400">
                修改成功后保持当前登录状态，下次请使用新密码登录。
              </p>

              <div v-if="passwordError" class="mt-5 rounded-2xl border border-rose-100 bg-rose-50 px-4 py-3 text-sm font-bold text-rose-500 dark:border-rose-500/20 dark:bg-rose-500/10 dark:text-rose-300">
                {{ passwordError }}
              </div>

              <div class="mt-5 grid grid-cols-1 gap-4 lg:grid-cols-3">
                <input
                  v-model="passwordForm.currentPassword"
                  type="password"
                  placeholder="当前密码"
                  class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-sm font-medium text-slate-800 outline-none transition focus:border-emerald-400 focus:bg-white dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
                />
                <input
                  v-model="passwordForm.newPassword"
                  type="password"
                  placeholder="新密码（至少 6 位）"
                  class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-sm font-medium text-slate-800 outline-none transition focus:border-emerald-400 focus:bg-white dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
                />
                <input
                  v-model="passwordForm.confirmPassword"
                  type="password"
                  placeholder="确认新密码"
                  class="rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-sm font-medium text-slate-800 outline-none transition focus:border-emerald-400 focus:bg-white dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
                />
              </div>

              <div class="mt-5 flex justify-end">
                <button
                  class="rounded-2xl bg-emerald-500 px-5 py-3 text-sm font-bold text-white transition hover:bg-emerald-600 disabled:cursor-not-allowed disabled:bg-emerald-300"
                  :disabled="passwordSaving"
                  @click="submitPasswordChange"
                >
                  {{ passwordSaving ? '保存中...' : '保存新密码' }}
                </button>
              </div>
            </section>

            <section class="pc-settings-panel rounded-[1.75rem] border border-rose-100 bg-white p-6 shadow-sm dark:border-rose-500/20 dark:bg-slate-950/[0.55]">
              <h4 class="text-lg font-black text-rose-500 dark:text-rose-300">注销账号</h4>
              <p class="mt-2 text-sm leading-6 text-slate-500 dark:text-slate-400">
                注销后将删除当前账号的云端农场、识别记录、收藏等数据，并且无法恢复。
              </p>

              <div class="mt-5 rounded-2xl border border-rose-100 bg-rose-50 px-4 py-3 text-sm text-rose-500 dark:border-rose-500/20 dark:bg-rose-500/10 dark:text-rose-300">
                为了确认这是你本人操作，请输入当前密码后再执行注销。
              </div>

              <div v-if="deleteError" class="mt-4 rounded-2xl border border-rose-100 bg-rose-50 px-4 py-3 text-sm font-bold text-rose-500 dark:border-rose-500/20 dark:bg-rose-500/10 dark:text-rose-300">
                {{ deleteError }}
              </div>

              <div class="mt-5 flex flex-col gap-4 lg:flex-row">
                <input
                  v-model="deleteForm.currentPassword"
                  type="password"
                  placeholder="当前密码"
                  class="flex-1 rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-sm font-medium text-slate-800 outline-none transition focus:border-rose-400 focus:bg-white dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
                />
                <button
                  class="rounded-2xl bg-rose-500 px-5 py-3 text-sm font-bold text-white transition hover:bg-rose-600 disabled:cursor-not-allowed disabled:bg-rose-300"
                  :disabled="deleteSubmitting"
                  @click="submitDeleteAccount"
                >
                  {{ deleteSubmitting ? '注销中...' : '确认注销账号' }}
                </button>
              </div>
            </section>
          </template>
        </div>

        <div v-else-if="activeSection === 'farm'" class="space-y-6">
          <PCFarmManager
            title="我的农场"
            subtitle="桌面端已接入云端农场档案。你可以直接新增、编辑、删除作物，并设置当前作物供工作台和数据中心联动使用。"
          />
        </div>

        <div v-else-if="activeSection === 'favorites'" class="space-y-6">
          <div class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <h3 class="text-2xl font-black text-slate-900 dark:text-slate-100">云端收藏</h3>
              <p class="mt-2 text-sm text-slate-500 dark:text-slate-400">
                此处展示 `favorite/list` 返回的收藏内容，支持直接取消收藏。
              </p>
            </div>
            <div class="rounded-full bg-slate-100 px-4 py-2 text-sm font-bold text-slate-600 dark:bg-slate-800 dark:text-slate-200">
              共 {{ favoriteCount }} 条
            </div>
          </div>

          <div
            v-if="!favoritesStore.favoriteItems.length"
            class="rounded-[2rem] border border-dashed border-slate-200 bg-slate-50 px-6 py-16 text-center text-slate-400 dark:border-slate-700 dark:bg-slate-950/[0.55]"
          >
            当前没有可展示的收藏内容。
          </div>

          <div v-else class="grid grid-cols-1 gap-4 xl:grid-cols-2">
            <article
              v-for="item in favoritesStore.favoriteItems"
              :key="`${item.itemType}_${item.itemId}`"
              class="pc-settings-panel relative flex gap-4 rounded-[1.75rem] border border-slate-100 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-950/[0.55]"
            >
              <div v-if="item.imageUrl" class="h-24 w-24 shrink-0 overflow-hidden rounded-2xl bg-slate-100 dark:bg-slate-800">
                <img :src="item.imageUrl" class="h-full w-full object-cover" />
              </div>

              <div class="min-w-0 flex-1">
                <div class="flex items-center gap-2">
                  <span class="rounded-full px-2.5 py-1 text-xs font-bold" :class="getFavoriteTypeClass(item.itemType)">
                    {{ getFavoriteTypeLabel(item.itemType) }}
                  </span>
                </div>
                <div class="mt-3 line-clamp-2 text-base font-black text-slate-900 dark:text-slate-100">{{ item.title }}</div>
                <div class="mt-2 line-clamp-2 text-sm text-slate-500 dark:text-slate-400">
                  {{ item.description || '暂无描述信息' }}
                </div>
                <div class="mt-3 text-xs text-slate-400">{{ formatDateTime(item.createTime) }}</div>
              </div>

              <button
                class="absolute right-4 top-4 rounded-full bg-rose-50 px-3 py-1 text-xs font-bold text-rose-500 transition hover:bg-rose-100 disabled:cursor-not-allowed disabled:opacity-50 dark:bg-rose-500/10 dark:hover:bg-rose-500/20"
                :disabled="favoritesStore.isFavoriteSubmitting"
                @click="toggleFavorite(item)"
              >
                取消收藏
              </button>
            </article>
          </div>
        </div>

        <div v-else-if="activeSection === 'records'" class="space-y-6">
          <div class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <h3 class="text-2xl font-black text-slate-900 dark:text-slate-100">识别记录</h3>
              <p class="mt-2 text-sm text-slate-500 dark:text-slate-400">
                当前页会在挂载后拉取 `/api/record/list`，并同步写入 `farmStore.syncIdentificationHistory(records)`。
              </p>
            </div>
            <div class="rounded-full bg-slate-100 px-4 py-2 text-sm font-bold text-slate-600 dark:bg-slate-800 dark:text-slate-200">
              共 {{ recordCount }} 条
            </div>
          </div>

          <div
            v-if="!historyRecords.length"
            class="rounded-[2rem] border border-dashed border-slate-200 bg-slate-50 px-6 py-16 text-center text-slate-400 dark:border-slate-700 dark:bg-slate-950/[0.55]"
          >
            当前没有识别记录。
          </div>

          <div v-else class="space-y-4">
            <article
              v-for="item in historyRecords"
              :key="item.id"
              class="pc-settings-panel flex flex-col gap-4 rounded-[1.75rem] border border-slate-100 bg-white p-5 shadow-sm md:flex-row md:items-center dark:border-slate-800 dark:bg-slate-950/[0.55]"
            >
              <div class="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-2xl bg-slate-100 text-2xl dark:bg-slate-800">
                <img v-if="item.imageUrl" :src="item.imageUrl" class="h-full w-full object-cover" />
                <span v-else>Leaf</span>
              </div>

              <div class="min-w-0 flex-1">
                <div class="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
                  <div class="text-base font-black text-slate-900 dark:text-slate-100">{{ item.pestName }}</div>
                  <div class="text-xs text-slate-400">{{ formatDateTime(item.createTime) }}</div>
                </div>
                <div class="mt-3 flex flex-wrap items-center gap-2">
                  <span class="rounded-full px-2.5 py-1 text-xs font-bold" :class="getConfidenceClass(item.confidence)">
                    置信度 {{ formatConfidence(item.confidence) }}
                  </span>
                  <span
                    v-if="item.cropName"
                    class="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-700 dark:bg-slate-800 dark:text-slate-300"
                  >
                    {{ item.cropName }}
                  </span>
                  <span
                    v-if="item.region || item.city"
                    class="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-bold text-slate-700 dark:bg-slate-800 dark:text-slate-300"
                  >
                    {{ item.region || item.city }}
                  </span>
                </div>
              </div>
            </article>
          </div>
        </div>


      </section>
    </div>
  </div>
</template>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  width: 6px;
}

.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 9999px;
}

.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

:global(.dark .custom-scrollbar::-webkit-scrollbar-thumb) {
  background: #334155;
}

:global(.dark .custom-scrollbar::-webkit-scrollbar-thumb:hover) {
  background: #475569;
}

.pc-settings-shell {
  background: rgba(255, 255, 255, 0.9);
  border-color: rgba(255, 255, 255, 0.6);
}

.pc-settings-panel {
  background: rgba(255, 255, 255, 0.96);
  border-color: rgba(226, 232, 240, 0.92);
  box-shadow: 0 18px 48px rgba(15, 23, 42, 0.08);
}

.pc-settings-soft {
  background: rgba(248, 250, 252, 0.96);
  border-color: rgba(226, 232, 240, 0.9);
}

:global(.dark .pc-settings-shell) {
  background: rgba(15, 23, 42, 0.92);
  border-color: rgba(30, 41, 59, 0.85);
  box-shadow: 0 28px 90px rgba(2, 6, 23, 0.34);
}

:global(.dark .pc-settings-panel) {
  background: rgba(2, 6, 23, 0.55);
  border-color: rgba(51, 65, 85, 0.88);
  box-shadow: 0 18px 48px rgba(2, 6, 23, 0.28);
}

:global(.dark .pc-settings-soft) {
  background: rgba(15, 23, 42, 0.78);
  border-color: rgba(51, 65, 85, 0.82);
}
</style>
