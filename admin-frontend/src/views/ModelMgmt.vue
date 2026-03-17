<script setup>
import { ref, computed, onMounted } from 'vue'
import { useAdminStore } from '../stores/admin'
import api from '../api'

const store = useAdminStore()
const configs = ref([])
const activeCategory = ref('yolo')
const showModal = ref(false)
const editing = ref(null)
const form = ref({ configValue: '', description: '' })
const toast = ref('')
const uploading = ref(false)
const uploadProgress = ref('')

onMounted(() => load())

async function load() {
  const { data } = await api.get('/models')
  if (data.code === 200) configs.value = data.data
}

const filtered = computed(() => configs.value.filter(c => c.category === activeCategory.value))

function openEdit(item) {
  editing.value = item
  form.value = { configValue: item.configValue, description: item.description }
  showModal.value = true
}

async function save() {
  if (!editing.value) return
  await api.put(`/models/${editing.value.id}`, {
    configValue: form.value.configValue,
    description: form.value.description,
    updatedBy: store.adminId
  })
  showToast('配置已更新')
  showModal.value = false
  load()
}

function triggerUpload() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.pt'
  input.onchange = (e) => {
    const file = e.target.files[0]
    if (file) uploadModel(file)
  }
  input.click()
}

async function uploadModel(file) {
  if (!file.name.endsWith('.pt')) {
    showToast('仅支持 .pt 格式模型文件')
    return
  }
  if (!confirm(`确认上传 "${file.name}" (${(file.size / 1024 / 1024).toFixed(1)} MB) 替换当前模型？旧模型将自动备份。`)) return

  uploading.value = true
  uploadProgress.value = '正在上传模型文件...'

  try {
    const formData = new FormData()
    formData.append('file', file)
    const { data } = await api.post('/models/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000
    })
    if (data.code === 200) {
      showToast('模型上传成功，已自动热重载！')
    } else {
      showToast(data.message || '上传失败')
    }
  } catch (e) {
    showToast('上传失败: ' + (e.response?.data?.message || e.message))
  } finally {
    uploading.value = false
    uploadProgress.value = ''
  }
}

function showToast(msg) { toast.value = msg; setTimeout(() => toast.value = '', 3000) }

function categoryLabel(c) {
  return { yolo: '🎯 YOLO 图像识别', llm: '🧠 LLM 大语言模型', asr: '🎙️ 语音识别 ASR', tts: '🎤 语音合成 TTS' }[c] || c
}

function maskValue(key, val) {
  if (!val) return '—'
  if (key.includes('key') || key.includes('token') || key.includes('password')) {
    return val.length > 8 ? val.substring(0, 4) + '****' + val.substring(val.length - 4) : '****'
  }
  return val
}
</script>

