<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import PromptInput from '../../components/pc/PromptInput.vue'
import WorkspaceRecords from '../../components/pc/WorkspaceRecords.vue'
import WorkspaceConversation from '../../components/pc/WorkspaceConversation.vue'
import { diagnosisApi, getApiErrorMessage, recordApi } from '../../api/index.js'
import { useFarmStore } from '../../stores/farmStore.js'
import { AUTH_CHANGE_EVENT, getStoredUser } from '../../utils/accountSecurity.js'
import { buildChatPayload, loadWorkspace, normalizeIdentification, readImage, saveWorkspace } from '../../utils/workspaceBackend.js'
import { isMeaninglessReview } from '../../utils/reviewConclusion.js'

const showWelcome = ref(true)
const records = ref([])
const activeRecordId = ref(null)
const activeRecord = computed(() => records.value.find(record => record.id === activeRecordId.value))
const turns = computed(() => activeRecord.value?.turns || [])
const stream = ref(null)
const composer = ref(null)
const composerHeight = ref(58)
const selectedTurn = ref(null)
const recordsOpen = ref(true)
const farmStore = useFarmStore()
const ready = ref(false)
const notice = ref('')
const voiceRecording = ref(false)
const voiceBusy = ref(false)
const requests = new Set()
const sourceFiles = new Map()
let owner = String(getStoredUser()?.userId || 'guest')
let generation = 0
let disposed = false
let saveTimer
let recorder
let audioStream
let recordingTimer
let draftVersion = 0
const previewURLs = new Set()
const summaryAnimations = new Map()
let resizeObserver
let nextId = 0
let nextRecordId = 0
let highlightTimer
const scrollBehavior = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth'

const recognitionRecords = computed(() => records.value.filter(record => record.mode === 'identify'))
const conversationRecords = computed(() => records.value.filter(record => record.mode === 'ask'))
const busy = computed(() => !ready.value || voiceBusy.value || activeRecord.value?.deleting || activeRecord.value?.turns.some(turn => turn.status === 'pending' || turn.running || turn.reportStatus === 'pending'))
const followUpAvailable = computed(() => activeRecord.value?.mode === 'identify' && activeRecord.value.turns[0]?.status === 'complete' && !activeRecord.value.turns.some(turn => turn.mode === 'ask'))

async function scrollToLatest() {
  await nextTick()
  if (stream.value) stream.value.scrollTo({ top: stream.value.scrollHeight, behavior: scrollBehavior() })
}

function persistSessions() {
  if (!ready.value) return
  clearTimeout(saveTimer)
  const snapshot = JSON.parse(JSON.stringify(records.value.slice(-100)))
  const targetOwner = owner
  saveWorkspace(targetOwner, snapshot).catch(() => {
    if (!disposed && targetOwner === owner) notice.value = '浏览器历史保存失败，请检查可用存储空间。'
  })
}
watch(records, () => { clearTimeout(saveTimer); saveTimer = setTimeout(persistSessions, 150) }, { deep: true })

function controller() {
  const request = new AbortController()
  requests.add(request)
  return request
}
function stillCurrent(version) { return !disposed && version === generation }

async function sendMessage(message, meta) {
  if (busy.value) return
  showWelcome.value = false
  const mode = meta.mode === 'identify' ? 'identify' : 'ask'
  const categories = [...(meta.categories || [])]
  const images = meta.attachments.map(file => {
    const url = URL.createObjectURL(file)
    previewURLs.add(url)
    return { name: file.name, url, localUrl: url }
  })
  const turn = {
    id: ++nextId, mode, message: message.trim(), categories, images,
    title: mode === 'identify' ? `${categories.join('、')} · 图片识别` : message.trim() || '图片询问',
    time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false }),
    status: 'pending', model: meta.model || '', reply: '',
    locationId: farmStore.activeCrop?.locationId || '101010100',
    cropId: farmStore.activeCrop?.id ?? null, cropName: farmStore.activeCrop?.name || '',
    city: farmStore.activeCrop?.city || '', region: farmStore.activeCrop?.region || ''
  }
  let record = activeRecord.value
  if (mode === 'identify' || !record) {
    records.value.push({ id: ++nextRecordId, mode, title: turn.title, time: turn.time, turns: [] })
    activeRecordId.value = nextRecordId
    record = activeRecord.value
  }
  record.turns.push(turn)
  sourceFiles.set(turn.id, meta.attachments)
  // Use the reactive turn, so async updates reach the view immediately.
  runTurn(record, record.turns.at(-1))
  scrollToLatest()
}

