export const SCENE_META = {
  single: {
    label: '单一目标',
    description: '图像中主要识别为一种病虫害类型，可直接围绕主目标生成建议。'
  },
  multi: {
    label: '多目标场景',
    description: '图像中同时检测到多种病虫害类型，建议结合综合报告统一处理。'
  },
  uncertain: {
    label: '置信度偏低',
    description: '当前检测结果置信度偏低，建议结合原图、现场症状或补拍近景进行复核。'
  },
  empty: {
    label: '未检出目标',
    description: '暂未检测到有效病虫害目标，建议补拍清晰的叶片局部图片。'
  }
}

export function getSceneMeta(sceneType = 'single') {
  return SCENE_META[sceneType] || SCENE_META.single
}

export function normalizeDetectionSummary(detectedSummary = []) {
  if (!Array.isArray(detectedSummary)) return []

  return detectedSummary
    .map((item, index) => ({
      id: `${item?.name || item?.nameZh || item?.name_zh || 'target'}-${index}`,
      name: item?.name || '',
      nameZh: item?.nameZh || item?.name_zh || item?.name || '未命名目标',
      count: Number(item?.count ?? 0),
      maxConfidence: Number(item?.maxConfidence ?? item?.max_confidence ?? 0),
      avgConfidence: Number(item?.avgConfidence ?? item?.avg_confidence ?? 0)
    }))
    .filter(item => item.nameZh)
}

export function normalizeClassNames(classNamesZh = [], detectedSummary = []) {
  if (Array.isArray(classNamesZh) && classNamesZh.length > 0) {
    return [...new Set(classNamesZh.filter(Boolean))]
  }

  return [...new Set(normalizeDetectionSummary(detectedSummary).map(item => item.nameZh))]
}

export function formatConfidencePercent(value, digits = 1) {
  const numericValue = Number(value)
  const safeValue = Number.isFinite(numericValue) ? numericValue : 0
  return `${(safeValue * 100).toFixed(digits)}%`
}

export function getPrimaryDisplayName(result = {}) {
  return result?.primaryTargetZh || result?.pestName || '未识别'
}

export function getReportTitle(result = {}) {
  if (result?.pestName === '通用农业咨询') return 'AI 诊断咨询'

  const sceneType = result?.sceneType || 'single'
  if (sceneType === 'multi') return '多目标综合分析报告'
  if (sceneType === 'uncertain') return '复核诊断报告'
  if (sceneType === 'empty') return '补拍建议'

  return `${getPrimaryDisplayName(result)} 分析报告`
}
