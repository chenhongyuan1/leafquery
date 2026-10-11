import test, { beforeEach } from 'node:test'
import assert from 'node:assert/strict'
import { AxiosError } from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import api from '../src/api.js'
import { useAdminStore } from '../src/stores/admin.js'

class Storage {
  values = new Map()
  getItem(key) { return this.values.get(key) ?? null }
  setItem(key, value) { this.values.set(key, String(value)) }
  removeItem(key) { this.values.delete(key) }
}

let requests
let redirects
beforeEach(() => {
  globalThis.localStorage = new Storage()
  globalThis.sessionStorage = new Storage()
  redirects = []
  globalThis.window = { location: { pathname: '/', assign: path => redirects.push(path) } }
  requests = []
  api.defaults.adapter = async config => {
    requests.push(config)
    return { config, status: 200, data: { code: 200 }, headers: {} }
  }
  setActivePinia(createPinia())
})

test('localStorage role and ID never become authorization headers', async () => {
  localStorage.setItem('admin', JSON.stringify({ role: 'super_admin', adminId: 999 }))
  await api.get('/system/admins')
  assert.equal(requests[0].headers.get('X-Admin-Role'), undefined)
  assert.equal(requests[0].headers.get('X-Admin-Id'), undefined)
  assert.equal(requests[0].headers.get('Authorization'), undefined)
  assert.equal(useAdminStore().isLoggedIn, false)
})

test('login stores the server token and requests use Bearer authorization', async () => {
  const store = useAdminStore()
  store.login({ role: 'admin', adminId: 7 }, 'server-issued-token')
  await api.get('/users')
  assert.equal(requests[0].headers.get('Authorization'), 'Bearer server-issued-token')
  assert.equal(sessionStorage.getItem('admin_token'), 'server-issued-token')
  assert.equal(localStorage.getItem('admin'), null)
  await api.post('/login', { username: 'test', password: 'test-only' })
  assert.equal(requests[1].headers.get('Authorization'), undefined)
})

test('logout revokes the server token before clearing local state', async () => {
  const store = useAdminStore()
  store.login({ role: 'admin', adminId: 7 }, 'server-issued-token')
  await store.logout()
  assert.equal(requests[0].url, '/logout')
  assert.equal(requests[0].headers.get('Authorization'), 'Bearer server-issued-token')
  assert.equal(sessionStorage.getItem('admin_token'), null)
  assert.equal(sessionStorage.getItem('admin'), null)
  assert.equal(store.isLoggedIn, false)
})

test('an expired server session clears state and redirects to login', async () => {
  useAdminStore().login({ role: 'admin' }, 'expired-token')
  api.defaults.adapter = async config => {
    throw new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, null, { status: 401 })
  }
  await assert.rejects(api.get('/users'))
  assert.equal(sessionStorage.getItem('admin_token'), null)
  assert.equal(sessionStorage.getItem('admin'), null)
  assert.deepEqual(redirects, ['/login'])
})

test('a login rejection does not redirect and a 403 does not clear the valid session', async () => {
  useAdminStore().login({ role: 'admin' }, 'valid-token')
  api.defaults.adapter = async config => {
    throw new AxiosError('Denied', 'ERR_BAD_REQUEST', config, null, {
      status: config.url === '/login' ? 401 : 403
    })
  }
  await assert.rejects(api.post('/login', {}))
  await assert.rejects(api.post('/system/admins', {}))
  assert.equal(sessionStorage.getItem('admin_token'), 'valid-token')
  assert.deepEqual(redirects, [])
})

test('logout still clears local state when the backend is unavailable', async () => {
  const store = useAdminStore()
  store.login({ role: 'admin' }, 'server-issued-token')
  api.defaults.adapter = async () => { throw new Error('Offline') }
  await assert.rejects(store.logout())
  assert.equal(sessionStorage.getItem('admin_token'), null)
  assert.equal(store.isLoggedIn, false)
})
