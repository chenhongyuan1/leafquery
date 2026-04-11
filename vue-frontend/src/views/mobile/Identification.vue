<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { useSettingsStore } from '../../stores/settings'
import { AUTH_CHANGE_EVENT, getStoredUser } from '../../utils/accountSecurity'
import {
  dismissPopupAnnouncement,
  fetchAnnouncements,
  getLocalAnnouncementReadIds,
  getLocalDismissedPopupIds,
  isAnnouncementRead
} from '../../utils/announcementReadState'
import {
  formatConfidencePercent,
  getPrimaryDisplayName,
  getReportTitle,
  getSceneMeta,
  normalizeClassNames,
  normalizeDetectionSummary
} from '../../utils/detectionPresentationClean'

const router = useRouter()
const settingsStore = useSettingsStore()

// ===== Category Selection =====
const categoryOptions = [
  { key: 'rice',   label: '水稻', value: '水稻', icon: '🌾', group: 'disease' },
  { key: 'corn',   label: '玉米', value: '玉米', icon: '🌽', group: 'disease' },
  { key: 'wheat',  label: '小麦', value: '小麦', icon: '🌾', group: 'disease' },
  { key: 'other',  label: '其他', value: '其他', icon: '📋', group: 'disease' },
  { key: 'pest',   label: '虫害', value: '虫害', icon: '🐛', group: 'pest'    },
]
const selectedCategories = ref([])
const toggleCategory = (value) => {
  const idx = selectedCategories.value.indexOf(value)
  if (idx >= 0) {
    selectedCategories.value.splice(idx, 1)
  } else {
    selectedCategories.value.push(value)
  }
}
const hasSelection = computed(() => selectedCategories.value.length > 0)
const selectionConfirmed = ref(false)
const confirmSelection = () => {
  if (hasSelection.value) {
    selectionConfirmed.value = true
  }
}

// ===== Scan State =====
const fileInput = ref(null)
const previewUrl = ref(null)
const analysisStage = ref('')  // '' -> 'yolo' -> 'review' -> 'done'
const yoloUsed = ref(false)
const reviewRequired = ref(false)

