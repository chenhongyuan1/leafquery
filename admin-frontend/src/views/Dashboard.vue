<script setup>
import { ref, onMounted } from 'vue'
import api from '../api'

const stats = ref({})
const loading = ref(true)

onMounted(async () => {
  try {
    const { data } = await api.get('/dashboard')
    if (data.code === 200) stats.value = data.data
  } catch (e) {
    console.error('Dashboard load failed', e)
  } finally {
    loading.value = false
  }
})

const cards = [
  { key: 'userCount', label: '注册用户', icon: '👥', cls: 'green' },
  { key: 'recordCount', label: '识别记录', icon: '🔬', cls: 'blue' },
  { key: 'newsCount', label: '资讯文章', icon: '📰', cls: 'yellow' },
  { key: 'knowledgeCount', label: '知识条目', icon: '📚', cls: 'green' },
  { key: 'qnaCount', label: '问答帖子', icon: '💬', cls: 'blue' },
  { key: 'announcementCount', label: '系统公告', icon: '📢', cls: 'yellow' },
  { key: 'adminCount', label: '管理员数', icon: '🛡️', cls: 'red' },
  { key: 'logCount', label: '操作日志', icon: '📋', cls: 'gray' },
]
</script>

<template>
  <div>
    <div class="page-header">
      <h2>📊 仪表盘</h2>
      <p>系统数据统计概览</p>
    </div>

    <div class="stats-grid">
      <div v-for="c in cards" :key="c.key" class="stat-card">
        <div class="stat-icon" :class="c.cls">{{ c.icon }}</div>
        <div class="stat-info">
          <div class="stat-value">{{ loading ? '—' : (stats[c.key] ?? 0) }}</div>
          <div class="stat-label">{{ c.label }}</div>
        </div>
      </div>
    </div>

    <div class="card">
      <h3 style="margin-bottom: 12px; font-size: 16px;">系统信息</h3>
      <table>
        <tbody>
          <tr><td style="color: var(--text-secondary); width: 160px;">系统名称</td><td>LeafQuery 病虫害智能识别系统</td></tr>
          <tr><td style="color: var(--text-secondary);">后端框架</td><td>Spring Boot 3.5.10 + MyBatis + MySQL</td></tr>
          <tr><td style="color: var(--text-secondary);">前端框架</td><td>Vue 3 + Vite</td></tr>
          <tr><td style="color: var(--text-secondary);">识别引擎</td><td>YOLOv8s + Python Flask</td></tr>
          <tr><td style="color: var(--text-secondary);">AI 大模型</td><td>豆包 Doubao (Seed-2.0-Lite)</td></tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
