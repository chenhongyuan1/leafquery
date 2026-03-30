export function createEmptyDiagnosisResult() {
  return {
    pestName: '',
    confidence: 0,
    imageUrl: '',
    imageToken: '',
    primaryTarget: '',
    primaryTargetZh: '',
    primaryConfidence: 0,
    sceneType: 'single',
    classCount: 0,
    targetCount: 0,
    classNamesZh: [],
    detectedSummary: [],
    report: '',
    selectionOptions: {
      cropOptions: [],
      targetOptions: []
    },
    selectionRequired: false,
    selectionConflict: false,
    selectionConflictReason: '',
    reviewRequired: false,
    reviewReason: '',
    finalized: false
  }
}

function ensureArray(value) {
  if (Array.isArray(value)) {
    return value.filter(Boolean)
  }
  if (typeof value === 'string' && value.trim()) {
    return value
      .split(/[、,]/)
      .map(item => item.trim())
      .filter(Boolean)
  }
  return []
}

function normalizeSelectionOption(option = {}, fallbackType = 'unknown') {
  const cropNames = ensureArray(option.crop_names ?? option.cropNames)
  return {
    value: option.value || option.label || '',
    label: option.label || option.value || '',
    targetType: option.target_type || option.targetType || fallbackType,
    cropNames,
    common: Boolean(option.common),
    source: option.source || 'catalog'
  }
}

export function normalizeSelectionOptions(rawOptions = {}) {
  return {
    cropOptions: ensureArray(rawOptions.crop_options ?? rawOptions.cropOptions).map(option =>
      normalizeSelectionOption(option, 'crop')
    ),
    targetOptions: ensureArray(rawOptions.target_options ?? rawOptions.targetOptions).map(option =>
      normalizeSelectionOption(option, 'unknown')
    )
  }
}

export function normalizeDiagnosisResponse(payload = {}, fallbackImageUrl = '') {
  const predictionRaw = payload.prediction || payload || {}
  const normalized = createEmptyDiagnosisResult()
  const fallbackName = predictionRaw.primary_target_zh || predictionRaw.primaryTargetZh || predictionRaw.pest_name || predictionRaw.pestName || '未能识别出具体病虫害'
  const safeName = String(fallbackName).toLowerCase() === 'unknown' ? '未能识别出具体病虫害' : fallbackName

  normalized.pestName = safeName
  normalized.confidence = Number(predictionRaw.confidence ?? predictionRaw.primary_confidence ?? predictionRaw.primaryConfidence ?? 0)
  normalized.imageUrl = payload.image_url || payload.imageUrl || fallbackImageUrl || ''
  normalized.imageToken = payload.image_token || payload.imageToken || ''
  normalized.primaryTarget = predictionRaw.primary_target || predictionRaw.primaryTarget || ''
  normalized.primaryTargetZh = predictionRaw.primary_target_zh || predictionRaw.primaryTargetZh || safeName
  normalized.primaryConfidence = Number(predictionRaw.primary_confidence ?? predictionRaw.primaryConfidence ?? normalized.confidence)
  normalized.sceneType = predictionRaw.scene_type || predictionRaw.sceneType || 'single'
  normalized.classCount = Number(predictionRaw.class_count ?? predictionRaw.classCount ?? 0)
  normalized.targetCount = Number(predictionRaw.target_count ?? predictionRaw.targetCount ?? 0)
  normalized.classNamesZh = ensureArray(predictionRaw.class_names_zh ?? predictionRaw.classNamesZh)
  normalized.detectedSummary = ensureArray(predictionRaw.detected_summary ?? predictionRaw.detectedSummary)
  normalized.report = payload.report || ''
  normalized.selectionOptions = normalizeSelectionOptions(payload.selection_options ?? payload.selectionOptions ?? {})
  normalized.selectionRequired = Boolean(payload.selection_required ?? payload.selectionRequired)
  normalized.selectionConflict = Boolean(payload.selection_conflict ?? payload.selectionConflict)
  normalized.selectionConflictReason = payload.selection_conflict_reason || payload.selectionConflictReason || ''
  normalized.reviewRequired = Boolean(payload.review_required ?? payload.reviewRequired)
  normalized.reviewReason = payload.review_reason || payload.reviewReason || ''
  normalized.finalized = Boolean(payload.finalized)
  return normalized
}

export function buildFilteredTargetOptions(targetOptions = [], selectedCropNames = []) {
  const selected = new Set(ensureArray(selectedCropNames))
  const normalizedOptions = targetOptions.map(option => normalizeSelectionOption(option))
  const deduped = []
  const seen = new Set()

  normalizedOptions.forEach(option => {
    const key = option.label || option.value
    if (!key || seen.has(key)) {
      return
    }

    const cropMatched = !selected.size || !option.cropNames.length || option.cropNames.some(name => selected.has(name))
    if (cropMatched || option.source === 'detected') {
      seen.add(key)
      deduped.push(option)
    }
  })

  return deduped.sort((left, right) => {
    const leftScore = left.source === 'detected' ? 0 : left.common ? 1 : 2
    const rightScore = right.source === 'detected' ? 0 : right.common ? 1 : 2
    if (leftScore !== rightScore) {
      return leftScore - rightScore
    }
    return left.label.localeCompare(right.label, 'zh-CN')
  })
}

export function hasAnySelection(selectedCropNames = [], selectedTargetNames = []) {
  return ensureArray(selectedCropNames).length > 0 || ensureArray(selectedTargetNames).length > 0
}

export function toggleSelectionItem(list = [], value = '') {
  if (!value) {
    return [...list]
  }

  return list.includes(value)
    ? list.filter(item => item !== value)
    : [...list, value]
}
