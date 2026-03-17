<script setup>
import { ref, onMounted } from 'vue'
import { useAdminStore } from '../stores/admin'
import api from '../api'

const store = useAdminStore()
const list = ref([])
const showModal = ref(false)
const editing = ref(null)
const form = ref({ title: '', content: '', type: 'info', status: 1, displayMode: 'normal' })
const toast = ref('')

onMounted(() => load())

async function load() {
  const { data } = await api.get('/announcements')
  if (data.code === 200) list.value = data.data
}

function openCreate() {
  editing.value = null
  form.value = { title: '', content: '', type: 'info', status: 1, displayMode: 'normal' }
  showModal.value = true
}

function openEdit(item) {
  editing.value = item
  form.value = { title: item.title, content: item.content, type: item.type, status: item.status, displayMode: item.displayMode || 'normal' }
  showModal.value = true
}

async function save() {
  if (!form.value.title) return
  if (editing.value) {
    await api.put(`/announcements/${editing.value.id}`, { ...form.value, adminId: store.adminId })
    showToast('公告已更新')
  } else {
    await api.post('/announcements', { ...form.value, adminId: store.adminId })
    showToast('公告已创建')
  }
  showModal.value = false
  load()
}

async function remove(id) {
  if (!confirm('确定删除该公告？')) return
  await api.delete(`/announcements/${id}?adminId=${store.adminId}`)
  showToast('公告已删除')
  load()
}

function showToast(msg) { toast.value = msg; setTimeout(() => toast.value = '', 2500) }
function typeLabel(t) { return { info: '通知', warning: '警告', urgent: '紧急' }[t] || t }
function typeBadge(t) { return { info: 'blue', warning: 'yellow', urgent: 'red' }[t] || 'gray' }
</script>

<template>
  <div>
    <div class="page-header">
      <h2>📢 公告管理</h2>
      <p>管理系统公告与通知</p>
    </div>

    <div class="toolbar">
      <div class="toolbar-left">共 {{ list.length }} 条公告</div>
      <div class="toolbar-right">
        <button class="btn btn-primary" @click="openCreate">＋ 新建公告</button>
      </div>
    </div>

    <div class="table-wrapper">
      <table>
        <thead>
          <tr>
            <th>ID</th><th>标题</th><th>类型</th><th>展示方式</th><th>状态</th><th>发布者</th><th>更新时间</th><th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in list" :key="item.id">
            <td>{{ item.id }}</td>
            <td class="text-truncate">{{ item.title }}</td>
            <td><span class="badge" :class="typeBadge(item.type)">{{ typeLabel(item.type) }}</span></td>
            <td><span class="badge" :class="item.displayMode === 'popup' ? 'red' : 'blue'">{{ item.displayMode === 'popup' ? '弹窗' : '普通' }}</span></td>
            <td><span class="badge" :class="item.status === 1 ? 'green' : 'gray'">{{ item.status === 1 ? '已发布' : '草稿' }}</span></td>
            <td>{{ item.adminName || '—' }}</td>
            <td>{{ item.updatedAt }}</td>
            <td>
              <div class="btn-group">
                <button class="btn btn-ghost btn-sm" @click="openEdit(item)">编辑</button>
                <button class="btn btn-danger btn-sm" @click="remove(item.id)">删除</button>
              </div>
            </td>
          </tr>
          <tr v-if="!list.length"><td colspan="8" class="text-center" style="padding:40px;color:var(--text-muted)">暂无公告</td></tr>
        </tbody>
      </table>
    </div>

    <!-- Modal -->
    <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
      <div class="modal">
        <div class="modal-header">
          <h3>{{ editing ? '编辑公告' : '新建公告' }}</h3>
          <button class="modal-close" @click="showModal = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>公告标题</label>
            <input v-model="form.title" type="text" placeholder="请输入标题" />
          </div>
          <div class="form-row">
            <div class="form-group">
              <label>类型</label>
              <select v-model="form.type">
                <option value="info">通知</option>
                <option value="warning">警告</option>
                <option value="urgent">紧急</option>
              </select>
            </div>
            <div class="form-group">
              <label>状态</label>
              <select v-model="form.status">
                <option :value="1">已发布</option>
                <option :value="0">草稿</option>
              </select>
            </div>
            <div class="form-group">
              <label>展示方式</label>
              <select v-model="form.displayMode">
                <option value="normal">普通通知</option>
                <option value="popup">弹窗通知</option>
              </select>
            </div>
          </div>
          <div class="form-group">
            <label>公告内容</label>
            <textarea v-model="form.content" rows="6" placeholder="请输入公告正文"></textarea>
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost" @click="showModal = false">取消</button>
          <button class="btn btn-primary" @click="save">{{ editing ? '保存' : '创建' }}</button>
        </div>
      </div>
    </div>

    <!-- Toast -->
    <div v-if="toast" class="toast-container"><div class="toast success">{{ toast }}</div></div>
  </div>
</template>