<template>
  <div>
    <div class="page-header">
      <h2>🤖 模型管理</h2>
      <p>管理 YOLO 图像识别模型与 LLM 大模型配置</p>
    </div>

    <div class="tabs">
      <button class="tab-btn" :class="{ active: activeCategory === 'yolo' }" @click="activeCategory = 'yolo'">🎯 YOLO 模型</button>
      <button class="tab-btn" :class="{ active: activeCategory === 'llm' }" @click="activeCategory = 'llm'">🧠 LLM 大模型</button>
      <button class="tab-btn" :class="{ active: activeCategory === 'asr' }" @click="activeCategory = 'asr'">🎙️ 语音识别</button>
      <button class="tab-btn" :class="{ active: activeCategory === 'tts' }" @click="activeCategory = 'tts'">🎤 语音合成</button>
    </div>

    <!-- YOLO 模型介绍卡片 + 上传按钮 -->
    <div v-if="activeCategory === 'yolo'" class="card mb-16">
      <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:16px;">
        <div style="flex:1;">
          <h3 style="margin-bottom: 8px; font-size: 15px;">📌 YOLO 图像识别模型</h3>
          <p style="color: var(--text-secondary); font-size: 13px; line-height: 1.8;">
            当前使用 <span class="badge green">YOLOv8s-cls</span> 分类模型进行病虫害图像识别。
            模型文件为 <code style="color: var(--accent);">best.pt</code>，部署在 Python Flask 服务中。
            支持 11 类病虫害分类识别。<br/>
            <strong>迭代方式：</strong>点击右侧按钮上传新的模型文件，系统会自动备份旧模型并热重载，无需重启服务。
          </p>
        </div>
        <div style="flex-shrink:0;padding-top:8px;">
          <button class="btn btn-primary" @click="triggerUpload" :disabled="uploading"
                  style="white-space:nowrap;padding:10px 20px;font-size:14px;">
            {{ uploading ? '⏳ 上传中...' : '📤 上传新模型' }}
          </button>
          <p v-if="uploadProgress" style="color:var(--accent);font-size:12px;margin-top:6px;text-align:center;">
            {{ uploadProgress }}
          </p>
        </div>
      </div>
    </div>

    <div v-if="activeCategory === 'llm'" class="card mb-16">
      <h3 style="margin-bottom: 8px; font-size: 15px;">📌 豆包 Doubao 大语言模型</h3>
      <p style="color: var(--text-secondary); font-size: 13px; line-height: 1.8;">
        当前集成 <span class="badge blue">字节跳动 豆包 Seed-2.0-Lite</span> 大模型，用于病虫害智能分析、
        多轮对话问答。通过下方配置可修改 API 地址、密钥和模型 ID。
      </p>
    </div>

    <div v-if="activeCategory === 'tts'" class="card mb-16">
      <h3 style="margin-bottom: 8px; font-size: 15px;">📌 语音合成 Seed-TTS</h3>
      <p style="color: var(--text-secondary); font-size: 13px; line-height: 1.8;">
        当前使用火山引擎 <span class="badge yellow">Seed-TTS V3</span> 生成病虫害播报音频。支持指定独有发音人（如天美桃子）。
      </p>
    </div>

    <div v-if="activeCategory === 'asr'" class="card mb-16">
      <h3 style="margin-bottom: 8px; font-size: 15px;">📌 语音识别 SeedASR</h3>
      <p style="color: var(--text-secondary); font-size: 13px; line-height: 1.8;">
        使用 <span class="badge yellow">豆包 SeedASR</span> 语音大模型提供语音转文字功能，
        支持普通话及多种方言。通过 WebSocket 协议与火山引擎通信。
      </p>
    </div>

    <!-- 配置表格 -->
    <div class="table-wrapper">
      <table>
        <thead>
          <tr><th>配置键</th><th>配置值</th><th>描述</th><th>更新时间</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="c in filtered" :key="c.id">
            <td><code style="color: var(--accent); font-size: 12px;">{{ c.configKey }}</code></td>
            <td style="font-family: monospace; font-size: 13px;">{{ maskValue(c.configKey, c.configValue) }}</td>
            <td style="color: var(--text-secondary);">{{ c.description }}</td>
            <td>{{ c.updatedAt }}</td>
            <td><button class="btn btn-ghost btn-sm" @click="openEdit(c)">编辑</button></td>
          </tr>
          <tr v-if="!filtered.length"><td colspan="5" class="text-center" style="padding:40px;color:var(--text-muted)">暂无配置</td></tr>
        </tbody>
      </table>
    </div>

    <!-- Modal -->
    <div v-if="showModal" class="modal-overlay" @click.self="showModal = false">
      <div class="modal">
        <div class="modal-header">
          <h3>编辑配置 — {{ editing?.configKey }}</h3>
          <button class="modal-close" @click="showModal = false">×</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label>配置值</label>
            <textarea v-model="form.configValue" rows="3" placeholder="配置值"></textarea>
          </div>
          <div class="form-group">
            <label>描述说明</label>
            <input v-model="form.description" type="text" placeholder="配置说明" />
          </div>
        </div>
        <div class="modal-footer">
          <button class="btn btn-ghost" @click="showModal = false">取消</button>
          <button class="btn btn-primary" @click="save">保存</button>
        </div>
      </div>
    </div>

    <div v-if="toast" class="toast-container"><div class="toast success">{{ toast }}</div></div>
  </div>
</template>
