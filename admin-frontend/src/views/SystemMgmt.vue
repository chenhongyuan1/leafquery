<script setup>
import { ref, onMounted } from 'vue'
import api from '../api'

const admins = ref([])
const showModal = ref(false)
const editing = ref(null)
const form = ref({ username: '', password: '', nickname: '', role: 'admin', status: 1 })
const toast = ref('')

onMounted(() => load())

async function load() {
  const { data } = await api.get('/system/admins')
  if (data.code === 200) admins.value = data.data
}

function openCreate() {
  editing.value = null
  form.value = { username: '', password: '', nickname: '', role: 'admin', status: 1 }
  showModal.value = true
}

function openEdit(item) {
  editing.value = item
  form.value = { username: item.username, password: '', nickname: item.nickname, role: item.role, status: item.status }
  showModal.value = true
}

async function save() {
  if (!form.value.username) return
  try {
    if (editing.value) {
      const { data } = await api.put(`/system/admins/${editing.value.adminId}`, form.value)
      if (data.code === 200) showToast('管理员已更新')
      else showToast(data.message || '操作失败')
    } else {
      if (!form.value.password) { showToast('请输入密码'); return }
      const { data } = await api.post('/system/admins', form.value)
      if (data.code === 200) showToast('管理员已创建')
      else showToast(data.message || '操作失败')
    }
  } catch (e) {
    showToast(e.response?.data?.message || '权限不足')
  }
  showModal.value = false
  load()
}

async function remove(id) {
  if (!confirm('确定删除该管理员？')) return
  try {
    const { data } = await api.delete(`/system/admins/${id}`)
    if (data.code === 200) showToast('管理员已删除')
    else showToast(data.message || '操作失败')
  } catch (e) {
    showToast(e.response?.data?.message || '权限不足')
  }
  load()
}

function showToast(msg) { toast.value = msg; setTimeout(() => toast.value = '', 2500) }
</script>

<template>
  <div>
    <div class="page-header">
      <h2>⚙️ 系统管理</h2>
      <p>管理后台管理员账号（仅系统管理员可操作）</p>
    </div>

    <div class="toolbar">
      <div class="toolbar-left">共 {{ admins.length }} 位管理员</div>
      <div class="toolbar-right">
        <button class="btn btn-primary" @click="openCreate">＋ 添加管理员</button>
      </div>
    </div>

    <div class="table-wrapper">
      <table>
        <thead>
          <tr><th>ID</th><th>用户名</th><th>昵称</th><th>角色</th><th>状态</th><th>最后登录</th><th>创建时间</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="a in admins" :key="a.adminId">
            <td>{{ a.adminId }}</td>
            <td>{{ a.username }}</td>
            <td>{{ a.nickname || '—' }}</td>
            <td><span class="badge" :class="a.role === 'super_admin' ? 'red' : 'blue'">{{ a.role === 'super_admin' ? '系统管理员' : '普通管理员' }}</span></td>
            <td><span class="badge" :class="a.status === 1 ? 'green' : 'gray'">{{ a.status === 1 ? '启用' : '禁用' }}</span></td>
            <td>{{ a.lastLoginAt || '从未登录' }}</td>
            <td>{{ a.createdAt }}</td>
            <td>
              <div class="btn-group">
                <button class="btn btn-ghost btn-sm" @click="openEdit(a)">编辑</button>
                <button v-if="a.role !== 'super_admin'" class="btn btn-danger btn-sm" @click="remove(a.adminId)">删除</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Modal -->
    <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
      <div class="modal">
        <div class="modal-header">
          <h3>{{ editing ? '编辑管理员' : '添加管理员' }}</h3>
          <button class="modal-close" @click="showModal = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>用户名</label>
            <input v-model="form.username" type="text" placeholder="管理员用户名" :disabled="!!editing" />
          </div>
          <div class="form-group" v-if="!editing">
            <label>密码</label>
            <input v-model="form.password" type="password" placeholder="设置密码" />
          </div>
          <div class="form-group">
            <label>昵称</label>
            <input v-model="form.nickname" type="text" placeholder="显示昵称" />
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>角色</label>
              <select v-model="form.role">
                <option value="admin">普通管理员</option>
                <option value="super_admin">系统管理员</option>
              </select>
            </div>
            <div class="form-group">
              <label>状态</label>
              <select v-model="form.status">
                <option :value="1">启用</option>
                <option :value="0">禁用</option>
              </select>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost" @click="showModal = false">取消</button>
          <button class="btn btn-primary" @click="save">{{ editing ? '保存' : '创建' }}</button>
        </div>
      </div>
    </div>

    <div v-if="toast" class="toast-container"><div class="toast success">{{ toast }}</div></div>
  </div>
</template>