async function runTurn(record, turn) {
  if (requests.size && turn.status === 'pending' && turn.running) return
  const version = generation
  const request = controller()
  turn.status = 'pending'; turn.error = ''; turn.running = true; turn.startedAt = Date.now(); turn.finishedAt = 0
  try {
    const files = sourceFiles.get(turn.id) || []
    for (const [index, file] of files.entries()) {
      if (!turn.images[index].dataUrl) turn.images[index].dataUrl = await readImage(file)
    }
    if (!stillCurrent(version)) return
    if (turn.mode === 'identify') {
      let file = files[0]
      if (!file && turn.images[0]?.dataUrl) file = new File([await (await fetch(turn.images[0].dataUrl)).blob()], 'identification.jpg', { type: 'image/jpeg' })
      if (!file) throw new Error('原图片已不可用，请重新添加图片。')
      const form = new FormData()
      form.append('file', file); form.append('categories', turn.categories.join(',')); form.append('locationId', turn.locationId)
      const response = await diagnosisApi.identify(form, { signal: request.signal })
      if (!stillCurrent(version)) return
      if (!response.data || !('prediction' in response.data || 'review_result' in response.data)) throw new Error('识别服务返回了无效结果。')
      const result = normalizeIdentification(response.data)
      Object.assign(turn, result, { status: 'complete', reply: result.reviewResult || (result.yoloUsed ? 'YOLO 检测完成。' : '视觉识别完成。') })
      if (result.imageUrl) turn.images[0].url = result.imageUrl
      record.title = result.pestName
      // Keep cloud writes tied to the account and farm snapshot that sent this image.
      const payload = { userId: owner === 'guest' ? null : Number(owner), cropId: turn.cropId, cropName: turn.cropName,
        pestName: turn.pestName, confidence: turn.confidence, locationId: turn.locationId, city: turn.city, region: turn.region,
        imageUrl: result.imageUrl, yoloUsed: turn.yoloUsed, reviewRequired: turn.reviewRequired, reviewResult: turn.reviewResult, reviewError: turn.reviewError }
      try {
        if (payload.userId) {
          const saved = await recordApi.add(payload, { signal: request.signal })
          if (!stillCurrent(version)) return
          const snapshot = saved.data?.data
          if (!snapshot?.id) throw new Error('记录接口未返回保存结果。')
          record.serverId = snapshot.id
          farmStore.syncIdentificationHistory([snapshot, ...farmStore.identificationHistory.filter(item => String(item.id) !== String(snapshot.id))])
        } else {
          await farmStore.saveIdentification(payload)
          record.serverId = farmStore.identificationHistory.find(item => item.imageUrl === result.imageUrl)?.id
        }
      } catch (error) {
        if (!stillCurrent(version)) return
        turn.saveError = '识别完成，但云端记录保存失败，已保存在本机。'
        notice.value = turn.saveError
      }
    } else {
      const response = await diagnosisApi.chat(buildChatPayload(record, turn), { signal: request.signal })
      if (!stillCurrent(version)) return
      const reply = response.data?.reply
      if (typeof reply !== 'string' || !reply.trim()) throw new Error('问答服务未返回有效内容。')
      if (/^AI (?:对话服务调用失败|聊天服务存在异常)/.test(reply)) throw new Error('AI 问答服务当前不可用，请检查管理端的接口地址、模型名称和密钥。')
      turn.reply = reply
      turn.status = 'complete'
    }
  } catch (error) {
    if (!stillCurrent(version) || request.signal.aborted) return
    turn.status = 'error'
    turn.error = getApiErrorMessage(error, '请求失败，请稍后重试。')
  } finally {
    requests.delete(request)
    if (stillCurrent(version)) {
      turn.running = false
      turn.finishedAt = Date.now()
      persistSessions()
      if (activeRecordId.value === record.id && turn.mode !== 'identify') scrollToLatest()
    }
  }
}

function retryTurn(turnId) {
  const record = activeRecord.value
  const turn = record?.turns.find(item => item.id === turnId)
  if (!turn || turn.status !== 'error' || busy.value) return
  runTurn(record, turn)
}

