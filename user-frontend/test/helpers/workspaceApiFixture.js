export function respondWorkspaceApi(request, calls, delay = 850) {
  const path = new URL(request.url()).pathname
  if (!path.startsWith('/api/')) return request.continue()
  let data = { code: 200, data: [] }
  if (/\/api\/(pest|ai)\//.test(path)) calls.push(path)
  if (path === '/api/pest/identify') data = {
    prediction: { pest_name: '疑似叶片病斑', primary_target_zh: '疑似叶片病斑', confidence: .86, scene_type: 'single' },
    image_url: '/vite.svg', yolo_used: true, review_required: true,
    review_result: '【确诊结果】：疑似叶片病斑\n接口替身的视觉复核结论。'
  }
  if (path === '/api/pest/diagnose') data = { report: '识别范围：水稻\n示例结论：疑似叶片病斑。\n\n示例建议\n1. 补充叶片正反面及整株图片。\n2. 记录发现时间、受影响部位与变化趋势。\n3. 结合现场情况进一步核验。\n\n本报告为固定模拟内容，仅由测试接口替身返回。' }
  if (path === '/api/ai/chat') data = { reply: '已收到关于本次水稻识别的追问。这是一条模拟回答，仅由测试接口替身返回。\n你可以继续补充作物类型、发现时间或图片。' }
  const respond = () => request.respond({ status: 200, contentType: 'application/json', body: JSON.stringify(data) }).catch(() => {})
  return /\/api\/(pest|ai)\//.test(path) ? setTimeout(respond, delay) : respond()
}
