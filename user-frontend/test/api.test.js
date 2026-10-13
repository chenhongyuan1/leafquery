import test, { beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { AxiosError } from 'axios'
import http, { ApiError, getApiErrorMessage } from '../src/api/http.js'
import { farmApi, recordApi, favoriteApi, discoveryApi, trendApi, userApi, diagnosisApi } from '../src/api/index.js'
import { getAccountErrorMessage } from '../src/utils/accountSecurity.js'
import { getFriendlySyncError } from '../src/stores/farmUtils.js'

let requests
beforeEach(() => {
  requests = []
  http.defaults.adapter = async config => {
    requests.push(config)
    return { data: { code: 200, message: 'success', data: [] }, status: 200, headers: {}, config }
  }
})

test('business modules own routes and encode query values once', async () => {
  await farmApi.searchLocations('上海 & 浦东')
  await farmApi.remove(22, 7)
  await recordApi.list(7)
  await favoriteApi.toggle({ userId: 7, itemType: 'news', itemId: '3' })
  await discoveryApi.knowledge(5)
  await trendApi.targets('玉米')
  await userApi.login({ username: 'test', password: 'test-only' })
  assert.equal(requests[0].url, '/api/location/search')
  assert.deepEqual(requests[0].params, { query: '上海 & 浦东' })
  assert.equal(new URL(http.getUri(requests[0]), 'http://test.local').searchParams.get('query'), '上海 & 浦东')
  assert.equal(requests[1].method, 'delete')
  assert.equal(requests[1].url, '/api/farm/crops/22')
  assert.deepEqual(requests[1].params, { userId: 7 })
  assert.deepEqual(requests[4].params, { plantId: 5 })
  assert.deepEqual(requests[5].params, { crop: '玉米' })
  assert.equal(JSON.parse(requests[6].data).username, 'test')
})

test('HTTP 200 with a failed business code rejects instead of reporting success', async () => {
  http.defaults.adapter = async config => ({ status: 200, data: { code: 409, message: '档案已变更' }, config })
  await assert.rejects(farmApi.activate(22, { userId: 7 }), error =>
    error instanceof ApiError && error.status === 409 && error.message === '档案已变更' && error.response.status === 200)
})

test('data=false is a successful favorite removal, not a business error', async () => {
  http.defaults.adapter = async config => ({ status: 200, data: { code: 200, data: false }, config })
  assert.equal((await favoriteApi.toggle({ userId: 7 })).data.data, false)
})

test('transport errors retain the response and use a common friendly message', async () => {
  http.defaults.adapter = async config => {
    throw new AxiosError('transport detail', 'ERR_BAD_REQUEST', config, null, { status: 403, data: { message: '权限不足' } })
  }
  await assert.rejects(recordApi.list(7), error => error instanceof ApiError && getApiErrorMessage(error) === '权限不足')
})

test('network and timeout failures are distinguishable', async () => {
  for (const [code, expected] of [['ERR_NETWORK', '网络连接异常'], ['ECONNABORTED', '请求超时']]) {
    http.defaults.adapter = async config => { throw new AxiosError('internal detail', code, config) }
    await assert.rejects(userApi.login({}), error => error.message.includes(expected) && !error.message.includes('internal detail'))
  }
})

test('multipart, diagnosis timeouts and audio responses keep their domain contracts', async () => {
  const form = new FormData()
  form.append('categories', '玉米')
  const audio = new Blob(['test-audio'])
  http.defaults.adapter = async config => {
    requests.push(config)
    return { status: 200, config, data: config.responseType === 'blob' ? audio : { prediction: { scene_type: 'single' } } }
  }
  assert.equal((await diagnosisApi.identify(form)).data.prediction.scene_type, 'single')
  await diagnosisApi.diagnose({ categories: ['玉米'] })
  const response = await diagnosisApi.textToSpeech({ text: 'test' })
  assert.equal(requests[0].data, form)
  assert.equal(requests[0].timeout, 120000)
  assert.equal(requests[1].timeout, 300000)
  assert.equal(requests[2].responseType, 'blob')
  assert.equal(response.data, audio)
})

test('announcement options own popup and user parameters', async () => {
  await discoveryApi.announcements(7, true)
  await discoveryApi.announcements(null)
  assert.equal(requests[0].url, '/api/discovery/announcements/popup')
  assert.deepEqual(requests[0].params, { userId: 7 })
  assert.deepEqual(requests[1].params, {})
})

test('account and farm error helpers preserve the shared message and fallback', () => {
  const error = new ApiError('数据已发生变化', { code: 409, status: 409 })
  assert.equal(getAccountErrorMessage(error), '数据已发生变化')
  assert.equal(getFriendlySyncError(error, '同步失败'), '数据已发生变化')
  assert.equal(getAccountErrorMessage(null, '账户操作失败'), '账户操作失败')
  assert.equal(getFriendlySyncError(null, '同步失败'), '同步失败')
})
