<script setup>
import { ref, computed, onMounted } from 'vue'
import api from '../api'
import { getChatEndpoint, getModelField, validateModelValue } from '../utils/modelConfig.js'

const configs = ref([])
const activeCategory = ref('yolo')
const showModal = ref(false)
const editing = ref(null)
const form = ref({ configValue: '', description: '' })
const toast = ref('')
const toastType = ref('success')
const loading = ref(false)
const loadError = ref('')
const saving = ref(false)
const formError = ref('')
const revealKey = ref(false)
const uploading = ref(false)
const uploadProgress = ref('')

onMounted(() => load())

async function load({ afterSave = false } = {}) {
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await api.get('/models')
    if (!Array.isArray(data.data)) throw new Error('模型配置返回格式异常')
    configs.value = data.data
  } catch (error) {
    loadError.value = afterSave ? '配置已保存，但重新读取失败，请重试加载。' : '模型配置加载失败，请重试。'
    showToast(loadError.value, 'error')
  } finally {
    loading.value = false
  }
}

const filtered = computed(() => configs.value.filter(c => c.category === activeCategory.value))
const editingField = computed(() => getModelField(editing.value?.configKey))
const llmValue = key => configs.value.find(item => item.configKey === key)?.configValue || ''
const configuredModel = computed(() => llmValue('doubao.api.model') || '使用服务器环境配置')
const configuredUrlError = computed(() => validateModelValue('doubao.api.url', llmValue('doubao.api.url')))
const configuredEndpoint = computed(() => configuredUrlError.value ? '' : getChatEndpoint(llmValue('doubao.api.url')))
const editEndpoint = computed(() => {
  if (editing.value?.configKey !== 'doubao.api.url' || validateModelValue('doubao.api.url', form.value.configValue)) return ''
  return getChatEndpoint(form.value.configValue)
})

function openEdit(item) {
  editing.value = item
  form.value = { configValue: item.configValue, description: item.description }
  formError.value = ''
  revealKey.value = false
  showModal.value = true
}

function closeEdit() {
  if (!saving.value) showModal.value = false
}

