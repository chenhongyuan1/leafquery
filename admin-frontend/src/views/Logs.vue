<script setup>
import { ref, onMounted } from 'vue'
import api from '../api'

const logs = ref([])
const loading = ref(true)

onMounted(async () => {
  const { data } = await api.get('/logs')
  if (data.code === 200) logs.value = data.data
  loading.value = false
})

function actionBadge(action) {
  if (action.includes('删除')) return 'red'
  if (action.includes('新增') || action.includes('创建')) return 'green'
  if (action.includes('更新') || action.includes('编辑')) return 'yellow'
  if (action.includes('登录')) return 'blue'
  return 'gray'
}
</script>

<template>
  <div>
    <div class="page-header">
      <h2>📋 操作日志</h2>
      <p>管理员操作审计记录</p>
    </div>

    <div class="toolbar">
      <div class="toolbar-left">共 {{ logs.length }} 条记录</div>
    </div>

    <div class="table-wrapper">
      <table>
        <thead>
          <tr><th>ID</th><th>操作者</th><th>动作</th><th>对象</th><th>详情</th><th>IP</th><th>时间</th></tr>
        </thead>
        <tbody>
          <tr v-for="l in logs" :key="l.id">
            <td>{{ l.id }}</td>
            <td>{{ l.adminName || '—' }}</td>
            <td><span class="badge" :class="actionBadge(l.action)">{{ l.action }}</span></td>
            <td class="text-truncate">{{ l.target || '—' }}</td>
            <td class="text-truncate">{{ l.detail || '—' }}</td>
            <td style="font-family: monospace; font-size: 12px;">{{ l.ip || '—' }}</td>
            <td>{{ l.createdAt }}</td>
          </tr>
          <tr v-if="!logs.length"><td colspan="7" class="text-center" style="padding:40px;color:var(--text-muted)">暂无日志</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
