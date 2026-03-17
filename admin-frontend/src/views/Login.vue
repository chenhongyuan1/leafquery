<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAdminStore } from '../stores/admin'
import api from '../api'

const router = useRouter()
const store = useAdminStore()

const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function handleLogin() {
  error.value = ''
  if (!username.value || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  try {
    const { data } = await api.post('/login', {
      username: username.value,
      password: password.value
    })
    if (data.code === 200) {
      store.login(data.data)
      router.push('/')
    } else {
      error.value = data.message || '登录失败'
    }
  } catch (e) {
    error.value = e.response?.data?.message || '网络错误，请检查后端服务'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-logo">
        <div class="icon">LQ</div>
        <h2>LeafQuery 管理后台</h2>
        <p>病虫害智能识别系统管理平台</p>
      </div>

      <div v-if="error" class="login-error">{{ error }}</div>

      <div class="form-group">
        <label>管理员账号</label>
        <input
          v-model="username"
          type="text"
          placeholder="请输入用户名"
          @keyup.enter="handleLogin"
        />
      </div>

      <div class="form-group">
        <label>密码</label>
        <input
          v-model="password"
          type="password"
          placeholder="请输入密码"
          @keyup.enter="handleLogin"
        />
      </div>

      <button class="btn btn-primary" @click="handleLogin" :disabled="loading">
        {{ loading ? '登录中...' : '登 录' }}
      </button>
    </div>
  </div>
</template>