async function selectRecord({ record }) {
  const changed = activeRecordId.value !== record.id
  activeRecordId.value = record.id
  showWelcome.value = false
  selectedTurn.value = record.turns[0]?.id || null
  if (changed) { draftVersion++; stopVoice(true); await composer.value?.resetDraft(record.turns.at(-1)?.mode || record.mode) }
  await nextTick()
  if (stream.value) stream.value.scrollTo({ top: 0, behavior: scrollBehavior() })
  clearTimeout(highlightTimer)
  highlightTimer = setTimeout(() => { selectedTurn.value = null }, 1600)
}

async function startNewRecord(type) {
  draftVersion++
  stopVoice(true)
  activeRecordId.value = null
  showWelcome.value = true
  selectedTurn.value = null
  await composer.value?.resetDraft(type === 'recognition' ? 'identify' : 'ask')
}

async function removeRecord(record) {
  if (record.deleting || record.turns.some(turn => turn.running || turn.status === 'pending' || turn.reportStatus === 'pending')) return
  const label = record.mode === 'identify' ? '识别' : '对话'
  if (!window.confirm(`确定删除这条${label}记录“${record.title}”及其中的对话吗？删除后无法恢复。`)) return
  const version = generation
  const request = controller()
  record.deleting = true
  notice.value = ''
  try {
    if (record.mode === 'identify' && record.serverId) {
      if (owner !== 'guest') {
        try { await recordApi.remove(record.serverId, { signal: request.signal }) }
        catch (error) { if (error.status !== 404) throw error }
        if (!stillCurrent(version)) return
        farmStore.syncIdentificationHistory(farmStore.identificationHistory.filter(item => String(item.id) !== String(record.serverId)))
      } else {
        await farmStore.removeIdentification(record.serverId)
      }
    }
    if (!stillCurrent(version)) return
    records.value = records.value.filter(item => item.id !== record.id)
    for (const turn of record.turns) {
      sourceFiles.delete(turn.id)
      summaryAnimations.get(turn.id)?.cancel()
      summaryAnimations.delete(turn.id)
      for (const image of turn.images) {
        const url = image.localUrl || image.url
        if (previewURLs.delete(url)) URL.revokeObjectURL(url)
      }
    }
    if (activeRecordId.value === record.id) await startNewRecord(record.mode === 'identify' ? 'recognition' : 'conversation')
    persistSessions()
    await nextTick()
    document.querySelector(`[aria-label="新建${label}"]`)?.focus({ preventScroll: true })
  } catch (error) {
    if (stillCurrent(version) && !request.signal.aborted) notice.value = `删除失败：${getApiErrorMessage(error)}，记录已保留。`
  } finally {
    requests.delete(request)
    if (stillCurrent(version)) record.deleting = false
  }
}

async function generateReport(turnId) {
  const record = records.value.find(item => item.turns.some(turn => turn.id === turnId))
  const turn = record?.turns.find(item => item.id === turnId)
  if (!turn || turn.mode !== 'identify' || turn.status !== 'complete' || turn.reportStatus === 'pending' || isMeaninglessReview(turn.reviewResult)) return
  if (turn.report) { scrollToReport(turnId); return }
  const request = controller(), version = generation
  turn.reportStatus = 'pending'; turn.reportError = ''; turn.reportStartedAt = Date.now(); turn.reportFinishedAt = 0
  scrollToReport(turnId)
  try {
    const form = new URLSearchParams({ imageUrl: turn.imageUrl || '', categories: turn.categories.join(','), locationId: turn.locationId || '101010100', reviewResult: turn.reviewResult || '' })
    if (turn.prediction) form.append('predictionJson', JSON.stringify(turn.prediction))
    const response = await diagnosisApi.diagnose(form, { signal: request.signal })
    if (!stillCurrent(version)) return
    if (!response.data?.report?.trim()) throw new Error('报告服务未返回有效内容，请重试。')
    turn.report = response.data.report
    turn.reportStatus = 'complete'
  } catch (error) {
    if (stillCurrent(version) && !request.signal.aborted) { turn.reportStatus = 'error'; turn.reportError = getApiErrorMessage(error) }
  } finally {
    requests.delete(request)
    if (stillCurrent(version)) { turn.reportFinishedAt = Date.now(); persistSessions(); if (activeRecordId.value === record.id) scrollToReport(turnId) }
  }
}

