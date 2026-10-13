// Shared transport contract; neither frontend owns the other's authentication state.
export class ApiError extends Error {
  constructor(message, { response, code, status, cause } = {}) {
    super(message, { cause })
    this.name = 'ApiError'
    this.response = response
    this.status = status ?? response?.status ?? 0
    this.code = code ?? response?.data?.code ?? this.status
  }
}

const statusMessages = {
  400: '请求参数有误，请检查后重试。',
  401: '登录验证失败，请重新登录后再试。',
  403: '当前账号没有权限执行这项操作。',
  404: '未找到对应数据，请刷新后重试。',
  409: '数据已发生变化，请刷新后重试。',
  500: '服务暂时不可用，请稍后重试。'
}

export function normalizeApiError(error) {
  if (error instanceof ApiError) return error
  const response = error?.response
  const serverMessage = response?.data?.message || response?.data?.error
  const timedOut = ['ECONNABORTED', 'ETIMEDOUT'].includes(error?.code)
  const message = typeof serverMessage === 'string' && serverMessage.trim()
    ? serverMessage
    : timedOut ? '请求超时，请稍后重试。'
      : response ? statusMessages[response.status] || '请求失败，请稍后重试。'
        : '网络连接异常，请检查网络后重试。'
  return new ApiError(message, {
    response, status: response?.status,
    code: response?.data?.code ?? error?.code, cause: error
  })
}

export function getApiErrorMessage(error, fallback = '操作失败，请稍后重试。') {
  if (error instanceof ApiError) return error.message
  if (error?.isAxiosError || error?.response) return normalizeApiError(error).message
  return error instanceof Error && error.message ? error.message : fallback
}

export function installResponseHandling(client, { onUnauthorized } = {}) {
  client.interceptors.response.use(response => {
    const body = response.data
    if (body && typeof body.code === 'number' && body.code !== 200) {
      const error = new ApiError(body.message || body.error || statusMessages[body.code] || '操作失败，请稍后重试。', {
        response, status: body.code, code: body.code
      })
      if (error.status === 401) onUnauthorized?.(response.config, error)
      throw error
    }
    return response
  }, original => {
    const error = normalizeApiError(original)
    if (error.status === 401) onUnauthorized?.(original.config, error)
    return Promise.reject(error)
  })
  return client
}
