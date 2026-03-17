<script setup>
import { ref, computed, onMounted } from 'vue'
import api from '../api'

const users = ref([])
const search = ref('')
const loading = ref(true)

// Modal state
const showModal = ref(false)
const modalTitle = ref('')
const form = ref({ userId: null, username: '', password: '', phoneNumber: '', email: '', role: 'user' })
const isSaving = ref(false)
const formError = ref('')

onMounted(() => {
  fetchUsers()
})

async function fetchUsers() {
  loading.value = true
  try {
    const { data } = await api.get('/users')
    if (data.code === 200) users.value = data.data
  } finally {
    loading.value = false
  }
}

const filtered = computed(() => {
  if (!search.value) return users.value
  const q = search.value.toLowerCase()
  return users.value.filter(u =>
    (u.username || '').toLowerCase().includes(q) ||
    (u.phoneNumber || '').includes(q) ||
    (u.email || '').toLowerCase().includes(q)
  )
})

function roleLabel(r) { return { user: '普通用户', expert: '专家', admin: '管理员' }[r] || r }
function roleBadge(r) { return { user: 'blue', expert: 'green', admin: 'yellow' }[r] || 'gray' }

function openCreateModal() {
  modalTitle.value = '新增用户'
  form.value = { userId: null, username: '', password: '', phoneNumber: '', email: '', role: 'user' }
  formError.value = ''
  showModal.value = true
}

function openEditModal(user) {
  modalTitle.value = '编辑用户'
  form.value = { ...user, password: '' } // 不显示原密码，留空表示不修改
  formError.value = ''
  showModal.value = true
}

async function saveUser() {
  if (!form.value.username) {
    formError.value = '请输入用户名'
    return
  }
  if (!form.value.userId && !form.value.password) {
    formError.value = '新增用户必须输入密码'
    return
  }

  isSaving.value = true
  formError.value = ''
  try {
    const payload = { ...form.value }
    if (!payload.password) delete payload.password // 如果编辑时未按密码，表示不修改

    if (form.value.userId) {
      await api.put(`/users/${form.value.userId}`, payload)
    } else {
      await api.post('/users', payload)
    }
    showModal.value = false
    fetchUsers()
    alert(form.value.userId ? '更新成功' : '创建成功')
  } catch (err) {
    formError.value = err.response?.data?.message || '保存失败'
  } finally {
    isSaving.value = false
  }
}

async function confirmDelete(user) {
  if (!confirm(`确定要删除用户 "${user.username}" 及其关联数据吗？此操作不可逆！`)) return
  try {
    await api.delete(`/users/${user.userId}`)
    alert('删除成功')
    fetchUsers()
  } catch (err) {
    alert(err.response?.data?.message || '删除失败')
  }
}
</script>

<template>
  <div>
    <div class="page-header">
      <h2>👥 用户管理</h2>
      <p>查看和管理前台注册用户</p>
    </div>

    <div class="toolbar">
      <div class="toolbar-left">
        <input v-model="search" type="text" placeholder="搜索用户名、手机号、邮箱..." style="width: 300px" />
        <button class="btn btn-primary" @click="openCreateModal">+ 新增用户</button>
      </div>
      <div class="toolbar-right">共 {{ filtered.length }} 位用户</div>
    </div>

    <div class="table-wrapper">
      <table>
        <thead>
          <tr>
            <th>ID</th><th>用户名</th><th>手机号</th><th>邮箱</th><th>角色</th><th>注册时间</th><th style="text-align: right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in filtered" :key="u.userId">
            <td>{{ u.userId }}</td>
            <td>
              <div style="display:flex;align-items:center;gap:8px">
                <img v-if="u.avatarUrl" :src="u.avatarUrl" style="width:28px;height:28px;border-radius:50%" />
                <span v-else style="width:28px;height:28px;border-radius:50%;background:#374151;display:flex;align-items:center;justify-content:center;font-size:12px;color:#9ca3af">{{ u.username.charAt(0).toUpperCase() }}</span>
                <span>{{ u.username }}</span>
              </div>
            </td>
            <td>{{ u.phoneNumber || '—' }}</td>
            <td>{{ u.email || '—' }}</td>
            <td><span class="badge" :class="roleBadge(u.role)">{{ roleLabel(u.role) }}</span></td>
            <td>{{ u.createdAt }}</td>
            <td style="text-align: right">
              <div class="btn-group" style="justify-content:flex-end">
                <button class="btn btn-ghost btn-sm" @click="openEditModal(u)">编辑</button>
                <button class="btn btn-danger btn-sm" @click="confirmDelete(u)">删除</button>
              </div>
            </td>
          </tr>
          <tr v-if="!filtered.length"><td colspan="7" class="text-center" style="padding:40px;color:var(--text-muted)">无匹配用户</td></tr>
        </tbody>
      </table>
    </div>

    <!-- 用户表单 Modal -->
    <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
      <div class="modal">
        <div class="modal-header">
          <h3>{{ modalTitle }}</h3>
          <button class="btn-close" @click="showModal = false">×</button>
        </div>
        <div class="modal-body">
          <div v-if="formError" class="alert alert-error" style="margin-bottom:1rem;color:#f87171;background:rgba(248,113,113,0.1);padding:0.75rem;border-radius:6px;">{{ formError }}</div>
          <div class="form-group">
            <label>用户名 <span style="color:#f87171">*</span></label>
            <input v-model="form.username" type="text" placeholder="输入用户名" />
          </div>
          <div class="form-group">
            <label>密码 <span v-if="!form.userId" style="color:#f87171">*</span></label>
            <input v-model="form.password" type="password" :placeholder="form.userId ? '留空表示不修改密码' : '输入密码'" />
          </div>
          <div class="form-group">
            <label>手机号</label>
            <input v-model="form.phoneNumber" type="text" placeholder="输入手机号" />
          </div>
          <div class="form-group">
            <label>邮箱</label>
            <input v-model="form.email" type="email" placeholder="输入邮箱" />
          </div>
          <div class="form-group">
            <label>角色</label>
            <select v-model="form.role">
              <option value="user">普通用户</option>
              <option value="expert">专家</option>
              <option value="admin">管理员</option>
            </select>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" @click="showModal = false">取消</button>
          <button class="btn btn-primary" @click="saveUser" :disabled="isSaving">
            {{ isSaving ? '保存中...' : '保存' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

