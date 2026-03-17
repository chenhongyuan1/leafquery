<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import ScanOverlay from '../components/ScanOverlay.vue'
import axios from 'axios'
import { useSettingsStore } from '../stores/settings'

const router = useRouter()
const settingsStore = useSettingsStore()

const scanRef = ref(null)
const isResultReady = ref(false)
const showDetail = ref(false)
const isAnalyzing = ref(false)
const isSending = ref(false)
const messages = ref([]) 
const userInput = ref('')

// TTS 语音播放状态
const isSpeaking = ref(false)
const isTtsLoading = ref(false)
let currentAudio = null

const speakLastReply = async () => {
  // 如果正在播放，停止
  if (isSpeaking.value) {
    stopSpeaking()
    return
  }

  // 取最后一条 assistant 消息
  const lastAssistant = [...messages.value].reverse().find(m => m.role === 'assistant')
  if (!lastAssistant || !lastAssistant.content) {
    showToast('没有可以朗读的内容', 'warning')
    return
  }

  // 移除 Markdown 格式符号，只保留纯文本
  const plainText = lastAssistant.content
    .replace(/[#*_`~>\-|]/g, '')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/\n{2,}/g, '\n')
    .trim()

  isTtsLoading.value = true
  try {
    const response = await axios.post('/api/ai/text-to-speech', 
      { text: plainText },
      { responseType: 'blob' }
    )
    const blob = new Blob([response.data], { type: 'audio/mpeg' })
    const url = URL.createObjectURL(blob)
    currentAudio = new Audio(url)
    currentAudio.onplay = () => { isSpeaking.value = true; isTtsLoading.value = false }
    currentAudio.onended = () => { stopSpeaking() }
    currentAudio.onerror = () => { stopSpeaking(); showToast('音频播放失败', 'error') }
    currentAudio.play()
  } catch (err) {
    console.error('TTS failed:', err)
    isTtsLoading.value = false
    showToast('语音合成失败，请稍后再试', 'error')
  }
}

const stopSpeaking = () => {
  if (currentAudio) {
    currentAudio.pause()
    currentAudio.currentTime = 0
    if (currentAudio.src) URL.revokeObjectURL(currentAudio.src)
    currentAudio = null
  }
  isSpeaking.value = false
  isTtsLoading.value = false
}
const chatContainer = ref(null)
const generalUserInput = ref('')
const pendingImage = ref('')
const imageInputRef = ref(null)
const hasNewNotification = ref(false)
const popupAnnouncement = ref(null)

const triggerImageUpload = () => {
  imageInputRef.value?.click()
}

const onImageSelected = (event) => {
  const file = event.target.files?.[0]
  if (!file) return

  const reader = new FileReader()
  reader.onload = (e) => {
    pendingImage.value = e.target.result
  }
  reader.readAsDataURL(file)
  event.target.value = ''
}

const removePendingImage = () => {
  pendingImage.value = ''
}

const pendingGeneralImage = ref('')
const generalImageInputRef = ref(null)

const triggerGeneralImageUpload = () => {
  generalImageInputRef.value?.click()
}

const onGeneralImageSelected = (event) => {
  const file = event.target.files?.[0]
  if (!file) return

  const reader = new FileReader()
  reader.onload = (e) => {
    pendingGeneralImage.value = e.target.result
  }
  reader.readAsDataURL(file)
  event.target.value = ''
}

const removePendingGeneralImage = () => {
  pendingGeneralImage.value = ''
}

// 语音转文字 + 方言识别 (Web Audio API PCM 采集 + WAV 编码 → 后端豆包 SeedASR)
const isRecording = ref(false)
const isAudioProcessing = ref(false)

// Toast 弹窗通知系统
const toastMessage = ref('')
const toastType = ref('info') // 'info' | 'error' | 'warning'
const toastVisible = ref(false)
let toastTimer = null

const showToast = (msg, type = 'info', duration = 3000) => {
  toastMessage.value = msg
  toastType.value = type
  toastVisible.value = true
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastVisible.value = false
  }, duration)
}

let audioContext = null
let sourceNode = null
let processorNode = null
let analyserNode = null
let animationFrameId = null
let orbCanvas = null
let orbCtx = null
let orbDepth = 0.3 // CodePen 默认值
let smoothedDepth = 0.3
let prevEnergy = 0
let smoothedEnergy = 0
let orbPhases = { s1: 0, s2: 0, s3: 0, rotation: 0 } // sin 波相位
let stream = null
let pcmBuffers = []

// ============ Organic AI Orb Canvas Renderer (忠实复刻 CodePen 数学公式) ============
// CodePen 原版的 7 个控制点相位偏移
const BLOB_POINTS = [
  { angle: 0, p1: 0, p2: 0, p3: 0 },
  { angle: 51.43, p1: 137.5, p2: 222.5, p3: 360.1 },
  { angle: 102.86, p1: 275, p2: 85, p3: 80.2 },
  { angle: 154.29, p1: 52.5, p2: 307.5, p3: 200.3 },
  { angle: 205.71, p1: 190, p2: 170, p3: 320.4 },
  { angle: 257.14, p1: 327.5, p2: 32.5, p3: 80.5 },
  { angle: 308.57, p1: 105, p2: 255, p3: 200.6 },
]
const DEG = Math.PI / 180

// 构建贝塞尔路径的辅助函数 (复用)
function buildBlobPath(ctx, cx, cy, baseR, depth, phases) {
  const pts = BLOB_POINTS.map(pt => {
    const rand = 0.5 + 0.5 * (
      0.5 * Math.sin(phases.s1 + pt.p1 * DEG) +
      0.3 * Math.sin(phases.s2 + pt.p2 * DEG) +
      0.2 * Math.sin(phases.s3 + pt.p3 * DEG)
    )
    const d = baseR * ((1 - depth) + rand * depth)
    const a = phases.rotation + pt.angle * DEG
    return { x: cx + d * Math.cos(a), y: cy + d * Math.sin(a) }
  })
  const mids = pts.map((p, i) => {
    const next = pts[(i + 1) % pts.length]
    return { x: (p.x + next.x) / 2, y: (p.y + next.y) / 2 }
  })
  ctx.beginPath()
  ctx.moveTo(mids[mids.length - 1].x, mids[mids.length - 1].y)
  for (let i = 0; i < pts.length; i++) {
    ctx.quadraticCurveTo(pts[i].x, pts[i].y, mids[i].x, mids[i].y)
  }
  ctx.closePath()
}