async function loadHistory() {
  const version = ++generation
  ready.value = false
  voiceBusy.value = false
  draftVersion++
  notice.value = ''
  requests.forEach(request => request.abort()); requests.clear()
  stopVoice(true)
  records.value = []; activeRecordId.value = null; showWelcome.value = true
  previewURLs.forEach(url => URL.revokeObjectURL(url)); previewURLs.clear(); sourceFiles.clear()
  owner = String(getStoredUser()?.userId || 'guest')
  try {
    const cached = await loadWorkspace(owner)
    if (!stillCurrent(version)) return
    records.value = cached
    for (const record of records.value) for (const turn of record.turns) {
      turn.running = false
      if (turn.status === 'pending') { turn.status = 'error'; turn.error = '上次请求已中断，可重新尝试。' }
      if (turn.reportStatus === 'pending') { turn.reportStatus = 'error'; turn.reportError = '上次报告生成已中断，请重试。' }
      turn.images = (turn.images || []).map(image => ({ ...image, url: image.url?.startsWith('blob:') ? image.dataUrl || '' : image.url }))
    }
    records.value.forEach(record => { record.deleting = false })
  } catch { if (stillCurrent(version)) notice.value = '本机对话历史读取失败，仍可使用识别与问答。' }
  try { await farmStore.initialize() } catch { notice.value = '历史记录同步失败，仍可使用当前会话。' }
  if (!stillCurrent(version)) return
  nextId = Math.max(0, ...records.value.flatMap(record => record.turns.map(turn => turn.id)))
  nextRecordId = Math.max(0, ...records.value.map(record => record.id))
  const history = owner === 'guest' || farmStore.syncMode === 'cloud' ? farmStore.identificationHistory : []
  if (owner !== 'guest' && farmStore.syncMode !== 'cloud') notice.value = '云端历史暂时不可用，当前仅展示此账号的本机记录。'
  if (owner !== 'guest' && farmStore.syncMode === 'cloud') {
    const existingIds = new Set(history.map(item => String(item.id)))
    records.value = records.value.filter(record => record.mode !== 'identify' || !record.serverId || existingIds.has(String(record.serverId)))
  }
  for (const item of history) {
    if (records.value.some(record => record.mode === 'identify' && (record.serverId ? String(record.serverId) === String(item.id) : record.turns[0]?.imageUrl === item.imageUrl))) continue
    const time = new Date(item.time).toLocaleString('zh-CN', { hour12: false })
    records.value.push({ id: ++nextRecordId, serverId: item.id, mode: 'identify', title: item.pestName, time, turns: [{
      ...item, id: ++nextId, mode: 'identify', title: item.pestName, time, message: '', status: 'complete',
      categories: [item.cropName || '其他'], images: item.imageUrl ? [{ name: '识别图片', url: item.imageUrl }] : [],
      reply: item.reviewResult || '已恢复历史识别结果。', report: '', model: ''
    }] })
  }
  ready.value = true
  persistSessions()
}

function stopVoice(discard = false) {
  clearTimeout(recordingTimer)
  if (recorder && recorder.state !== 'inactive') { if (discard) recorder.onstop = null; recorder.stop() }
  audioStream?.getTracks().forEach(track => track.stop())
  audioStream = null; voiceRecording.value = false
}
async function toggleVoice() {
  if (voiceRecording.value) { stopVoice(); return }
  if (voiceBusy.value || busy.value) return
  const version = generation, voiceDraft = draftVersion
  voiceBusy.value = true
  try {
    if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) throw new Error('此浏览器不支持录音，请使用 HTTPS 或 localhost。')
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    if (!stillCurrent(version) || voiceDraft !== draftVersion) { stream.getTracks().forEach(track => track.stop()); return }
    audioStream = stream
    recorder = new MediaRecorder(stream)
    const chunks = []
    recorder.ondataavailable = event => { if (event.data.size) chunks.push(event.data) }
    recorder.onstop = async () => {
      if (!stillCurrent(version) || voiceDraft !== draftVersion) return
      voiceBusy.value = true
      const request = controller()
      try {
        const form = new FormData()
        form.append('audio', new Blob(chunks, { type: recorder.mimeType || 'audio/webm' }), 'voice.webm')
        form.append('dialect', localStorage.getItem('leafquery_dialect') || 'mandarin')
        const response = await diagnosisApi.speechToText(form, { signal: request.signal })
        if (stillCurrent(version) && voiceDraft === draftVersion) await composer.value?.appendText(response.data?.text || '')
      } catch (error) { if (stillCurrent(version)) notice.value = getApiErrorMessage(error, '语音识别失败。') }
      finally { requests.delete(request); if (stillCurrent(version)) voiceBusy.value = false }
    }
    recorder.start(); voiceRecording.value = true
    recordingTimer = setTimeout(() => stopVoice(), 60000)
  } catch (error) { stopVoice(true); if (stillCurrent(version)) notice.value = getApiErrorMessage(error, '无法使用麦克风，请检查权限。') }
  finally { if (stillCurrent(version)) voiceBusy.value = false }
}

