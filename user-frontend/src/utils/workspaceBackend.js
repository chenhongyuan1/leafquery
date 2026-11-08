import { normalizeDiagnosisResponse } from './diagnosisSelection.js'
import { normalizeDetectionSummary } from './detectionPresentation.js'
import { isMeaninglessReview } from './reviewConclusion.js'

export function normalizeIdentification(payload) {
  const result = normalizeDiagnosisResponse(payload)
  const reviewResult = payload.review_result ?? payload.reviewResult ?? ''
  const reviewError = payload.report_error ?? payload.reviewError ?? ''
  const conclusion = reviewResult.match(/【确诊结果】[：:]?\s*([^\n]+)/)?.[1]?.replace(/\*/g, '').trim()
  return {
    ...result, pestName: isMeaninglessReview(reviewResult) ? '无意义' : conclusion || result.pestName,
    confidence: Math.min(1, Math.max(0, Number.isFinite(result.confidence) ? result.confidence : 0)),
    prediction: payload.prediction || null, detectedSummary: normalizeDetectionSummary(result.detectedSummary), reviewResult, reviewError,
    yoloUsed: payload.yolo_used ?? payload.yoloUsed ?? Boolean(payload.prediction),
    reviewRequired: payload.review_required ?? payload.reviewRequired ?? false
  }
}

export function readImage(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(new Error('图片读取失败，请重新添加。'))
    reader.readAsDataURL(file)
  })
}

export function buildChatPayload(record, currentTurn) {
  const messages = []
  const turns = record.turns.slice(0, record.turns.indexOf(currentTurn) + 1)
  for (const turn of turns) {
    if (turn.mode === 'identify') {
      messages.push({ role: 'user', content: `本次识别范围：${turn.categories.join('、')}`, imageBase64: turn.images[0]?.dataUrl || undefined })
      messages.push({ role: 'assistant', content: `识别结果：${turn.pestName || '未识别'}。${turn.yoloUsed ? `YOLO 置信度：${Math.round(turn.confidence * 100)}%。` : '未使用 YOLO。'}\n${turn.reviewResult || turn.reviewError || ''}` })
      if (turn.report) messages.push({ role: 'assistant', content: turn.report })
    } else {
      const images = turn.images.filter(image => image.dataUrl)
      messages.push({ role: 'user', content: turn.message || '请结合图片回答', imageBase64: images[0]?.dataUrl || undefined })
      for (const image of images.slice(1)) messages.push({ role: 'user', content: '同次提问的补充图片', imageBase64: image.dataUrl })
      if (turn.status === 'complete' && turn.reply) messages.push({ role: 'assistant', content: turn.reply })
    }
  }
  const diagnosis = record.turns.find(turn => turn.mode === 'identify')
  return { pestName: diagnosis?.pestName || '通用农业咨询', messages }
}

// Chat/report storage is local to this browser; recognition snapshots also use the existing record API.
let database
function openDatabase() {
  if (!database) database = new Promise((resolve, reject) => {
    const request = indexedDB.open('leafquery-workspace', 1)
    request.onupgradeneeded = () => request.result.createObjectStore('sessions')
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => { database = null; reject(request.error) }
  })
  return database
}
export async function loadWorkspace(owner) {
  const db = await openDatabase()
  return new Promise((resolve, reject) => {
    const request = db.transaction('sessions').objectStore('sessions').get(owner)
    request.onsuccess = () => resolve(request.result || [])
    request.onerror = () => reject(request.error)
  })
}
export async function saveWorkspace(owner, records) {
  const db = await openDatabase()
  return new Promise((resolve, reject) => {
    const transaction = db.transaction('sessions', 'readwrite')
    transaction.objectStore('sessions').put(records, owner)
    transaction.oncomplete = resolve
    transaction.onerror = () => reject(transaction.error)
    transaction.onabort = () => reject(transaction.error)
  })
}