function drawOrb(ctx, w, h, depth, phases) {
  const cx = w / 2, cy = h / 2
  // CodePen 原版: transform: scale(calc(1 + var(--depth)))
  // depth 越大 → 整体越大 + 形变越剧烈 (向外膨胀 + 不规则震动)
  const scaleFactor = 0.5 + depth * 0.5
  const baseR = Math.min(w, h) / 2 * 0.85 * scaleFactor

  ctx.clearRect(0, 0, w, h)

  // ======== 第 1 层: 外发光 Glow (feGaussianBlur 模拟) ========
  // 先画一次带大模糊阴影的填充，然后清除实体只保留光晕
  ctx.save()
  buildBlobPath(ctx, cx, cy, baseR, depth, phases)
  ctx.shadowColor = 'hsla(200, 90%, 70%, 0.6)'
  ctx.shadowBlur = 50
  ctx.shadowOffsetX = 0
  ctx.shadowOffsetY = 0
  ctx.fillStyle = 'hsla(180, 70%, 60%, 0.35)'
  ctx.fill()
  ctx.restore()

  // ======== 第 2 层: 主体填充 (Mesh Gradient) ========
  buildBlobPath(ctx, cx, cy, baseR, depth, phases)

  // 网格渐变填充 (绿色植物主题配色)
  const g1 = ctx.createRadialGradient(cx * 1.4, cy * 0.5, 0, cx, cy, baseR * 1.2)
  g1.addColorStop(0, 'hsla(160, 90%, 75%, 1)')  // 亮薄荷绿
  g1.addColorStop(1, 'hsla(160, 80%, 60%, 0)')
  
  const g2 = ctx.createRadialGradient(cx * 0.4, cy * 1.5, 0, cx, cy, baseR * 1.1)
  g2.addColorStop(0, 'hsla(140, 70%, 55%, 0.8)')  // 翠绿
  g2.addColorStop(1, 'hsla(140, 70%, 55%, 0)')

  const g3 = ctx.createRadialGradient(cx * 0.6, cy * 0.3, 0, cx, cy, baseR)
  g3.addColorStop(0, 'hsla(180, 65%, 60%, 1)')  // 青绿
  g3.addColorStop(1, 'hsla(150, 62%, 50%, 0)')

  // 底色
  ctx.fillStyle = 'hsla(150, 62%, 73%, 1)'
  ctx.fill()
  // 叠加渐变
  ctx.save()
  ctx.clip()
  ctx.fillStyle = g1; ctx.fill()
  ctx.fillStyle = g2; ctx.fill()
  ctx.fillStyle = g3; ctx.fill()

  // ======== 第 3 层: 噪点质感 Film Grain (feTurbulence 模拟) ========
  // 使用 offscreen canvas 生成噪点，再通过 drawImage 叠加（drawImage 尊重 clip）
  if (!drawOrb._grainCanvas) {
    drawOrb._grainCanvas = document.createElement('canvas')
    drawOrb._grainCanvas.width = w
    drawOrb._grainCanvas.height = h
  }
  const gc = drawOrb._grainCanvas.getContext('2d')
  const gImg = gc.createImageData(w, h)
  const gd = gImg.data
  for (let i = 0; i < gd.length; i += 4) {
    const v = Math.random() * 255
    gd[i] = v; gd[i+1] = v; gd[i+2] = v; gd[i+3] = 40
  }
  gc.putImageData(gImg, 0, 0)
  
  // 在已裁剪的主 canvas 上用 overlay 混合模式绘制噪点
  ctx.globalCompositeOperation = 'overlay'
  ctx.drawImage(drawOrb._grainCanvas, 0, 0)

  ctx.restore()
}

function startOrbAnimation(dataArray) {
  const render = () => {
    if (!stream) return
    // 更新相位 (与 CodePen 一致的速率: 7s, 11s, 13s 周期)
    orbPhases.s1 += (2 * Math.PI) / (7 * 60)
    orbPhases.s2 += (2 * Math.PI) / (11 * 60)
    orbPhases.s3 += (2 * Math.PI) / (13 * 60)
    orbPhases.rotation += (2 * Math.PI) / (20 * 60)

    // 音频分析 (与 CodePen JS 完全一致的 RMS 能量检测)
    if (analyserNode) {
      analyserNode.getByteTimeDomainData(dataArray)
      let sumSq = 0
      for (let i = 0; i < dataArray.length; i++) {
        const n = (dataArray[i] - 128) / 128
        sumSq += n * n
      }
      const rms = Math.sqrt(sumSq / dataArray.length)
      smoothedEnergy += (rms - smoothedEnergy) * 0.3
      const delta = smoothedEnergy - prevEnergy
      prevEnergy = smoothedEnergy
      if (delta > 0.015) {
        orbDepth = Math.min(1.0, orbDepth + delta * 3 * 1.5)
      }
      orbDepth = 0.3 + (orbDepth - 0.3) * 0.92
      smoothedDepth += (orbDepth - smoothedDepth) * 0.5
    }

    if (orbCtx) {
      drawOrb(orbCtx, orbCanvas.width, orbCanvas.height, smoothedDepth, orbPhases)
    }

    if (isRecording.value) {
      animationFrameId = requestAnimationFrame(render)
    }
  }
  render()
}

// 将 Float32 PCM 样本编码为标准 WAV 文件
const encodeWAV = (samples, sampleRate) => {
  const buffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(buffer)
  const writeStr = (offset, str) => { for (let i = 0; i < str.length; i++) view.setUint8(offset + i, str.charCodeAt(i)) }
  writeStr(0, 'RIFF')
  view.setUint32(4, 36 + samples.length * 2, true)
  writeStr(8, 'WAVE')
  writeStr(12, 'fmt ')
  view.setUint32(16, 16, true)       // chunk size
  view.setUint16(20, 1, true)        // PCM format
  view.setUint16(22, 1, true)        // mono
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true) // byte rate
  view.setUint16(32, 2, true)        // block align
  view.setUint16(34, 16, true)       // bits per sample
  writeStr(36, 'data')
  view.setUint32(40, samples.length * 2, true)
  // 写入 16-bit PCM 样本
  for (let i = 0; i < samples.length; i++) {
    const s = Math.max(-1, Math.min(1, samples[i]))
    view.setInt16(44 + i * 2, s < 0 ? s * 0x8000 : s * 0x7FFF, true)
  }
  return new Blob([buffer], { type: 'audio/wav' })
}

// 将 48kHz 下采样到 16kHz
const downsample = (buffer, fromRate, toRate) => {
  if (fromRate === toRate) return buffer
  const ratio = fromRate / toRate
  const newLength = Math.round(buffer.length / ratio)
  const result = new Float32Array(newLength)
  for (let i = 0; i < newLength; i++) {
    result[i] = buffer[Math.round(i * ratio)]
  }
  return result
}