async function scrollToReport(turnId) {
  await nextTick()
  const viewport = stream.value
  const card = viewport?.querySelector(`[data-turn-id="${turnId}"] .turn-user--identify`)
  const message = viewport?.querySelector(`[data-turn-id="${turnId}"] .turn-report-message`)
  if (!viewport || !card || !message) return
  const top = viewport.scrollTop + message.getBoundingClientRect().top - viewport.getBoundingClientRect().top - card.getBoundingClientRect().height - 24
  viewport.scrollTo({ top: Math.max(0, top), behavior: scrollBehavior() })
}

async function toggleSummary(turnId) {
  const turn = activeRecord.value?.turns.find(item => item.id === turnId)
  if (!turn || turn.mode !== 'identify') return
  const card = stream.value?.querySelector(`[data-turn-id="${turnId}"] .turn-user--identify`)
  const previousHeight = card?.getBoundingClientRect().height
  summaryAnimations.get(turnId)?.cancel()
  summaryAnimations.delete(turnId)
  turn.summaryCollapsed = !turn.summaryCollapsed
  await nextTick()
  if (card?.isConnected && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    const targetHeight = card.getBoundingClientRect().height
    const animation = card.animate([
      { height: `${previousHeight}px`, overflow: 'hidden' },
      { height: `${targetHeight}px`, overflow: 'hidden' }
    ], { duration: 280, easing: 'cubic-bezier(.22, 1, .36, 1)' })
    summaryAnimations.set(turnId, animation)
    try { await animation.finished } catch { return }
    if (summaryAnimations.get(turnId) !== animation) return
    summaryAnimations.delete(turnId)
  }
  if (turn.report || turn.reportStatus === 'pending') await scrollToReport(turnId)
}

onMounted(() => {
  loadHistory()
  window.addEventListener(AUTH_CHANGE_EVENT, loadHistory)
  resizeObserver = new ResizeObserver(entries => { composerHeight.value = entries[0].target.getBoundingClientRect().height })
  if (composer.value?.$el) resizeObserver.observe(composer.value.$el)
})
onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  persistSessions()
  disposed = true
  generation++
  clearTimeout(saveTimer)
  requests.forEach(request => request.abort())
  stopVoice(true)
  window.removeEventListener(AUTH_CHANGE_EVENT, loadHistory)
  clearTimeout(highlightTimer)
  summaryAnimations.forEach(animation => animation.cancel())
  previewURLs.forEach(url => URL.revokeObjectURL(url))
})
</script>

<template>
  <section class="pc-workspace" :class="{ 'records-collapsed': !recordsOpen, 'is-identify-view': activeRecord?.mode === 'identify' }" aria-label="识别工作台" data-prompt-collapse-area :style="{ '--composer-height': `${composerHeight}px` }">
    <Transition name="workspace-welcome" appear>
      <div v-if="showWelcome" class="workspace-welcome" aria-label="LeafQuery 欢迎">
        <div class="workspace-welcome-brand"><img src="/vite.svg" alt="" width="58" height="58" /><h1>LeafQuery</h1></div>
        <p>准备好识别询问了吗？</p>
      </div>
    </Transition>
    <div v-if="turns.length" ref="stream" class="workspace-output" role="log" aria-label="识别与对话" aria-live="polite">
      <WorkspaceConversation :turns="turns" :selected-id="selectedTurn" @report="generateReport" @retry="retryTurn" @collapse="toggleSummary" />
    </div>
    <WorkspaceRecords :recognition-records="recognitionRecords" :conversation-records="conversationRecords" :active-id="activeRecordId" @select="selectRecord" @new="startNewRecord" @remove="removeRecord" @toggle="recordsOpen = $event" />
    <div v-if="notice" class="workspace-notice" role="status">{{ notice }}<button type="button" aria-label="关闭提示" @click="notice = ''">×</button></div>
    <PromptInput ref="composer" :follow-up-available="followUpAvailable" :busy="busy" :voice-recording="voiceRecording" :voice-busy="voiceBusy" :voice-connected="true" submit-message="" @submit="sendMessage" @voice-request="toggleVoice" />
  </section>