const scanRef = ref(null)
const isResultReady = ref(false)
const showDetail = ref(false)
const isAnalyzing = ref(false)
const isDiagnosing = ref(false)
const isSending = ref(false)
const messages = ref([]) 
const userInput = ref('')
const selectedCropNames = ref([])
const selectedTargetNames = ref([])


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
    currentAudio.onerror = () => { stopSpeaking(); showToast('语音播放失败', 'error') }
    currentAudio.play()
  } catch (err) {
    console.error('TTS failed:', err)
    isTtsLoading.value = false
    showToast('语音合成失败，请稍后重试', 'error')
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

// 语音转文字 + 方言识别 (Web Audio API PCM 采集 + WAV 编码 -> 后端豆包  SeedASR)
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

// ============ Organic AI Orb Canvas Renderer (忠实复刻  CodePen 数学公式 ============
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

// 构建贝塞尔路径的辅助函数 (复用
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
  // depth 越大 -> 整体越大  + 形变越剧烈 (向外膨胀  + 不规则震动
  const scaleFactor = 0.5 + depth * 0.5
  const baseR = Math.min(w, h) / 2 * 0.85 * scaleFactor

  ctx.clearRect(0, 0, w, h)

  // ======== 第 1 层  外发光 Glow (feGaussianBlur 模拟 ========
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

  // ======== 第 2 层  主体填充 (Mesh Gradient) ========
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

  // ======== 第 3 层  噪点质感  Film Grain (feTurbulence 模拟 ========
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
    // 更新相位 (与 CodePen 一致的速率: 7s, 11s, 13s 周期
    orbPhases.s1 += (2 * Math.PI) / (7 * 60)
    orbPhases.s2 += (2 * Math.PI) / (11 * 60)
    orbPhases.s3 += (2 * Math.PI) / (13 * 60)
    orbPhases.rotation += (2 * Math.PI) / (20 * 60)

    // 音频分析(与 CodePen JS 完全一致的  RMS 能量检测
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

// 层 Float32 PCM 样本编码为标准 WAV 文件
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

// 层 48kHz 下采样到  16kHz
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

  // 下采样到  16kHz 并编码为  WAV
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
    // 后端会把错误/状态消息也放进 text 返回，这里先过滤掉这些提示前缀
    const backendErrorPrefixes = [
      'application',
      '语音识别失败',
      '语音识别服务',
      '文件上传失败'
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
      showToast(
        isBackendError ? text : '语音识别失败，请稍后重试',
        isBackendError && text.includes('失败') ? 'error' : 'warning',
        4000
      )
    }
  } catch (err) {
    console.error("Speech to text translation failed:", err)
    showToast('语音识别失败，请稍后重试', 'error')
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

const createEmptyDetectionResult = () => ({
  pestName: '',
  confidence: 0,
  imageUrl: '',
  primaryTarget: '',
  primaryTargetZh: '',
  primaryConfidence: 0,
  sceneType: 'single',
  classNamesZh: [],
  detectedSummary: [],
  report: ''
})
const detectionResult = ref(createEmptyDetectionResult())

const sceneBadgeClassMap = {
  single: 'bg-emerald-100 text-emerald-700',
  multi: 'bg-amber-100 text-amber-700',
  uncertain: 'bg-orange-100 text-orange-700',
  empty: 'bg-slate-100 text-slate-600'
}

// 识别历史记录
const historyItems = ref(JSON.parse(localStorage.getItem('leafquery_history') || '[]'))

const refreshNotificationBadge = async () => {
  try {
    const announcements = await fetchAnnouncements('/api/discovery/announcements')
    const localReadIds = getLocalAnnouncementReadIds()
    hasNewNotification.value = announcements.some(item => !isAnnouncementRead(item, localReadIds))
  } catch (error) {
    console.error('Failed to refresh announcement badge state', error)
    hasNewNotification.value = false
  }
}

const refreshPopupAnnouncement = async () => {
  try {
    const popupAnnouncements = await fetchAnnouncements('/api/discovery/announcements/popup')
    const currentUser = getStoredUser()
    if (currentUser?.userId) {
      popupAnnouncement.value = popupAnnouncements.find(item => !Boolean(item.read)) || null
      return
    }

    const dismissedIds = getLocalDismissedPopupIds()
    popupAnnouncement.value = popupAnnouncements.find(item => !dismissedIds.includes(Number(item.id))) || null
  } catch (error) {
    console.error('Failed to refresh popup announcement state', error)
    popupAnnouncement.value = null
  }
}

const refreshAnnouncementState = async () => {
  await refreshNotificationBadge()
  await refreshPopupAnnouncement()
}

onMounted(async () => {
  try {
    const user = getStoredUser()
    if (user?.userId) {
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
  } catch (e) {
    console.error('Failed to sync history from cloud', e)
  }

  await refreshAnnouncementState()
  window.addEventListener(AUTH_CHANGE_EVENT, refreshAnnouncementState)
})

onUnmounted(() => {
  window.removeEventListener(AUTH_CHANGE_EVENT, refreshAnnouncementState)
})

async function dismissPopup() {
  if (!popupAnnouncement.value) {
    return
  }

  try {
    await dismissPopupAnnouncement(popupAnnouncement.value.id)
  } catch (error) {
    console.error('Failed to dismiss popup announcement', error)
  } finally {
    popupAnnouncement.value = null
    await refreshNotificationBadge()
  }
}

// 置信度百分比显示
const confidencePercent = computed(() => {
  return (detectionResult.value.confidence * 100).toFixed(1)
})

const summaryItems = computed(() => normalizeDetectionSummary(detectionResult.value.detectedSummary))
const detectedClassNames = computed(() => normalizeClassNames(
  detectionResult.value.classNamesZh,
  detectionResult.value.detectedSummary
))
const sceneMeta = computed(() => getSceneMeta(detectionResult.value.sceneType))
const sceneBadgeClasses = computed(() => sceneBadgeClassMap[detectionResult.value.sceneType] || sceneBadgeClassMap.single)
const primaryDisplayName = computed(() => getPrimaryDisplayName(detectionResult.value))
const detailTitle = computed(() => getReportTitle(detectionResult.value))
const diagnosisContextName = computed(() => {
  if (detectionResult.value.pestName === '通用农业咨询') return detectionResult.value.pestName
  if (detectionResult.value.sceneType === 'multi') {
    return detectedClassNames.value.join('、') || primaryDisplayName.value
  }
  return primaryDisplayName.value
})
const formatConfidence = (value, digits = 1) => formatConfidencePercent(value, digits)

// 根据置信度返回颜色方案
const confidenceColor = computed(() => {
  const c = detectionResult.value.confidence
  if (c >= 0.8) return { text: 'text-green-600', bg: 'bg-green-500', glow: 'shadow-green-500/40', icon: 'bg-green-50 text-green-500', border: 'border-green-500' }
  if (c >= 0.5) return { text: 'text-orange-500', bg: 'bg-orange-500', glow: 'shadow-orange-500/40', icon: 'bg-orange-50 text-orange-500', border: 'border-orange-500' }
  return { text: 'text-red-500', bg: 'bg-red-500', glow: 'shadow-red-500/40', icon: 'bg-red-50 text-red-500', border: 'border-red-500' }
})

const persistFinalizedDetection = async result => {
  const newRecord = {
    id: Date.now(),
    name: result.pestName,
    confidence: result.confidence,
    time: Date.now(),
    imageUrl: result.imageUrl
  }

  historyItems.value.unshift(newRecord)
  if (historyItems.value.length > 100) historyItems.value.pop()
  localStorage.setItem('leafquery_history', JSON.stringify(historyItems.value))

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
    console.error('Failed to sync record to cloud:', e)
  }
}

// ===== 先选后扫：拍照/上传后直接进入识别 =====
const triggerScan = () => {
  if (!hasSelection.value) {
    showToast('请先选择至少一个检测类别', 'warning')
    return
  }
  fileInput.value?.click()
}

const onScanFileSelected = async (event) => {
  const file = event.target.files?.[0]
  if (!file) return
  event.target.value = ''

  previewUrl.value = URL.createObjectURL(file)
  isAnalyzing.value = true
  analysisStage.value = 'yolo'
  isResultReady.value = false
  showDetail.value = false
  messages.value = []

  // 记录分析开始时间，确保动画至少播放一段时间
  const analysisStartTime = Date.now()
  const MIN_ANIMATION_DURATION = 1800 // 最少展示 1.8 秒动画

  let yoloTimer = setTimeout(() => {
    if (isAnalyzing.value && analysisStage.value === 'yolo') {
      analysisStage.value = 'review'
    }
  }, 1500)

  try {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('categories', selectedCategories.value.join(','))
    formData.append('locationId', '101010100')

    const response = await axios.post('/api/pest/identify', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 120000
    })

    const predictionRaw = response.data.prediction || {}
    const savedImageUrl = response.data.image_url || previewUrl.value
    yoloUsed.value = response.data.yolo_used !== false
    reviewRequired.value = response.data.review_required === true
    const reportError = response.data.report_error || ''
    const reviewResult = response.data.review_result || ''

    let finalPestName = predictionRaw.pest_name || predictionRaw.pestName ||
                        predictionRaw.primary_target_zh || predictionRaw.primaryTargetZh || '未识别'
    if (String(finalPestName).toLowerCase() === 'unknown' || finalPestName === '未识别') {
      finalPestName = '未能识别出具体病虫害'
    }

    if (reviewResult && reviewResult.includes('【确诊结果】')) {
      const match = reviewResult.match(/【确诊结果】[：:]?\s*([^\n]+)/);
      if (match && match[1]) {
        finalPestName = match[1].replace(/\*/g, '').trim();
      }
    }

    detectionResult.value = {
      pestName: finalPestName,
      confidence: predictionRaw.confidence || predictionRaw.primary_confidence || 0,
      primaryTargetZh: predictionRaw.primary_target_zh || predictionRaw.primaryTargetZh || finalPestName,
      primaryConfidence: predictionRaw.primary_confidence || predictionRaw.primaryConfidence || 0,
      sceneType: predictionRaw.scene_type || predictionRaw.sceneType || (yoloUsed.value ? 'single' : 'empty'),
      classNamesZh: predictionRaw.class_names_zh || predictionRaw.classNamesZh || [],
      detectedSummary: predictionRaw.detected_summary || predictionRaw.detectedSummary || [],
      report: '',  // 阶段 3 按需生成
      reviewResult: reviewResult,
      imageUrl: savedImageUrl,
      predictionJson: JSON.stringify(predictionRaw),
      userCategories: selectedCategories.value.join(',')
    }

    clearTimeout(yoloTimer)

    const elapsed = Date.now() - analysisStartTime
    
    // 保证 YOLO 至少闪 800 毫秒才进下一步，防止突兀
    if (elapsed < 800) {
      await new Promise(r => setTimeout(r, 800 - elapsed))
    }

    if (reviewRequired.value) {
      if (analysisStage.value !== 'review') {
        analysisStage.value = 'review'
      }
      // AI复核保底思考时间（1500毫秒），让业务体感真实，绝不比YOLO短
      await new Promise(r => setTimeout(r, 1500))
    } else {
      const nowElapsed = Date.now() - analysisStartTime
      if (nowElapsed < MIN_ANIMATION_DURATION) {
        await new Promise(r => setTimeout(r, MIN_ANIMATION_DURATION - nowElapsed))
      }
    }

    // 展示"分析完成"✅ 动画，停留一段时间让用户看到
    analysisStage.value = 'done'
    await new Promise(r => setTimeout(r, 1000))

    // 复核失败提示
    if (reportError) {
      showToast('YOLO 识别完成，但 AI 复核失败', 'warning', 5000)
    }

    // 关闭分析动画，展示结果摘要
    isAnalyzing.value = false
    analysisStage.value = ''
    messages.value = []
    isResultReady.value = true
    showDetail.value = false

    await persistFinalizedDetection(detectionResult.value)
    scrollToBottom()
  } catch (err) {
    console.error('Identification failed', err)
    showToast(err.response?.data?.error || '识别失败，请检查网络或服务配置', 'error')
    isResultReady.value = false
    isAnalyzing.value = false
    analysisStage.value = ''
  }
}