async function save() {
  if (!editing.value || saving.value) return
  formError.value = validateModelValue(editing.value.configKey, form.value.configValue)
  if (formError.value) return
  saving.value = true
  try {
    await api.put(`/models/${editing.value.id}`, {
      configValue: editingField.value ? (form.value.configValue || '').trim() : form.value.configValue,
      description: form.value.description
    })
    showToast(editingField.value ? '配置已更新，将在下一次 AI 请求中读取' : '配置已更新')
    showModal.value = false
    await load({ afterSave: true })
  } catch (error) {
    formError.value = error.response?.data?.message || error.message || '保存失败，请稍后重试。'
    showToast('配置保存失败', 'error')
  } finally {
    saving.value = false
  }
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

function showToast(msg, type = 'success') { toast.value = msg; toastType.value = type; setTimeout(() => toast.value = '', 3000) }

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
      <p>管理图像识别、OpenAI 兼容问答与语音服务配置</p>
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
      <h3 class="llm-title">📌 AI 问答模型 <span class="badge blue">OpenAI 兼容 · Chat Completions</span></h3>
      <p class="config-help">用于首页农业咨询和连续追问。图像复核和诊断报告使用 Dify 工作流内的模型配置。通过下方“编辑”分别修改接口地址、API Key 和模型 ID，三项需对应同一服务。支持 OpenAI 及兼容服务，图文提问需要所选模型支持图片输入。</p>
      <dl class="llm-summary">
        <div><dt>后台模型 ID</dt><dd>{{ configuredModel }}</dd></div>
        <div><dt>请求端点预览</dt><dd class="endpoint-preview">{{ configuredEndpoint || (configuredUrlError ? '地址需要修改' : '使用服务器环境配置') }}</dd></div>
      </dl>
      <p v-if="configuredUrlError" class="config-error" role="alert">{{ configuredUrlError }}</p>
      <p class="config-help">后台非空配置优先于服务器环境变量，留空时使用服务器配置。保存后从下一次 AI 请求生效；已发出的请求继续使用原配置。</p>
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

    <div v-if="loadError" class="card mb-16 config-error" role="alert">{{ loadError }} <button class="btn btn-ghost btn-sm" @click="load()" :disabled="loading">重试加载</button></div>

    <!-- 配置表格 -->
    <div class="table-wrapper">
      <table>
        <thead>
          <tr><th>配置项</th><th>配置值</th><th>说明</th><th>更新时间</th><th>操作</th></tr>
        </thead>
        <tbody>
          <tr v-for="c in filtered" :key="c.id" :data-config-key="c.configKey">
            <td><div v-if="getModelField(c.configKey)" class="config-label">{{ getModelField(c.configKey).label }}</div><code style="color: var(--accent); font-size: 12px;">{{ c.configKey }}</code></td>
            <td class="config-value">{{ maskValue(c.configKey, c.configValue) }}</td>
            <td style="color: var(--text-secondary);">{{ getModelField(c.configKey)?.hint || c.description }}</td>
            <td>{{ c.updatedAt }}</td>
            <td><button class="btn btn-ghost btn-sm" @click="openEdit(c)">编辑</button></td>
          </tr>
          <tr v-if="!filtered.length"><td colspan="5" class="text-center" style="padding:40px;color:var(--text-muted)">{{ loading ? '正在加载配置...' : '暂无配置' }}</td></tr>
        </tbody>
      </table>
    </div>

    <!-- 模态框 -->
    <div v-if="showModal" class="modal-overlay" @click.self="closeEdit">
      <form class="modal" role="dialog" aria-modal="true" aria-labelledby="model-config-title" novalidate @submit.prevent="save">
        <div class="modal-header">
          <h3 id="model-config-title">编辑 — {{ editingField?.label || editing?.configKey }}</h3>
          <button type="button" class="modal-close" :disabled="saving" aria-label="关闭编辑" @click="closeEdit">×</button>
        </div>
        <div class="modal-body">
          <div class="form-group">
            <label for="model-config-value">{{ editingField?.label || '配置值' }}</label>
            <div v-if="editingField" class="config-input-row">
              <input id="model-config-value" v-model="form.configValue" :type="editingField.type === 'password' && revealKey ? 'text' : editingField.type" :placeholder="editingField.placeholder" :disabled="saving" :aria-invalid="!!formError" aria-describedby="model-field-hint" autocomplete="off" spellcheck="false" />
              <button v-if="editingField.type === 'password'" type="button" class="btn btn-ghost btn-sm" :disabled="saving" :aria-pressed="revealKey" @click="revealKey = !revealKey">{{ revealKey ? '隐藏' : '显示' }}</button>
            </div>
            <textarea v-else id="model-config-value" v-model="form.configValue" rows="3" placeholder="配置值" :disabled="saving"></textarea>
            <p v-if="editingField" id="model-field-hint" class="config-help">{{ editingField.hint }} 留空将使用服务器配置。</p>
            <p v-if="editEndpoint" class="config-help endpoint-preview">请求端点：{{ editEndpoint }}</p>
          </div>
          <div class="form-group">
            <label for="model-config-description">描述说明</label>
            <input id="model-config-description" v-model="form.description" type="text" placeholder="配置说明" :disabled="saving" />
          </div>
          <p v-if="formError" class="config-error" role="alert">{{ formError }}</p>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-ghost" :disabled="saving" @click="closeEdit">取消</button>
          <button type="submit" class="btn btn-primary" :disabled="saving">{{ saving ? '保存中...' : '保存' }}</button>
        </div>
      </form>
    </div>

    <div v-if="toast" class="toast-container" role="status"><div class="toast" :class="toastType">{{ toast }}</div></div>
  </div>
</template>

<style scoped>
.llm-title { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-bottom: 8px; font-size: 15px; }
.config-help { margin-top: 8px; color: var(--text-secondary); font-size: 13px; line-height: 1.8; }
.llm-summary { display: grid; grid-template-columns: minmax(0, 0.7fr) minmax(0, 1.3fr); gap: 16px; margin: 16px 0; }
.llm-summary > div { min-width: 0; padding: 14px; border: 1px solid var(--border); border-radius: var(--radius-sm); background: var(--bg-input); }
.llm-summary dt { margin-bottom: 6px; font-size: 12px; color: var(--text-secondary); }
.llm-summary dd { overflow-wrap: anywhere; font-size: 14px; }
.endpoint-preview, .config-value { overflow-wrap: anywhere; font-family: monospace; font-size: 13px; }
.config-label { margin-bottom: 4px; font-weight: 600; }
.config-error { color: #fca5a5; font-size: 13px; line-height: 1.7; }
.config-input-row { display: flex; align-items: center; gap: 8px; }
.config-input-row input { min-width: 0; flex: 1; }
.config-input-row button { flex-shrink: 0; }
@media (max-width: 900px) { .llm-summary { grid-template-columns: minmax(0, 1fr); } }
</style>