</template>

<style scoped>
.pc-workspace {
  --workspace-bottom-gap: clamp(44px, 8vh, 112px);
  position: relative;
  box-sizing: border-box;
  height: 100%;
  min-height: 0;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 32px 32px var(--workspace-bottom-gap);
}
.workspace-notice { position: absolute; bottom: 8px; left: 32px; right: 32px; text-align: center; color: #b87524; font-size: 13px; }
.workspace-notice button { margin-left: 8px; border: 0; background: transparent; cursor: pointer; }
.workspace-output {
  position: absolute;
  top: 24px;
  left: 8px;
  right: 348px;
  bottom: calc(var(--composer-height) + var(--workspace-bottom-gap) + 24px);
  overflow-y: auto;
  overflow-x: hidden;
  padding: 8px 16px 20px 24px;
  direction: rtl;
  scrollbar-gutter: stable;
  scrollbar-width: thin;
  scrollbar-color: #c5d5cc transparent;
  scroll-behavior: smooth;
  transition: right .28s cubic-bezier(.22, 1, .36, 1), padding .28s;
}
.workspace-output :deep(.workspace-conversation) { direction: ltr; max-width: 880px; margin-inline: auto; }
.dark .workspace-output { scrollbar-color: #34413e transparent; }
@media (max-width: 1280px) { .workspace-output { right: 306px; padding: 8px 10px 20px 16px; } }
.records-collapsed .workspace-output { right: 8px; padding-inline: 16px; scrollbar-gutter: stable both-edges; }
.is-identify-view .workspace-output { top: 0; padding-top: 0; }
.workspace-welcome {
  position: absolute;
  left: 50%;
  top: 40%;
  transform: translate(-50%, -50%);
  width: min(560px, calc(100% - 64px));
  text-align: center;
  color: #173b3a;
  pointer-events: none;
  user-select: none;
}
.workspace-welcome-brand { display: flex; align-items: center; justify-content: center; gap: 14px; }
.workspace-welcome-brand img { display: block; width: 58px; height: 58px; }
.workspace-welcome h1 { margin: 0; font: 750 44px/1.2 'Segoe UI', 'Microsoft YaHei', sans-serif; letter-spacing: -1.5px; }
.workspace-welcome p { margin: 21px 0 0; font-size: 27px; line-height: 1.5; font-weight: 500; letter-spacing: .02em; color: #6b827a; }
.dark .workspace-welcome { color: #e9f4ef; }
.dark .workspace-welcome p { color: #9cb8ad; }
.workspace-welcome-enter-active, .workspace-welcome-leave-active { transition: opacity .3s, transform .35s cubic-bezier(.22,1,.36,1); }
.workspace-welcome-enter-from, .workspace-welcome-leave-to { opacity: 0; transform: translate(-50%, calc(-50% + 10px)); }
@media (max-height: 760px) {
  .workspace-welcome { top: 22%; }
  .workspace-welcome h1 { font-size: 34px; }
  .workspace-welcome-brand img { width: 44px; height: 44px; }
  .workspace-welcome p { margin-top: 12px; font-size: 22px; }
}
@media (max-height: 600px) {
  .pc-workspace { --workspace-bottom-gap: 24px; }
  .workspace-welcome { top: 18%; }
  .workspace-welcome h1 { font-size: 30px; }
  .workspace-welcome-brand img { width: 38px; height: 38px; }
  .workspace-welcome p { margin-top: 10px; font-size: 19px; }
}
@media (prefers-reduced-motion: reduce) {
  .workspace-output { scroll-behavior: auto; transition: none; }
  .workspace-welcome-enter-active, .workspace-welcome-leave-active { transition: none; }
}
</style>