const startRecording = async (target) => {
  if (isRecording.value) return
  
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: { sampleRate: 16000, channelCount: 1 } })
    audioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 16000 })
    sourceNode = audioContext.createMediaStreamSource(stream)
    
    // Web Audio Analyzer for Organic Orb Reactivity
    analyserNode = audioContext.createAnalyser()
    analyserNode.fftSize = 256
    // 我们主要关注人声频率，设置平滑度让线条更少抖动
    analyserNode.smoothingTimeConstant = 0.8
    sourceNode.connect(analyserNode)
    
    const dataArray = new Uint8Array(analyserNode.frequencyBinCount)
    orbDepth = 0.3
    smoothedDepth = 0.3
    prevEnergy = 0
    smoothedEnergy = 0
    
    // 等待 Vue DOM 更新后获取 Canvas
    setTimeout(() => {
      orbCanvas = document.getElementById('orb-canvas')
      if (orbCanvas) {
        orbCtx = orbCanvas.getContext('2d')
        orbCanvas.width = 260
        orbCanvas.height = 260
      }
      startOrbAnimation(dataArray)
    }, 200)

    processorNode = audioContext.createScriptProcessor(4096, 1, 1)
    pcmBuffers = []

    processorNode.onaudioprocess = (e) => {
      const data = e.inputBuffer.getChannelData(0)
      pcmBuffers.push(new Float32Array(data))
    }

    sourceNode.connect(processorNode)
    processorNode.connect(audioContext.destination)
    isRecording.value = target
  } catch (err) {
    console.error("Microphone access denied or error:", err)
    showToast('无法访问麦克风，请检查权限设置', 'error')
  }
}

const stopRecording = async () => {
  if (!isRecording.value) return
  const target = isRecording.value
  isRecording.value = false

  if (animationFrameId) { cancelAnimationFrame(animationFrameId); animationFrameId = null }
  if (processorNode) { processorNode.disconnect(); processorNode = null }
  if (analyserNode) { analyserNode.disconnect(); analyserNode = null }
  if (sourceNode) { sourceNode.disconnect(); sourceNode = null }
  if (stream) { stream.getTracks().forEach(t => t.stop()); stream = null }

  // 合并所有 PCM buffer
  const totalLength = pcmBuffers.reduce((acc, b) => acc + b.length, 0)
  const merged = new Float32Array(totalLength)
  let offset = 0
  for (const buf of pcmBuffers) { merged.set(buf, offset); offset += buf.length }
  pcmBuffers = []

  // 下采样到 16kHz 并编码为 WAV
  const actualRate = audioContext ? audioContext.sampleRate : 16000
  const samples16k = downsample(merged, actualRate, 16000)
  if (audioContext) { audioContext.close(); audioContext = null }

  const wavBlob = encodeWAV(samples16k, 16000)
  
  // 发送到后端
  isAudioProcessing.value = true
  const formData = new FormData()
  formData.append('audio', wavBlob, 'recording.wav')
  formData.append('dialect', settingsStore.selectedDialect)

  try {
    const response = await axios.post('/api/ai/speech-to-text', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    const text = response.data.text || ''
    // 后端将所有错误/状态消息都作为 text 返回，需要过滤
    const backendErrorPrefixes = [
      '请在 application',
      '语音未识别到有效内容',
      '语音识别超时',
      '语音识别失败',
    ]
    const isBackendError = backendErrorPrefixes.some(prefix => text.startsWith(prefix))
    
    if (text && !isBackendError) {
      if (target === 'general') {
        generalUserInput.value = (generalUserInput.value + ' ' + text).trim()
      } else {
        userInput.value = (userInput.value + ' ' + text).trim()
      }
    } else {
      // 所有后端错误/空结果统一走 Toast
      showToast(isBackendError ? text : '语音未识别到有效内容，请重试', isBackendError && text.includes('失败') ? 'error' : 'warning', 4000)
    }
  } catch (err) {
    console.error("Speech to text translation failed:", err)
    showToast('语音识别失败，请重试', 'error')
  } finally {
    isAudioProcessing.value = false
  }
}

const handleVoiceStart = (target) => {
  if (isRecording.value) return
  startRecording(target)
}

const handleVoiceStop = () => {
  if (isRecording.value) stopRecording()
}

// 点击切换录音（对话页麦克风按钮使用）
const toggleVoiceInput = (target) => {
  if (isRecording.value === target) {
    stopRecording()
  } else if (!isRecording.value) {
    startRecording(target)
  }
}

// 真实 AI 结果数据
const detectionResult = ref({
  pestName: '',
  confidence: 0,
  imageUrl: ''
})

// 识别历史记录
const historyItems = ref(JSON.parse(localStorage.getItem('leafquery_history') || '[]'))

onMounted(async () => {
  try {
    const userStr = localStorage.getItem('user')
    if (userStr) {
      const user = JSON.parse(userStr)
      if (user && user.userId) {
        const response = await axios.get(`/api/record/list?userId=${user.userId}`)
        if (response.data && response.data.code === 200) {
          historyItems.value = response.data.data.map(item => ({
            id: item.id,
            name: item.pestName,
            confidence: item.confidence,
            time: item.createTime,
            imageUrl: item.imageUrl
          }))
        }
      }
    }
  } catch (e) {
    console.error('Failed to sync history from cloud', e)
  }

  // 检查是否有未读通知 → 显示红点
  try {
    const { data } = await axios.get('/api/discovery/announcements')
    if (data.code === 200 && data.data.length > 0) {
      const readIds = JSON.parse(localStorage.getItem('read_announcements') || '[]')
      const hasUnread = data.data.some(a => !readIds.includes(a.id))
      hasNewNotification.value = hasUnread
    }
  } catch (e) {
    // 静默失败，不影响主功能
  }

  // 检查是否有弹窗公告
  try {
    const { data: popupData } = await axios.get('/api/discovery/announcements/popup')
    if (popupData.code === 200 && popupData.data.length > 0) {
      const dismissedIds = JSON.parse(localStorage.getItem('dismissed_popups') || '[]')
      const unDismissed = popupData.data.find(a => !dismissedIds.includes(a.id))
      if (unDismissed) {
        popupAnnouncement.value = unDismissed
      }
    }
  } catch (e) {
    // 静默失败
  }
})

function dismissPopup() {
  if (popupAnnouncement.value) {
    const dismissedIds = JSON.parse(localStorage.getItem('dismissed_popups') || '[]')
    dismissedIds.push(popupAnnouncement.value.id)
    localStorage.setItem('dismissed_popups', JSON.stringify(dismissedIds))
    popupAnnouncement.value = null
  }
}

// 置信度百分比显示
const confidencePercent = computed(() => {
  return (detectionResult.value.confidence * 100).toFixed(1)
})

// 根据置信度返回颜色方案
const confidenceColor = computed(() => {
  const c = detectionResult.value.confidence
  if (c >= 0.8) return { text: 'text-green-600', bg: 'bg-green-500', glow: 'shadow-green-500/40', icon: 'bg-green-50 text-green-500', border: 'border-green-500' }
  if (c >= 0.5) return { text: 'text-orange-500', bg: 'bg-orange-500', glow: 'shadow-orange-500/40', icon: 'bg-orange-50 text-orange-500', border: 'border-orange-500' }
  return { text: 'text-red-500', bg: 'bg-red-500', glow: 'shadow-red-500/40', icon: 'bg-red-50 text-red-500', border: 'border-red-500' }
})

const handleScanComplete = async (result) => {
  detectionResult.value = result
  isResultReady.value = true
  showDetail.value = false
  messages.value = []

  const newRecord = {
    id: Date.now(),
    name: result.pestName,
    confidence: result.confidence,
    time: Date.now(), // Store as timestamp for better sorting/formatting later
    imageUrl: result.imageUrl
  }

  // 添加到历史记录
  historyItems.value.unshift(newRecord)

  // 首页栏最多显示 10 条, localStorage 最多存 100 条
  if (historyItems.value.length > 100) historyItems.value.pop()
  localStorage.setItem('leafquery_history', JSON.stringify(historyItems.value))

  // 同步至云端
  try {
    const userStr = localStorage.getItem('user')
    if (userStr) {
      const user = JSON.parse(userStr)
      if (user && user.userId) {
        await axios.post('/api/record/add', {
          userId: user.userId,
          pestName: result.pestName,
          confidence: result.confidence,
          imageUrl: result.imageUrl
        })
      }
    }
  } catch (e) {
    console.error("Failed to sync record to cloud:", e)
  }
}

const closeResult = () => {
  stopSpeaking()
  isResultReady.value = false
  showDetail.value = false
}

const fetchAiAnalysis = async () => {
  if (detectionResult.value.pestName === 'unknown' || detectionResult.value.pestName === '未识别') {
    showToast('无法对未识别的病虫害提供分析建议', 'warning')
    return
  }

  isAnalyzing.value = true
  showDetail.value = true
  messages.value = [] // Clear previous chat
  
  try {
    const response = await axios.post('/api/ai/analyze', {
      pestName: detectionResult.value.pestName
    })
    messages.value.push({ role: 'assistant', content: response.data.analysis })
  } catch (err) {
    console.error('Failed to fetch AI analysis', err)
    showToast('暂时无法获取 AI 分析结果，请稍后再试', 'error')
  } finally {
    isAnalyzing.value = false
    scrollToBottom()
  }
}

// 继续对话
const sendMessage = async () => {
  if (!(userInput.value.trim() || pendingImage.value) || isSending.value) return
  
  const text = userInput.value.trim()
  userInput.value = ''
  
  // 添加用户消息
  messages.value.push({ role: 'user', content: text, imageBase64: pendingImage.value })
  scrollToBottom()
  
  isSending.value = true
  pendingImage.value = ''

  try {
    const response = await axios.post('/api/ai/chat', {
      pestName: detectionResult.value.pestName,
      messages: messages.value.map(m => ({ role: m.role, content: m.content, imageBase64: m.imageBase64 }))
    })
    messages.value.push({ role: 'assistant', content: response.data.reply })
  } catch (err) {
    console.error('Failed to send message', err)
    showToast('发送消息失败，请重试', 'error')
  } finally {
    isSending.value = false
    scrollToBottom()
  }
}

// 开启通用咨询对话
const startGeneralChat = () => {
  if (!(generalUserInput.value.trim() || pendingGeneralImage.value) || isSending.value) return
  
  detectionResult.value = {
    pestName: '通用农业咨询',
    confidence: 1,
    imageUrl: ''
  }
  isResultReady.value = true
  showDetail.value = true
  messages.value = []
  
  userInput.value = generalUserInput.value.trim()
  generalUserInput.value = ''
  pendingImage.value = pendingGeneralImage.value
  pendingGeneralImage.value = ''
  
  sendMessage()
}

// 自动滚动到底部
const scrollToBottom = () => {
  setTimeout(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  }, 100)
}

