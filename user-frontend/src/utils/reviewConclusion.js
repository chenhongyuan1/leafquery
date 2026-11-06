export const MEANINGLESS_REVIEW_REPORT_MESSAGE = '复核确诊为“无意义”，不能生成诊断报告。请重新上传清晰的作物或病虫害图片。'

export function isMeaninglessReview(reviewResult) {
  if (typeof reviewResult !== 'string' || !reviewResult.trim()) return false
  const text = reviewResult.replace(/<think>[\s\S]*?<\/think>/gi, '').replace(/[*`_#>]/g, '').trim()
  const diagnosis = text.match(/(?:【\s*确诊结果\s*】\s*[:：]?|(?:^|[\r\n])\s*确诊结果\s*[:：])\s*([^\r\n]+)/u)
  const value = (diagnosis ? diagnosis[1] : text).replace(/^[\s\p{P}\p{Z}]+|[\s\p{P}\p{Z}]+$/gu, '')
  return value === '无意义'
}
