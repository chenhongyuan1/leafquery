const LLM_FIELDS = {
  'doubao.api.url': {
    label: '接口地址（Base URL）', type: 'url', placeholder: 'https://api.openai.com/v1',
    hint: '可填写基础地址或完整 /chat/completions 地址；旧 /responses 地址会自动兼容。'
  },
  'doubao.api.key': {
    label: 'API Key', type: 'password', placeholder: '填写对应服务的 API Key',
    hint: '必须与接口地址属于同一服务。'
  },
  'doubao.api.model': {
    label: '模型 ID', type: 'text', placeholder: '填写服务提供方支持的模型 ID',
    hint: '使用该服务提供的完整模型 ID 或部署 ID；图文提问需要模型支持图片输入。'
  }
}

export function getModelField(key) {
  return LLM_FIELDS[key] || null
}

// Keep the preview aligned with the backend's Chat Completions endpoint rules.
export function getChatEndpoint(value) {
  if (!value?.trim()) return ''
  let url
  try { url = new URL(value.trim()) } catch { throw new Error('请输入完整的 HTTP 或 HTTPS 接口地址。') }
  if (!['http:', 'https:'].includes(url.protocol) || !url.hostname) {
    throw new Error('接口地址仅支持 HTTP 或 HTTPS。')
  }
  let path = url.pathname.replace(/\/+$/, '')
  if (!path) path = '/v1/chat/completions'
  else if (path.endsWith('/responses')) path = path.slice(0, -'/responses'.length) + '/chat/completions'
  else if (!path.endsWith('/chat/completions')) path += '/chat/completions'
  url.pathname = path
  url.hash = ''
  return url.toString()
}

export function validateModelValue(key, value) {
  const text = value?.trim() || ''
  if (!getModelField(key) || !text) return ''
  if (key === 'doubao.api.url') {
    try { getChatEndpoint(text) } catch (error) { return error.message }
  } else if (/\s/.test(text)) {
    return `${getModelField(key).label} 不能包含空格或换行。`
  }
  return ''
}