// 简单的 Markdown 渲染（粗略处理 headers 和 lists）
const renderMarkdown = (text) => {
  if (!text) return ''
  let html = text
  html = html.replace(/### (.*)/g, '<h4 class="font-bold text-slate-800 mt-4 mb-2 text-md">$1</h4>')
  html = html.replace(/## (.*)/g, '<h3 class="font-bold text-slate-900 mt-6 mb-3 text-lg border-b pb-2 flex items-center"><span class="w-1.5 h-5 bg-green-500 rounded-full mr-2"></span>$1</h3>')
  html = html.replace(/\*\*(.*?)\*\*/g, '<span class="font-bold text-slate-800">$1</span>')
  html = html.replace(/- (.*)/g, '<li class="ml-4 list-disc marker:text-green-500 mb-1">$1</li>')
  html = html.replace(/\n\n/g, '<br/>')
  return html
}

// 历史条目的 emoji 图标
const getEmoji = (name) => {
  if (name === 'unknown' || name === '未识别') return '❓'
  return '🍃'
}

// 格式化历史时间
const formatTime = (item) => {
  const timestamp = item.time
  if (!timestamp || isNaN(Number(timestamp))) return item.time || '刚刚'
  
  const date = new Date(Number(timestamp))
  const now = new Date()
  const diff = now - date
  
  if (date.toDateString() === now.toDateString()) {
    if (diff < 60000) return '刚刚'
    if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
    return `${Math.floor(diff / 3600000)}小时前`
  }
  
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (date.toDateString() === yesterday.toDateString()) {
    return `昨天 ${date.getHours().toString().padStart(2, '0')}:${date.getMinutes().toString().padStart(2, '0')}`
  }
  
  return `${date.getMonth() + 1}月${date.getDate()}日`
}
</script>

<template>
  <div class="px-6 pt-4 min-h-full flex flex-col relative">
    
    <!-- Toast 弹窗通知 -->
    <Transition
      enter-active-class="transition duration-300 ease-out"
      enter-from-class="opacity-0 -translate-y-4"
      enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition duration-200 ease-in"
      leave-from-class="opacity-100 translate-y-0"
      leave-to-class="opacity-0 -translate-y-4"
    >
      <div v-if="toastVisible" class="fixed top-12 left-1/2 -translate-x-1/2 z-[100] max-w-[85vw]">
        <div class="flex items-center gap-3 px-5 py-3 rounded-2xl shadow-xl backdrop-blur-xl border"
             :class="{
               'bg-red-50/90 border-red-200 text-red-700': toastType === 'error',
               'bg-amber-50/90 border-amber-200 text-amber-700': toastType === 'warning',
               'bg-blue-50/90 border-blue-200 text-blue-700': toastType === 'info'
             }">
          <span class="text-lg shrink-0">{{ toastType === 'error' ? '❌' : toastType === 'warning' ? '⚠️' : 'ℹ️' }}</span>
          <span class="text-sm font-medium">{{ toastMessage }}</span>
          <button @click="toastVisible = false" class="ml-2 shrink-0 opacity-50 hover:opacity-100 text-lg leading-none">&times;</button>
        </div>
      </div>
    </Transition>
    <!-- SVG 滤镜定义 (用于发光) -->
    <svg width="0" height="0" style="position:absolute">
      <filter id="orb-glow" x="-100%" y="-100%" width="300%" height="300%">
        <feGaussianBlur in="SourceGraphic" stdDeviation="20" result="blur" />
        <feComponentTransfer in="blur" result="g">
          <feFuncA type="linear" slope="0.6" />
        </feComponentTransfer>
        <feMerge>
          <feMergeNode in="g" />
          <feMergeNode in="SourceGraphic" />
        </feMerge>
      </filter>
    </svg>

    <!-- 全屏录音毛玻璃遮罩层 -->
    <Transition
      enter-active-class="transition duration-300 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-200 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div v-if="isRecording" class="fixed inset-0 z-[60] bg-slate-900/50 backdrop-blur-md flex flex-col items-center justify-center selection:bg-transparent">
        <div class="relative flex items-center justify-center" style="width:260px;height:260px;">
          <!-- Organic AI Orb: Canvas 实现 (忠实复刻 CodePen 数学公式) -->
          <canvas id="orb-canvas" width="260" height="260" class="absolute inset-0"></canvas>
          
          <!-- 悬浮的麦克风 Icon（绝对居中） -->
          <div class="absolute inset-0 flex items-center justify-center z-10">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="white" class="w-8 h-8 drop-shadow-lg">
              <path stroke-linecap="round" stroke-linejoin="round" d="M12 18.75a6 6 0 0 0 6-6v-1.5m-6 7.5a6 6 0 0 1-6-6v-1.5m6 7.5v3.75m-3.75 0h7.5M12 15.75a3 3 0 0 1-3-3V4.5a3 3 0 1 1 6 0v8.25a3 3 0 0 1-3 3Z" />
            </svg>
          </div>
        </div>
        <p class="mt-8 text-white text-lg font-medium tracking-wide drop-shadow-md z-10">正在聆听您的声音...</p>
        <p class="mt-2 text-white/70 text-sm font-light z-10">松开手指完成识别</p>
      </div>
    </Transition>

    <!-- Header -->
    <div class="flex justify-between items-center mb-8">
      <div>
        <h1 class="text-3xl font-bold text-slate-900 tracking-tight" v-motion-slide-visible-once-bottom>识别</h1>
        <p class="text-slate-500 text-sm mt-1 font-medium" v-motion-slide-visible-once-bottom :delay="100">AI 智能诊断助手</p>
      </div>
      <button @click="router.push('/notifications')" class="w-12 h-12 bg-white rounded-2xl flex items-center justify-center text-slate-800 shadow-sm border border-slate-100 hover:shadow-md transition-shadow relative">
        🔔
        <span v-if="hasNewNotification" class="absolute -top-1 -right-1 w-3.5 h-3.5 bg-red-500 rounded-full border-2 border-white shadow-sm animate-pulse"></span>
      </button>
    </div>
    
    <!-- Main Scan Area -->
    <div class="flex-1 w-full min-h-[400px] bg-slate-900 rounded-[2rem] shadow-2xl shadow-slate-900/20 overflow-hidden relative border border-slate-800 mb-8 transform transition-transform active:scale-[0.99]" v-motion-pop-visible-once :delay="200">
      <ScanOverlay ref="scanRef" @scan-complete="handleScanComplete" />
    </div>

    <!-- Recent History -->
    <div class="mb-4" v-motion-slide-visible-once-bottom :delay="300">
      <div class="flex justify-between items-center mb-4 px-1">
        <h3 class="font-bold text-slate-800 text-lg">最近记录</h3>
        <button @click="router.push('/records')" class="text-green-600 text-sm font-semibold">全部</button>
      </div>
      
      <div class="flex space-x-4 overflow-x-auto pb-2 -mx-6 px-6 scrollbar-hide">
        <!-- 空状态 -->
        <div v-if="historyItems.length === 0" v-for="i in 3" :key="'placeholder-' + i" class="flex-shrink-0 w-24 h-28 bg-white rounded-2xl p-3 border border-slate-100 shadow-[0_4px_20px_rgb(0,0,0,0.03)] flex flex-col items-center justify-center space-y-2 relative overflow-hidden">
          <div class="w-10 h-10 bg-slate-50 text-slate-300 rounded-full flex items-center justify-center text-lg">📷</div>
          <div class="text-center">
             <span class="block text-xs font-bold text-slate-300">待识别</span>
             <span class="block text-[10px] text-slate-300 font-medium mt-1">--</span>
          </div>
        </div>

        <!-- 真实历史记录 -->
        <div
          v-for="item in historyItems"
          :key="item.id"
          class="flex-shrink-0 w-24 h-28 bg-white rounded-2xl p-3 border border-slate-100 shadow-[0_4px_20px_rgb(0,0,0,0.03)] flex flex-col items-center justify-center space-y-2 transition-all duration-300 hover:shadow-md"
        >
          <div class="w-10 h-10 bg-green-50 text-green-500 rounded-full flex items-center justify-center text-lg overflow-hidden shrink-0">
            <img v-if="item.imageUrl && item.imageUrl.trim() !== ''" :src="item.imageUrl" class="w-full h-full object-cover" @error="$event.target.style.display='none'; $event.target.nextElementSibling.style.display='inline'" />
            <span v-show="!item.imageUrl || item.imageUrl.trim() === ''">{{ getEmoji(item.name) }}</span>
          </div>
          <div class="text-center w-full">
             <span class="block text-xs font-bold text-slate-800 truncate w-full">{{ item.name }}</span>
             <span class="block text-[10px] text-slate-400 font-medium mt-1">{{ formatTime(item) }}</span>
          </div>
        </div>
      </div>
    </div>
    
    <!-- 首页直接提问框：有任何病害相关问题可以直接问 AI -->
    <div class="mb-24 flex flex-col relative" v-motion-slide-visible-once-bottom :delay="400">
      <!-- 待发送图片预览区 -->
      <div v-if="pendingGeneralImage" class="mb-2 relative w-16 h-16 ml-3">
        <img :src="pendingGeneralImage" class="w-full h-full object-cover rounded border border-slate-200 shadow-sm" />
        <button @click="removePendingGeneralImage" class="absolute -top-2 -right-2 bg-red-500/90 text-white rounded-full w-5 h-5 flex items-center justify-center text-xs pb-[1px] hover:bg-red-600">×</button>
      </div>

      <div class="flex items-center space-x-3 w-full">
        <form @submit.prevent="startGeneralChat" class="relative flex items-center flex-1">
          <!-- 隐藏的文件选择器 -->
          <input type="file" accept="image/*" class="hidden" ref="generalImageInputRef" @change="onGeneralImageSelected" />
          
          <!-- 左侧添加图片按钮 -->
          <button type="button" @click="triggerGeneralImageUpload" class="absolute left-2 w-10 h-10 flex items-center justify-center text-slate-400 hover:text-green-500 transition-colors z-10" :disabled="isSending">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-6 h-6">
              <path stroke-linecap="round" stroke-linejoin="round" d="m2.25 15.75 5.159-5.159a2.25 2.25 0 0 1 3.182 0l5.159 5.159m-1.5-1.5 1.409-1.409a2.25 2.25 0 0 1 3.182 0l2.909 2.909m-18 3.75h16.5a1.5 1.5 0 0 0 1.5-1.5V6a1.5 1.5 0 0 0-1.5-1.5H3.75A1.5 1.5 0 0 0 2.25 6v12a1.5 1.5 0 0 0 1.5 1.5Zm10.5-11.25h.008v.008h-.008V8.25Zm.375 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Z" />
            </svg>
          </button>

          <!-- 文本输入框 -->
          <input 
            v-model="generalUserInput" 
            type="text" 
            :placeholder="isRecording === 'general' ? '正在聆听您的声音...' : '输入病害名称提问'" 
            class="w-full bg-white border border-slate-200 text-slate-800 rounded-full pl-12 pr-14 py-3.5 focus:outline-none focus:ring-2 focus:ring-green-400 focus:border-transparent transition-all shadow-[0_4px_20px_rgb(0,0,0,0.04)]"
            :disabled="isSending"
          />
          <!-- 发送按钮 -->
          <button 
            type="submit" 
            class="absolute py-1 px-3 right-1.5 top-1.5 bottom-1.5 bg-green-500 hover:bg-green-600 disabled:bg-slate-300 text-white rounded-full flex items-center justify-center transition-colors shadow-sm"
            :disabled="!(generalUserInput.trim() || pendingGeneralImage) || isSending"
          >
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-5 h-5 -rotate-45 ml-1 mb-1">
              <path d="M3.478 2.404a.75.75 0 00-.926.941l2.432 7.905H13.5a.75.75 0 010 1.5H4.984l-2.432 7.905a.75.75 0 00.926.94 60.519 60.519 0 0018.445-8.986.75.75 0 000-1.218A60.517 60.517 0 003.478 2.404z" />
            </svg>
          </button>
        </form>

        <!-- 按住说话语音按钮 -->
        <button 
          type="button" 
          @touchstart.prevent="handleVoiceStart('general')"
          @touchend.prevent="handleVoiceStop()"
          @mousedown.prevent="handleVoiceStart('general')"
          @mouseup.prevent="handleVoiceStop()"
          @mouseleave="handleVoiceStop()"
          class="shrink-0 w-12 h-12 rounded-full flex items-center justify-center transition-all bg-white border border-slate-200 shadow-md relative select-none z-50"
          :class="isRecording === 'general' ? 'border-green-400 bg-green-500 text-white shadow-green-500/50 scale-110' : 'text-slate-600 hover:text-green-500 active:scale-95'"
          :disabled="isSending || isAudioProcessing"
        >
          <!-- 取消小按钮自身的发光动画，因为大遮罩层已经有了 -->
          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-6 h-6 relative z-10 transition-transform">
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 18.75a6 6 0 0 0 6-6v-1.5m-6 7.5a6 6 0 0 1-6-6v-1.5m6 7.5v3.75m-3.75 0h7.5M12 15.75a3 3 0 0 1-3-3V4.5a3 3 0 1 1 6 0v8.25a3 3 0 0 1-3 3Z" />
          </svg>
        </button>
      </div>
    </div>
    
    <!-- Result Modal -->
    <Transition
      enter-active-class="transition duration-300 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition duration-200 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div v-if="isResultReady" class="absolute inset-0 z-50 flex items-end" @click="closeResult">
        <div class="absolute inset-0 bg-slate-900/40 backdrop-blur-sm"></div>
        <Transition
          enter-active-class="transition duration-400 ease-out"
          enter-from-class="translate-y-full"
          enter-to-class="translate-y-0"
          leave-active-class="transition duration-300 ease-in"
          leave-from-class="translate-y-0"
          leave-to-class="translate-y-full"
        >
          <div v-if="isResultReady" class="relative z-10 bg-white w-full rounded-t-[2.5rem] px-8 pt-8 pb-24 h-[75%] sm:h-[80%] shadow-[0_-10px_40px_rgba(0,0,0,0.1)] flex flex-col" @click.stop>
            <div class="w-16 h-1.5 bg-slate-100 rounded-full mx-auto mb-8 shrink-0"></div>
            <div v-if="!showDetail" class="flex-1 flex flex-col min-h-0">
              <div class="flex-1 overflow-y-auto custom-scrollbar pr-2 mb-2">
                <div class="text-center mb-2">
                  <!-- 结果图标 -->
                  <div
                    class="w-20 h-20 rounded-full mx-auto flex items-center justify-center text-4xl mb-4 border-4 border-white shadow-xl transition-all duration-500"
                    :class="[confidenceColor.icon, `shadow-${confidenceColor.glow}`]"
                  >
                    <img v-if="detectionResult.imageUrl" :src="detectionResult.imageUrl" class="w-full h-full object-cover rounded-full" />
                    <span v-else>✓</span>
                  </div>
                  <h2 class="text-2xl font-bold text-slate-900 mb-1">识别完成</h2>
                  <p class="text-slate-500 font-medium mb-4">
                    检测对象：<span class="text-slate-800 font-bold">{{ detectionResult.pestName }}</span>
                  </p>
                </div>
                
                <!-- 置信度进度条 -->
                <div class="bg-slate-50 rounded-2xl p-4 border border-slate-100/50">
                  <div class="flex justify-between items-center mb-2">
                    <span class="text-slate-500 text-sm font-medium">AI 置信度</span>
                    <span class="font-bold text-base" :class="confidenceColor.text">{{ confidencePercent }}%</span>
                  </div>
                  <div class="w-full bg-slate-200 rounded-full h-2 overflow-hidden">
                    <div
                      class="h-full rounded-full transition-all duration-1000 ease-out"
                      :class="confidenceColor.bg"
                      :style="{ width: confidencePercent + '%', boxShadow: '0 0 10px currentColor' }"
                    ></div>
                  </div>
                </div>
              </div>
              
              <div class="shrink-0">
                <button @click="fetchAiAnalysis" class="w-full bg-slate-900 text-white py-3.5 sm:py-4 rounded-xl font-bold text-base shadow-lg shadow-slate-900/20 active:scale-[0.98] transition-all flex justify-center items-center space-x-2">
                  <span>✨</span>
                  <span>生成详细防范建议</span>
                </button>
              </div>
            </div>

            <!-- 详细分析视图 -->
            <div v-else class="flex-1 flex flex-col min-h-0">
              <div class="flex items-center justify-between mb-4 pb-4 border-b">
                <button v-if="detectionResult.pestName !== '通用农业咨询'" @click="showDetail = false" class="text-slate-400 font-bold px-2 py-1 bg-slate-100 rounded-lg text-sm">返回</button>
                <button v-else @click="closeResult" class="text-slate-400 font-bold px-2 py-1 bg-slate-100 rounded-lg text-sm">关闭</button>
                <h2 class="text-xl font-bold text-slate-900 truncate flex-1 text-center px-4">
                  {{ detectionResult.pestName === '通用农业咨询' ? 'AI 诊断咨询' : detectionResult.pestName + ' 分析报告' }}
                </h2>
                <!-- 右上角朗读按钮 -->
                <button 
                  @click="speakLastReply" 
                  class="w-10 h-10 rounded-full flex items-center justify-center transition-all"
                  :class="isSpeaking ? 'bg-green-100 text-green-600' : isTtsLoading ? 'bg-slate-100 text-slate-400' : 'text-slate-400 hover:text-green-500 hover:bg-green-50'"
                  :disabled="isTtsLoading && !isSpeaking"
                  title="朗读 AI 回复"
                >
                  <!-- 加载中 -->
                  <svg v-if="isTtsLoading && !isSpeaking" class="w-5 h-5 animate-spin" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  <!-- 播放中 -->
                  <svg v-else-if="isSpeaking" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-5 h-5 animate-pulse">
                    <path d="M13.5 4.06c0-1.336-1.616-2.005-2.56-1.06l-4.5 4.5H4.508c-1.141 0-2.318.664-2.66 1.905A9.76 9.76 0 001.5 12c0 .898.121 1.768.35 2.595.341 1.24 1.518 1.905 2.659 1.905h1.93l4.5 4.5c.945.945 2.561.276 2.561-1.06V4.06zM18.584 5.106a.75.75 0 011.06 0c3.808 3.807 3.808 9.98 0 13.788a.75.75 0 01-1.06-1.06 8.25 8.25 0 000-11.668.75.75 0 010-1.06z" />
                    <path d="M15.932 7.757a.75.75 0 011.061 0 6 6 0 010 8.486.75.75 0 01-1.06-1.061 4.5 4.5 0 000-6.364.75.75 0 010-1.06z" />
                  </svg>
                  <!-- 默认状态 -->
                  <svg v-else xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M19.114 5.636a9 9 0 010 12.728M16.463 8.288a5.25 5.25 0 010 7.424M6.75 8.25l4.72-4.72a.75.75 0 011.28.53v15.88a.75.75 0 01-1.28.53l-4.72-4.72H4.51c-.88 0-1.704-.507-1.938-1.354A9.01 9.01 0 012.25 12c0-.83.112-1.633.322-2.396C2.806 8.756 3.63 8.25 4.51 8.25H6.75z" />
                  </svg>
                </button>
              </div>
              
              <div ref="chatContainer" class="flex-1 overflow-y-auto pb-4 custom-scrollbar px-2 space-y-6">
                <!-- 初始的思考动画 -->
                <div v-if="isAnalyzing && messages.length === 0" class="flex flex-col items-center justify-center py-20">
                   <div class="w-16 h-16 relative mb-6">
                     <div class="absolute inset-0 border-4 border-green-100 rounded-full"></div>
                     <div class="absolute inset-0 border-4 border-green-500 rounded-full border-t-transparent animate-spin"></div>
                     <div class="absolute inset-0 flex items-center justify-center text-xl">✨</div>
                   </div>
                   <h3 class="text-lg font-bold text-slate-800 mb-2 animate-pulse">豆包 AI 思考中</h3>
                   <p class="text-sm text-slate-500">正在查阅农业知识库生成防治方案...</p>
                </div>
                
                <!-- 聊天历史列表 -->
                <div v-for="(msg, index) in messages" :key="index" class="flex w-full" :class="msg.role === 'user' ? 'justify-end' : 'justify-start'">
                  <!-- AI 气泡 -->
                  <div v-if="msg.role === 'assistant'" class="flex items-start space-x-3 max-w-[95%]">
                    <div class="w-8 h-8 rounded-full bg-slate-900 text-white flex items-center justify-center text-xs shrink-0 shadow-md">
                      AI
                    </div>
                    <div class="bg-slate-50 border border-slate-100 rounded-2xl rounded-tl-sm px-5 py-4 shadow-sm">
                      <div class="markdown-body text-slate-700 text-sm leading-relaxed" v-html="renderMarkdown(msg.content)"></div>
                    </div>
                  </div>
                  
                  <!-- 用户气泡 -->
                  <div v-else class="flex items-end space-x-2 max-w-[85%]">
                    <div class="bg-green-500 text-white rounded-2xl rounded-br-sm shadow-md overflow-hidden relative group" :class="[msg.imageBase64 ? 'p-1' : 'px-5 py-3']">
                      <img v-if="msg.imageBase64" :src="msg.imageBase64" class="w-full max-w-[200px] object-cover rounded-[10px]" :class="msg.content ? 'mb-2' : ''" />
                      <div v-if="msg.content" :class="msg.imageBase64 ? 'px-3 pb-2 pt-1' : ''">
                        <p class="text-sm leading-relaxed whitespace-pre-wrap">{{ msg.content }}</p>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 发送消息时的等待动画 -->
                <div v-if="isSending" class="flex items-start space-x-3 max-w-[95%]">
                   <div class="w-8 h-8 rounded-full bg-slate-900 text-white flex items-center justify-center text-xs shrink-0 shadow-md">
                     AI
                   </div>
                   <div class="bg-slate-50 border border-slate-100 rounded-2xl rounded-tl-sm px-5 py-4 shadow-sm flex items-center space-x-1">
                     <div class="w-2 h-2 bg-slate-400 rounded-full animate-bounce" style="animation-delay: 0ms"></div>
                     <div class="w-2 h-2 bg-slate-400 rounded-full animate-bounce" style="animation-delay: 150ms"></div>
                     <div class="w-2 h-2 bg-slate-400 rounded-full animate-bounce" style="animation-delay: 300ms"></div>
                   </div>
                </div>
              </div>

              <!-- 底部吸底输入框 (带有图文混合功能) -->
              <div class="shrink-0 pt-3 mt-1 bg-white border-t border-slate-100 flex flex-col relative w-full">
                <!-- 待发送图片预览区 -->
                <div v-if="pendingImage" class="mb-2 relative w-16 h-16 ml-3">
                  <img :src="pendingImage" class="w-full h-full object-cover rounded border border-slate-200 shadow-sm" />
                  <button @click="removePendingImage" class="absolute -top-2 -right-2 bg-red-500/90 text-white rounded-full w-5 h-5 flex items-center justify-center text-xs pb-[1px] hover:bg-red-600">×</button>
                </div>

                <div class="flex items-center space-x-2 w-full">
                  <form @submit.prevent="sendMessage" class="relative flex items-center flex-1">
                    <!-- 隐藏的文件选择器 -->
                    <input type="file" accept="image/*" class="hidden" ref="imageInputRef" @change="onImageSelected" />
                    
                    <!-- 左侧添加图片按钮 -->
                    <button type="button" @click="triggerImageUpload" class="absolute left-2 w-10 h-10 flex items-center justify-center text-slate-400 hover:text-green-500 transition-colors z-10" :disabled="isSending || isAnalyzing">
                      <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-6 h-6">
                        <path stroke-linecap="round" stroke-linejoin="round" d="m2.25 15.75 5.159-5.159a2.25 2.25 0 0 1 3.182 0l5.159 5.159m-1.5-1.5 1.409-1.409a2.25 2.25 0 0 1 3.182 0l2.909 2.909m-18 3.75h16.5a1.5 1.5 0 0 0 1.5-1.5V6a1.5 1.5 0 0 0-1.5-1.5H3.75A1.5 1.5 0 0 0 2.25 6v12a1.5 1.5 0 0 0 1.5 1.5Zm10.5-11.25h.008v.008h-.008V8.25Zm.375 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Z" />
                      </svg>
                    </button>

                    <!-- 文本输入框 -->
                    <input 
                      v-model="userInput" 
                      type="text" 
                      :placeholder="isRecording === 'chat' ? '倾听中...' : '附图追问大模型...'" 
                      class="w-full bg-slate-50 border border-slate-200 text-slate-800 rounded-full pl-12 pr-14 py-3.5 focus:outline-none focus:ring-2 focus:ring-green-400 focus:border-transparent transition-all"
                      :disabled="isSending || isAnalyzing"
                    />
                    <!-- 发送按钮 -->
                    <button 
                      type="submit" 
                      class="absolute py-1 px-3 right-1.5 top-1.5 bottom-1.5 bg-green-500 hover:bg-green-600 disabled:bg-slate-300 text-white rounded-full flex items-center justify-center transition-colors shadow-sm"
                      :disabled="!(userInput.trim() || pendingImage) || isSending || isAnalyzing"
                    >
                      <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-5 h-5 -rotate-45 ml-1 mb-1">
                        <path d="M3.478 2.404a.75.75 0 00-.926.941l2.432 7.905H13.5a.75.75 0 010 1.5H4.984l-2.432 7.905a.75.75 0 00.926.94 60.519 60.519 0 0018.445-8.986.75.75 0 000-1.218A60.517 60.517 0 003.478 2.404z" />
                      </svg>
                    </button>
                  </form>

                  <!-- 外部独立的语音按钮（长按说话） -->
                  <button 
                    type="button" 
                    @touchstart.prevent="handleVoiceStart('chat')"
                    @touchend.prevent="handleVoiceStop()"
                    @mousedown.prevent="handleVoiceStart('chat')"
                    @mouseup.prevent="handleVoiceStop()"
                    class="shrink-0 w-12 h-12 rounded-full flex items-center justify-center transition-all bg-white border border-slate-200 shadow-sm relative"
                    :class="isRecording === 'chat' ? 'border-green-400 bg-green-500 text-white shadow-green-500/50 scale-105' : 'text-slate-600 hover:text-green-500 bg-slate-50'"
                    :disabled="isSending || isAnalyzing"
                  >
                    <span v-if="isRecording === 'chat'" class="absolute w-full h-full bg-green-400 rounded-full animate-ping opacity-75"></span>
                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-6 h-6 relative z-10 transition-transform" :class="isRecording === 'chat' ? 'animate-pulse' : ''">
                      <path stroke-linecap="round" stroke-linejoin="round" d="M12 18.75a6 6 0 0 0 6-6v-1.5m-6 7.5a6 6 0 0 1-6-6v-1.5m6 7.5v3.75m-3.75 0h7.5M12 15.75a3 3 0 0 1-3-3V4.5a3 3 0 1 1 6 0v8.25a3 3 0 0 1-3 3Z" />
                    </svg>
                  </button>
                </div>
              </div>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>

    <!-- Popup Announcement Modal -->
    <div v-if="popupAnnouncement" class="fixed inset-0 z-[100] flex items-center justify-center px-6">
      <div class="absolute inset-0 bg-black/50 backdrop-blur-sm" @click="dismissPopup"></div>
      <div class="bg-white w-full max-w-sm rounded-[2rem] overflow-hidden relative z-10 shadow-2xl popup-enter">
        <div class="bg-gradient-to-br from-green-500 to-emerald-600 px-6 py-6 text-white">
          <div class="flex items-center justify-between mb-3">
            <span class="bg-white/20 backdrop-blur-sm px-3 py-1 rounded-full text-xs font-bold">📢 系统公告</span>
            <button @click="dismissPopup" class="w-8 h-8 bg-white/20 backdrop-blur-sm rounded-full flex items-center justify-center text-white font-bold active:scale-90 transition-transform">✕</button>
          </div>
          <h2 class="text-xl font-bold leading-tight">{{ popupAnnouncement.title }}</h2>
        </div>
        <div class="px-6 py-5 max-h-[40vh] overflow-y-auto">
          <p class="text-slate-600 text-sm leading-relaxed whitespace-pre-wrap">{{ popupAnnouncement.content }}</p>
        </div>
        <div class="px-6 pb-6">
          <button @click="dismissPopup" class="w-full bg-slate-900 text-white py-3.5 rounded-2xl font-bold text-sm shadow-lg shadow-slate-900/20 active:scale-[0.98] transition-transform">我知道了</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
@keyframes popup-in { from { transform: scale(0.8); opacity: 0; } to { transform: scale(1); opacity: 1; } }
.popup-enter { animation: popup-in 0.35s cubic-bezier(0.16, 1, 0.3, 1); }
</style>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-track {
  background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 4px;
}
</style>