// ====== 阶段 3：按需生成诊断报告 ======
const isGeneratingReport = ref(false)
const reportLoadingSteps = [
  '检查本地气象数据...',
  '索引病虫害知识库...',
  '参考初筛与复核结果...',
  '构建多维度推理中...',
  'AI 诊断报告生成中...'
]
const reportLoadingText = ref(reportLoadingSteps[0])
let reportLoadingInterval = null

const generateReport = async () => {
  if (isGeneratingReport.value) return
  isGeneratingReport.value = true

  let stepIndex = 0
  reportLoadingText.value = reportLoadingSteps[0]
  reportLoadingInterval = setInterval(() => {
    stepIndex++
    if (stepIndex < reportLoadingSteps.length) {
      reportLoadingText.value = reportLoadingSteps[stepIndex]
    }
  }, 3500)

  try {
    const params = new URLSearchParams()
    params.append('imageUrl', detectionResult.value.imageUrl || '')
    params.append('categories', detectionResult.value.userCategories || selectedCategories.value.join(','))
    params.append('locationId', '101010100')
    if (detectionResult.value.predictionJson) {
      params.append('predictionJson', detectionResult.value.predictionJson)
    }
    if (detectionResult.value.reviewResult) {
      params.append('reviewResult', detectionResult.value.reviewResult)
    }

    const response = await axios.post('/api/pest/diagnose', params, { timeout: 120000 })
    const report = response.data.report || ''

    detectionResult.value.report = report
    messages.value = report
      ? [{ role: 'assistant', content: report }]
      : [{ role: 'assistant', content: '诊断报告生成失败，请稍后重试。' }]

    showDetail.value = true
    scrollToBottom()
  } catch (err) {
    console.error('Report generation failed', err)
    showToast('诊断报告生成失败：' + (err.response?.data?.error || '请检查网络'), 'error')
  } finally {
    isGeneratingReport.value = false
    if (reportLoadingInterval) clearInterval(reportLoadingInterval)
  }
}

const closeResult = () => {
  stopSpeaking()
  isResultReady.value = false
  showDetail.value = false
  messages.value = []
  detectionResult.value = createEmptyDetectionResult()
  analysisStage.value = ''
}
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
      pestName: diagnosisContextName.value,
      messages: messages.value.map(m => ({ role: m.role, content: m.content, imageBase64: m.imageBase64 }))
    })
    messages.value.push({ role: 'assistant', content: response.data.reply })
  } catch (err) {
    console.error('Failed to send message', err)
    showToast('发送消息失败，请稍后重试', 'error')
  } finally {
    isSending.value = false
    scrollToBottom()
  }
}

// 开启通用咨询对话
const startGeneralChat = () => {
  if (!(generalUserInput.value.trim() || pendingGeneralImage.value) || isSending.value) return
  
  detectionResult.value = {
    ...createEmptyDetectionResult(),
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

// 简单的 Markdown 渲染（粗略处理标题和列表）
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

// 历史条目的 emoji  图标
const getEmoji = name => {
  if (!name) return '📌'
  if (name.includes('健康')) return '🌿'
  if (name.includes('虫')) return '🐛'
  if (name.includes('病')) return '🦠'
  return '📌'
}

const formatTime = (item) => {
  const timestamp = item.time || item.createTime
  if (!timestamp) return '未知时间'

  let date
  if (typeof timestamp === 'number' || (typeof timestamp === 'string' && /^\d+$/.test(timestamp))) {
    date = new Date(Number(timestamp))
  } else {
    date = new Date(timestamp)
  }

  if (isNaN(date.getTime())) return timestamp

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
    
    <!-- Toast -->
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
          <span class="text-lg shrink-0">{{ toastType === 'error' ? '✖' : toastType === 'warning' ? '⚠️' : 'ℹ️' }}</span>
          <span class="text-sm font-medium">{{ toastMessage }}</span>
          <button @click="toastVisible = false" class="ml-2 shrink-0 opacity-50 hover:opacity-100 text-lg leading-none">&times;</button>
        </div>
      </div>
    </Transition>

    <!-- SVG Filter -->
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

    <!-- Recording Overlay -->
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
          <canvas id="orb-canvas" width="260" height="260" class="absolute inset-0"></canvas>
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
    <div class="flex justify-between items-center mb-6">
      <div>
        <h1 class="text-3xl font-bold text-slate-900 tracking-tight" v-motion-slide-visible-once-bottom>识别</h1>
        <p class="text-slate-500 text-sm mt-1 font-medium" v-motion-slide-visible-once-bottom :delay="100">AI 智能诊断助手</p>
      </div>
      <button @click="router.push('/notifications')" class="w-12 h-12 bg-white rounded-2xl flex items-center justify-center text-slate-800 shadow-sm border border-slate-100 hover:shadow-md transition-shadow relative">
        🔔
        <span v-if="hasNewNotification" class="absolute -top-1 -right-1 w-3.5 h-3.5 bg-red-500 rounded-full border-2 border-white shadow-sm animate-pulse"></span>
      </button>
    </div>

    <!-- Combined Card Stack Area (Selection + Scan) -->
    <div class="w-full flex-none mx-auto aspect-[0.95/1] max-w-[34rem] min-h-[22rem] bg-slate-900 rounded-[2rem] shadow-2xl shadow-slate-900/20 overflow-hidden relative border border-slate-800 mb-8 lg:flex-1 lg:max-w-none lg:min-h-[450px] lg:aspect-auto" v-motion-pop-visible-once :delay="200">
      
      <!-- LAYER 1: Main Scan Area (Bottom Pattern) -->
      <div class="absolute inset-0 transition-all duration-700 ease-in-out" :class="selectionConfirmed ? 'opacity-100 scale-100 pointer-events-auto' : 'opacity-0 scale-90 pointer-events-none'">
        <input ref="fileInput" type="file" accept="image/*" class="hidden" @change="onScanFileSelected" />

        <!-- Scanning Animation -->
        <div v-if="isAnalyzing" class="absolute inset-0 z-40 flex flex-col items-center justify-center">
          <img v-if="previewUrl" :src="previewUrl" class="absolute inset-0 w-full h-full object-cover opacity-20" />
          <div class="relative z-10 flex flex-col items-center">
            <div v-if="analysisStage === 'yolo'" class="flex flex-col items-center animate-fadeIn">
              <div class="w-16 h-16 relative mb-3">
                <div class="absolute inset-0 border-4 border-emerald-200 rounded-full"></div>
                <div class="absolute inset-0 border-4 border-emerald-500 rounded-full border-t-transparent animate-spin"></div>
              </div>
              <span class="text-base font-black text-emerald-300">YOLO 快速检测中...</span>
              <span class="text-xs text-white/60 mt-1">正在扫描图像中的病虫害特征</span>
            </div>
            <div v-else-if="analysisStage === 'review'" class="flex flex-col items-center animate-fadeIn">
              <div class="w-16 h-16 relative mb-3">
                <div class="absolute inset-0 border-4 border-amber-200 rounded-full"></div>
                <div class="absolute inset-0 border-4 border-amber-500 rounded-full border-t-transparent animate-spin" style="animation-duration: 1.5s"></div>
              </div>
              <span class="text-base font-black text-amber-300">AI 视觉复核中...</span>
              <span class="text-xs text-white/60 mt-1">大模型正在对图像进行深度分析</span>
            </div>
            <div v-else class="flex flex-col items-center animate-fadeIn">
              <div class="w-20 h-20 bg-emerald-500/20 rounded-full flex items-center justify-center mb-4 animate-scaleIn">
                <span class="text-4xl">✓</span>
              </div>
              <span class="text-lg font-black text-emerald-300 mb-1">分析完成</span>
              <span class="text-sm text-white/80 font-medium">{{ primaryDisplayName || '处理完毕' }}</span>
            </div>
          </div>
        </div>

        <!-- Idle State -->
        <div v-if="!isAnalyzing" class="absolute inset-0 bg-black/40 flex flex-col items-center justify-center cursor-pointer z-40" @click="triggerScan">
          
          <button @click.stop="selectionConfirmed = false" class="absolute top-4 left-4 text-white/80 bg-black/30 px-3 py-1.5 rounded-full text-xs flex items-center gap-1 backdrop-blur-md transition-all z-50">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-3.5 h-3.5"><path stroke-linecap="round" stroke-linejoin="round" d="M15.75 19.5 8.25 12l7.5-7.5" /></svg>
            重选范围
          </button>

          <div class="relative flex flex-col items-center justify-center animate-tap mt-2">
            <div class="absolute w-16 h-16 bg-green-500/40 rounded-full animate-ping -top-2"></div>
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-14 h-14 text-white drop-shadow-lg z-10">
              <path stroke-linecap="round" stroke-linejoin="round" d="M15.042 21.672 13.684 16.6m0 0-2.51 2.225.569-9.47 5.227 7.917-3.286-.672ZM12 2.25V4.5m5.834.166-1.591 1.591M20.25 10.5H18M7.757 14.743l-1.59 1.59M6 10.5H3.75m4.007-4.243-1.59-1.59" />
            </svg>
            <span class="mt-5 text-white/90 font-medium tracking-wide drop-shadow-md bg-black/20 px-4 py-1.5 rounded-full border border-white/10 backdrop-blur-sm">
              点击拍照或上传图片
            </span>
          </div>
        </div>

        <!-- Aurora background -->
        <div class="absolute inset-0 bg-aurora">
          <div class="absolute top-[0%] left-[0%] w-[50%] h-[50%] bg-emerald-500/30 rounded-full mix-blend-screen filter blur-[60px] animate-blob"></div>
          <div class="absolute bottom-[0%] right-[0%] w-[50%] h-[50%] bg-blue-500/40 rounded-full mix-blend-screen filter blur-[60px] animate-blob animation-delay-2000"></div>
          <div class="absolute top-[10%] right-[30%] w-[60%] h-[60%] bg-cyan-500/35 rounded-full mix-blend-screen filter blur-[60px] animate-blob animation-delay-4000"></div>
        </div>
      </div>

      <!-- LAYER 2: Category Selection Panel (Top Layer / Card) -->
      <Transition
        enter-active-class="transition-all duration-500 ease-out"
        leave-active-class="transition-all duration-500 ease-in"
        enter-from-class="opacity-0 translate-y-8 scale-95"
        leave-to-class="opacity-0 -translate-y-8 scale-105"
      >
        <div v-if="!selectionConfirmed" class="absolute inset-0 bg-white z-50 flex flex-col p-5 rounded-[2rem]">
          <div class="flex items-center gap-2 mb-4 shrink-0">
            <h3 class="font-bold text-slate-800 text-lg tracking-wide">选择检测范围</h3>
            <span v-if="!hasSelection" class="ml-auto text-xs font-bold text-amber-500 animate-pulse bg-amber-50 px-2.5 py-1 rounded-full">请至少选一项</span>
            <span v-else class="ml-auto text-xs font-bold text-emerald-600 bg-emerald-50 px-2.5 py-1 rounded-full">已选 {{ selectedCategories.length }} 项</span>
          </div>
          
          <div class="flex-1 content-start flex flex-col justify-center">
            <p class="text-slate-400 text-xs font-bold mb-2 uppercase tracking-wider">作物与病害</p>
            <div class="flex flex-wrap gap-2 mb-3">
              <button
                v-for="opt in categoryOptions.filter(o => o.group === 'disease')"
                :key="opt.key"
                @click="toggleCategory(opt.value)"
                class="relative px-3.5 py-1.5 rounded-[12px] border-2 text-sm font-bold transition-all duration-300 select-none flex items-center gap-1.5"
                :class="selectedCategories.includes(opt.value)
                  ? 'border-emerald-400 bg-emerald-50 text-emerald-700 shadow-sm scale-[1.03]'
                  : 'border-slate-100 bg-slate-50 text-slate-500 hover:border-slate-200 hover:bg-slate-100'"
              >
                <span class="text-base">{{ opt.icon }}</span>{{ opt.label }}
                <!-- Checkmark badge -->
                <div v-if="selectedCategories.includes(opt.value)" class="absolute -top-1.5 -right-1.5 w-5 h-5 bg-emerald-500 shadow-md border-2 border-white text-white rounded-full flex items-center justify-center">
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-3 h-3"><path fill-rule="evenodd" d="M16.704 4.153a.75.75 0 01.143 1.052l-8 10.5a.75.75 0 01-1.127.075l-4.5-4.5a.75.75 0 011.06-1.06l3.894 3.893 7.48-9.817a.75.75 0 011.05-.143z" clip-rule="evenodd" /></svg>
                </div>
              </button>
            </div>
            
            <p class="text-slate-400 text-xs font-bold mb-2 uppercase tracking-wider mt-1">虫害检测</p>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="opt in categoryOptions.filter(o => o.group === 'pest')"
                :key="opt.key"
                @click="toggleCategory(opt.value)"
                class="relative px-3.5 py-1.5 rounded-[12px] border-2 text-sm font-bold transition-all duration-300 select-none flex items-center gap-1.5"
                :class="selectedCategories.includes(opt.value)
                  ? 'border-orange-400 bg-orange-50 text-orange-700 shadow-sm scale-[1.03]'
                  : 'border-slate-100 bg-slate-50 text-slate-500 hover:border-slate-200 hover:bg-slate-100'"
              >
                <span class="text-base">{{ opt.icon }}</span>{{ opt.label }}
                <!-- Checkmark badge -->
                <div v-if="selectedCategories.includes(opt.value)" class="absolute -top-1.5 -right-1.5 w-5 h-5 bg-orange-500 shadow-md border-2 border-white text-white rounded-full flex items-center justify-center">
                  <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20" fill="currentColor" class="w-3 h-3"><path fill-rule="evenodd" d="M16.704 4.153a.75.75 0 01.143 1.052l-8 10.5a.75.75 0 01-1.127.075l-4.5-4.5a.75.75 0 011.06-1.06l3.894 3.893 7.48-9.817a.75.75 0 011.05-.143z" clip-rule="evenodd" /></svg>
                </div>
              </button>
            </div>
          </div>
          
          <div class="mt-3 shrink-0">
             <button 
               @click="confirmSelection"
               :disabled="!hasSelection"
               class="w-full py-3.5 rounded-[14px] font-bold text-[15px] transition-all duration-300 flex items-center justify-center gap-2"
               :class="hasSelection ? 'bg-slate-900 text-white shadow-xl shadow-slate-900/20 active:scale-[0.98]' : 'bg-slate-100 text-slate-400 cursor-not-allowed'"
             >
               <span>下一步：拍照或上传图片</span>
               <svg v-if="hasSelection" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2.5" stroke="currentColor" class="w-5 h-5">
                 <path stroke-linecap="round" stroke-linejoin="round" d="M13.5 4.5 21 12m0 0-7.5 7.5M21 12H3" />
               </svg>
             </button>
          </div>
        </div>
      </Transition>
    </div>

    <!-- Recent History -->
    <div class="mb-4" v-motion-slide-visible-once-bottom :delay="400">
      <div class="flex justify-between items-center mb-4 px-1">
        <h3 class="font-bold text-slate-800 text-lg">最近记录</h3>
        <button @click="router.push('/records')" class="text-green-600 text-sm font-semibold">全部</button>
      </div>
      <div class="flex space-x-4 overflow-x-auto pb-2 -mx-6 px-6 scrollbar-hide">
        <div v-if="historyItems.length === 0" v-for="i in 3" :key="'placeholder-' + i" class="flex-shrink-0 w-24 h-28 bg-white rounded-2xl p-3 border border-slate-100 shadow-[0_4px_20px_rgb(0,0,0,0.03)] flex flex-col items-center justify-center space-y-2">
          <div class="w-10 h-10 bg-slate-50 text-slate-300 rounded-full flex items-center justify-center text-lg">📲</div>
          <div class="text-center">
             <span class="block text-xs font-bold text-slate-300">待识别</span>
             <span class="block text-[10px] text-slate-300 font-medium mt-1">--</span>
          </div>
        </div>
        <div
          v-for="item in historyItems"
          :key="item.id"
          class="flex-shrink-0 w-24 h-28 bg-white rounded-2xl p-3 border border-slate-100 shadow-[0_4px_20px_rgb(0,0,0,0.03)] flex flex-col items-center justify-center space-y-2 transition-all duration-300 hover:shadow-md"
        >
          <div class="w-10 h-10 bg-green-50 text-green-500 rounded-full flex items-center justify-center text-lg overflow-hidden shrink-0">
            <img v-if="item.imageUrl && item.imageUrl.trim() !== ''" :src="item.imageUrl" class="w-full h-full object-cover" @error="$event.target.style.display='none'" />
            <span v-show="!item.imageUrl || item.imageUrl.trim() === ''">🌾</span>
          </div>
          <div class="text-center w-full">
             <span class="block text-xs font-bold text-slate-800 truncate w-full">{{ item.name }}</span>
             <span class="block text-[10px] text-slate-400 font-medium mt-1">{{ formatTime(item) }}</span>
          </div>
        </div>
      </div>
    </div>
    
    <!-- General Chat Input -->
    <div class="mb-24 flex flex-col relative" v-motion-slide-visible-once-bottom :delay="500">
      <div v-if="pendingGeneralImage" class="mb-2 relative w-16 h-16 ml-3">
        <img :src="pendingGeneralImage" class="w-full h-full object-cover rounded border border-slate-200 shadow-sm" />
        <button @click="removePendingGeneralImage" class="absolute -top-2 -right-2 bg-red-500/90 text-white rounded-full w-5 h-5 flex items-center justify-center text-xs pb-[1px] hover:bg-red-600">×</button>
      </div>
      <div class="flex items-center space-x-3 w-full">
        <form @submit.prevent="startGeneralChat" class="relative flex items-center flex-1">
          <input type="file" accept="image/*" class="hidden" ref="generalImageInputRef" @change="onGeneralImageSelected" />
          <button type="button" @click="triggerGeneralImageUpload" class="absolute left-2 w-10 h-10 flex items-center justify-center text-slate-400 hover:text-green-500 transition-colors z-10" :disabled="isSending">
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-6 h-6">
              <path stroke-linecap="round" stroke-linejoin="round" d="m2.25 15.75 5.159-5.159a2.25 2.25 0 0 1 3.182 0l5.159 5.159m-1.5-1.5 1.409-1.409a2.25 2.25 0 0 1 3.182 0l2.909 2.909m-18 3.75h16.5a1.5 1.5 0 0 0 1.5-1.5V6a1.5 1.5 0 0 0-1.5-1.5H3.75A1.5 1.5 0 0 0 2.25 6v12a1.5 1.5 0 0 0 1.5 1.5Zm10.5-11.25h.008v.008h-.008V8.25Zm.375 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Z" />
            </svg>
          </button>
          <input 
            v-model="generalUserInput" 
            type="text" 
            :placeholder="isRecording === 'general' ? '正在聆听...' : '输入病害名称提问'" 
            class="w-full bg-white border border-slate-200 text-slate-800 rounded-full pl-12 pr-14 py-3.5 focus:outline-none focus:ring-2 focus:ring-green-400 focus:border-transparent transition-all shadow-[0_4px_20px_rgb(0,0,0,0.04)]"
            :disabled="isSending"
          />
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
      <div v-if="isResultReady" class="fixed inset-0 z-[100] flex items-end sm:items-center sm:justify-center" @click="closeResult">
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

            <!-- 摘要视图（点击“查看报告”前） -->
            <div v-if="!showDetail" class="flex-1 flex flex-col min-h-0">
              <div class="flex-1 overflow-y-auto custom-scrollbar pr-2 mb-2">
                <div class="text-center mb-2">
                  <div class="w-20 h-20 rounded-full mx-auto flex items-center justify-center text-4xl mb-4 border-4 border-white shadow-xl" :class="[confidenceColor.icon]">
                    <img v-if="detectionResult.imageUrl" :src="detectionResult.imageUrl" class="w-full h-full object-cover rounded-full" />
                    <span v-else>🌾</span>
                  </div>
                  <h2 class="text-2xl font-bold text-slate-900 mb-1">识别完成</h2>
                  <p class="text-slate-500 font-medium mb-4">
                    检测对象：<span class="text-slate-800 font-bold">{{ primaryDisplayName }}</span>
                  </p>
                  <div class="flex flex-wrap items-center justify-center gap-2 mb-4">
                    <span class="rounded-full px-3 py-1 text-xs font-bold" :class="sceneBadgeClasses">{{ sceneMeta.label }}</span>
                    <span v-if="yoloUsed" class="rounded-full bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-600">YOLO</span>
                    <span v-if="reviewRequired && detectionResult?.reviewResult" class="rounded-full bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-600">已复核</span>
                    <span v-else-if="reviewRequired && !detectionResult?.reviewResult" class="rounded-full bg-amber-50 px-3 py-1 text-xs font-bold text-amber-600">待复核</span>
                    <span v-else-if="!reviewRequired && yoloUsed" class="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-500">无需复核</span>
                  </div>
                </div>
                
                <div v-if="detectionResult.confidence > 0" class="bg-slate-50 rounded-2xl p-4 border border-slate-100/50">
                  <div class="flex justify-between items-center mb-2">
                    <span class="text-slate-500 text-sm font-medium">AI 置信度</span>
                    <span class="font-bold text-base" :class="confidenceColor.text">{{ confidencePercent }}%</span>
                  </div>
                  <div class="w-full bg-slate-200 rounded-full h-2 overflow-hidden">
                    <div class="h-full rounded-full transition-all duration-1000 ease-out" :class="confidenceColor.bg" :style="{ width: confidencePercent + '%' }"></div>
                  </div>
                </div>

                <div v-if="yoloUsed && summaryItems.length" class="mt-4 rounded-2xl border border-slate-100 bg-white p-4 shadow-sm">
                  <div class="text-sm font-bold text-slate-800">检测概览</div>
                  <p class="mt-1 text-sm text-slate-500">{{ sceneMeta.description }}</p>
                  <div v-if="detectedClassNames.length" class="mt-3 flex flex-wrap gap-2">
                    <span v-for="className in detectedClassNames" :key="className" class="rounded-full border border-slate-200 bg-slate-50 px-3 py-1 text-xs font-bold text-slate-600">{{ className }}</span>
                  </div>
                  <div class="mt-4 grid gap-3">
                    <div v-for="item in summaryItems" :key="item.id" class="rounded-2xl border border-slate-100 bg-slate-50 px-4 py-3">
                      <div class="flex items-start justify-between gap-3">
                        <div>
                          <div class="font-bold text-slate-800">{{ item.nameZh }}</div>
                          <div class="mt-1 text-xs text-slate-500">检测 {{ item.count }} 处</div>
                        </div>
                        <div class="rounded-full bg-emerald-50 px-3 py-1 text-xs font-bold text-emerald-600">{{ formatConfidence(item.maxConfidence, 1) }}</div>
                      </div>
                      <div class="mt-3 h-2 overflow-hidden rounded-full bg-slate-200">
                        <div class="h-full rounded-full bg-gradient-to-r from-green-400 to-emerald-500" :style="{ width: `${Math.max(6, Math.min(item.maxConfidence * 100, 100))}%` }"></div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 复核结论卡片（阶段 2 结果） -->
                <div v-if="detectionResult?.reviewResult" class="mt-4 rounded-2xl border border-emerald-200 bg-emerald-50/80 p-4">
                  <div class="flex items-center gap-2 mb-2">
                    <span class="text-base">🔍</span>
                    <span class="text-sm font-bold text-emerald-800">AI 视觉复核结果</span>
                  </div>
                  <div class="text-sm text-slate-700 leading-relaxed whitespace-pre-line">{{ detectionResult.reviewResult }}</div>
                </div>

                <!-- 待复核提示（Dify 复核失败时） -->
                <div v-else-if="reviewRequired" class="mt-4 rounded-2xl border border-amber-200 bg-amber-50/80 px-4 py-3 flex items-center gap-2">
                  <span class="text-base">⚠</span>
                  <span class="text-xs font-bold text-amber-700">AI 复核暂时不可用，请根据 YOLO 检测结果自行判断</span>
                </div>
              </div>
              
              <div class="shrink-0 space-y-3">
                <!-- 阶段 3：生成诊断报告按钮 -->
                <button 
                  v-if="!detectionResult?.report"
                  @click="generateReport" 
                  :disabled="isGeneratingReport"
                  class="w-full py-3.5 sm:py-4 rounded-xl font-bold text-base transition-all active:scale-[0.98] flex items-center justify-center gap-2"
                  :class="reviewRequired 
                    ? 'bg-amber-500 text-white shadow-lg shadow-amber-500/20 animate-pulse-subtle' 
                    : 'bg-slate-900 text-white shadow-lg shadow-slate-900/20'"
                >
                  <template v-if="isGeneratingReport">
                    <svg class="animate-spin w-5 h-5 shrink-0" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24"><circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle><path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"></path></svg>
                    <span class="animate-pulse truncate">{{ reportLoadingText }}</span>
                  </template>
                  <template v-else>
                    <span>📝</span>
                    <span>生成 AI 诊断建议</span>
                    <span v-if="reviewRequired" class="text-xs opacity-80 ml-1">(推荐)</span>
                  </template>
                </button>

                <!-- 已有报告时：查看报告按钮 -->
                <button 
                  v-if="detectionResult?.report" 
                  @click="showDetail = true" 
                  class="w-full bg-slate-900 text-white py-3.5 sm:py-4 rounded-xl font-bold text-base shadow-lg shadow-slate-900/20 active:scale-[0.98] transition-all"
                >
                  查看详细诊断报告
                </button>
              </div>
            </div>

            <!-- Detail View (Chat with Report) -->
            <div v-else class="flex-1 flex flex-col min-h-0">
              <div class="flex items-center justify-between mb-4 pb-4 border-b">
                <button v-if="detectionResult.pestName !== '通用农业咨询'" @click="showDetail = false" class="text-slate-400 font-bold px-2 py-1 bg-slate-100 rounded-lg text-sm">返回</button>
                <button v-else @click="closeResult" class="text-slate-400 font-bold px-2 py-1 bg-slate-100 rounded-lg text-sm">关闭</button>
                <h2 class="text-xl font-bold text-slate-900 truncate flex-1 text-center px-4">{{ detailTitle }}</h2>
                <button 
                  @click="speakLastReply" 
                  class="w-10 h-10 rounded-full flex items-center justify-center transition-all"
                  :class="isSpeaking ? 'bg-green-100 text-green-600' : isTtsLoading ? 'bg-slate-100 text-slate-400' : 'text-slate-400 hover:text-green-500 hover:bg-green-50'"
                  :disabled="isTtsLoading && !isSpeaking"
                >
                  <svg v-if="isTtsLoading && !isSpeaking" class="w-5 h-5 animate-spin" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                    <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
                    <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                  <svg v-else-if="isSpeaking" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor" class="w-5 h-5 animate-pulse">
                    <path d="M13.5 4.06c0-1.336-1.616-2.005-2.56-1.06l-4.5 4.5H4.508c-1.141 0-2.318.664-2.66 1.905A9.76 9.76 0 001.5 12c0 .898.121 1.768.35 2.595.341 1.24 1.518 1.905 2.659 1.905h1.93l4.5 4.5c.945.945 2.561.276 2.561-1.06V4.06zM18.584 5.106a.75.75 0 011.06 0c3.808 3.807 3.808 9.98 0 13.788a.75.75 0 01-1.06-1.06 8.25 8.25 0 000-11.668.75.75 0 010-1.06z" />
                    <path d="M15.932 7.757a.75.75 0 011.061 0 6 6 0 010 8.486.75.75 0 01-1.06-1.061 4.5 4.5 0 000-6.364.75.75 0 010-1.06z" />
                  </svg>
                  <svg v-else xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-5 h-5">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M19.114 5.636a9 9 0 010 12.728M16.463 8.288a5.25 5.25 0 010 7.424M6.75 8.25l4.72-4.72a.75.75 0 011.28.53v15.88a.75.75 0 01-1.28.53l-4.72-4.72H4.51c-.88 0-1.704-.507-1.938-1.354A9.01 9.01 0 012.25 12c0-.83.112-1.633.322-2.396C2.806 8.756 3.63 8.25 4.51 8.25H6.75z" />
                  </svg>
                </button>
              </div>
              
              <div ref="chatContainer" class="flex-1 overflow-y-auto pb-4 custom-scrollbar px-2 space-y-6">
                <div v-if="isAnalyzing && messages.length === 0" class="flex flex-col items-center justify-center py-20">
                   <div class="w-16 h-16 relative mb-6">
                     <div class="absolute inset-0 border-4 border-green-100 rounded-full"></div>
                     <div class="absolute inset-0 border-4 border-green-500 rounded-full border-t-transparent animate-spin"></div>
                     <div class="absolute inset-0 flex items-center justify-center text-xl">AI</div>
                   </div>
                   <h3 class="text-lg font-bold text-slate-800 mb-2 animate-pulse">AI 正在思考中</h3>
                   <p class="text-sm text-slate-500">正在整理识别结果并生成诊断建议...</p>
                </div>
                
                <div v-for="(msg, index) in messages" :key="index" class="flex w-full" :class="msg.role === 'user' ? 'justify-end' : 'justify-start'">
                  <div v-if="msg.role === 'assistant'" class="flex items-start space-x-3 max-w-[95%]">
                    <div class="w-8 h-8 rounded-full bg-slate-900 text-white flex items-center justify-center text-xs shrink-0 shadow-md">AI</div>
                    <div class="bg-slate-50 border border-slate-100 rounded-2xl rounded-tl-sm px-5 py-4 shadow-sm">
                      <div class="markdown-body text-slate-700 text-sm leading-relaxed" v-html="renderMarkdown(msg.content)"></div>
                    </div>
                  </div>
                  <div v-else class="flex items-end space-x-2 max-w-[85%]">
                    <div class="bg-green-500 text-white rounded-2xl rounded-br-sm shadow-md overflow-hidden relative" :class="[msg.imageBase64 ? 'p-1' : 'px-5 py-3']">
                      <img v-if="msg.imageBase64" :src="msg.imageBase64" class="w-full max-w-[200px] object-cover rounded-[10px]" :class="msg.content ? 'mb-2' : ''" />
                      <div v-if="msg.content" :class="msg.imageBase64 ? 'px-3 pb-2 pt-1' : ''">
                        <p class="text-sm leading-relaxed whitespace-pre-wrap">{{ msg.content }}</p>
                      </div>
                    </div>
                  </div>
                </div>

                <div v-if="isSending" class="flex items-start space-x-3 max-w-[95%]">
                   <div class="w-8 h-8 rounded-full bg-slate-900 text-white flex items-center justify-center text-xs shrink-0 shadow-md">AI</div>
                   <div class="bg-slate-50 border border-slate-100 rounded-2xl rounded-tl-sm px-5 py-4 shadow-sm flex items-center space-x-1">
                     <div class="w-2 h-2 bg-slate-400 rounded-full animate-bounce" style="animation-delay: 0ms"></div>
                     <div class="w-2 h-2 bg-slate-400 rounded-full animate-bounce" style="animation-delay: 150ms"></div>
                     <div class="w-2 h-2 bg-slate-400 rounded-full animate-bounce" style="animation-delay: 300ms"></div>
                   </div>
                </div>
              </div>

              <!-- Chat input -->
              <div class="shrink-0 pt-3 mt-1 bg-white border-t border-slate-100 flex flex-col relative w-full">
                <div v-if="pendingImage" class="mb-2 relative w-16 h-16 ml-3">
                  <img :src="pendingImage" class="w-full h-full object-cover rounded border border-slate-200 shadow-sm" />
                  <button @click="removePendingImage" class="absolute -top-2 -right-2 bg-red-500/90 text-white rounded-full w-5 h-5 flex items-center justify-center text-xs pb-[1px] hover:bg-red-600">×</button>
                </div>
                <div class="flex items-center space-x-2 w-full">
                  <form @submit.prevent="sendMessage" class="relative flex items-center flex-1">
                    <input type="file" accept="image/*" class="hidden" ref="imageInputRef" @change="onImageSelected" />
                    <button type="button" @click="triggerImageUpload" class="absolute left-2 w-10 h-10 flex items-center justify-center text-slate-400 hover:text-green-500 transition-colors z-10" :disabled="isSending || isAnalyzing">
                      <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" stroke-width="2" stroke="currentColor" class="w-6 h-6">
                        <path stroke-linecap="round" stroke-linejoin="round" d="m2.25 15.75 5.159-5.159a2.25 2.25 0 0 1 3.182 0l5.159 5.159m-1.5-1.5 1.409-1.409a2.25 2.25 0 0 1 3.182 0l2.909 2.909m-18 3.75h16.5a1.5 1.5 0 0 0 1.5-1.5V6a1.5 1.5 0 0 0-1.5-1.5H3.75A1.5 1.5 0 0 0 2.25 6v12a1.5 1.5 0 0 0 1.5 1.5Zm10.5-11.25h.008v.008h-.008V8.25Zm.375 0a.375.375 0 1 1-.75 0 .375.375 0 0 1 .75 0Z" />
                      </svg>
                    </button>
                    <input 
                      v-model="userInput" 
                      type="text" 
                      :placeholder="isRecording === 'chat' ? '倾听中...' : '附图追问大模型...'" 
                      class="w-full bg-slate-50 border border-slate-200 text-slate-800 rounded-full pl-12 pr-14 py-3.5 focus:outline-none focus:ring-2 focus:ring-green-400 focus:border-transparent transition-all"
                      :disabled="isSending || isAnalyzing"
                    />
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
            <span class="bg-white/20 backdrop-blur-sm px-3 py-1 rounded-full text-xs font-bold">🔔 系统公告</span>
            <button @click="dismissPopup" class="w-8 h-8 bg-white/20 backdrop-blur-sm rounded-full flex items-center justify-center text-white font-bold active:scale-90 transition-transform">×</button>
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

@keyframes fadeIn { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
.animate-fadeIn { animation: fadeIn 0.4s ease-out; }

@keyframes scaleIn { 0% { transform: scale(0); opacity: 0; } 60% { transform: scale(1.15); opacity: 1; } 100% { transform: scale(1); opacity: 1; } }
.animate-scaleIn { animation: scaleIn 0.5s cubic-bezier(0.16, 1, 0.3, 1); }

@keyframes tap { 0% { transform: scale(1) translateY(0); } 10% { transform: scale(0.9) translateY(4px); } 20% { transform: scale(1) translateY(0); } 100% { transform: scale(1) translateY(0); } }
.animate-tap { animation: tap 2s ease-in-out infinite; }

.bg-aurora { background: linear-gradient(-45deg, #020617, #064e3b, #0f172a); background-size: 400% 400%; animation: gradientBG 15s ease infinite; }
@keyframes gradientBG { 0% { background-position: 0% 50%; } 50% { background-position: 100% 50%; } 100% { background-position: 0% 50%; } }

@keyframes blob { 0% { transform: translate(0px, 0px) scale(1); } 33% { transform: translate(30px, -50px) scale(1.1); } 66% { transform: translate(-20px, 20px) scale(0.9); } 100% { transform: translate(0px, 0px) scale(1); } }
.animate-blob { animation: blob 8s infinite alternate cubic-bezier(0.4, 0, 0.2, 1); }
.animation-delay-2000 { animation-delay: 2s; }
.animation-delay-4000 { animation-delay: 4s; }

.custom-scrollbar::-webkit-scrollbar { width: 4px; }
.custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
.custom-scrollbar::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 4px; }
</style>

