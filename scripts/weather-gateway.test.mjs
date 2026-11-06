import { test } from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { createWeatherGateway } from './weather-gateway.mjs';

const endpoint = '/api/weather/features?locationId=101010100';

async function listen(server, t) {
  await new Promise((done) => server.listen(0, '127.0.0.1', done));
  t.after(() => new Promise((done) => { server.close(done); server.closeAllConnections(); }));
  return `http://127.0.0.1:${server.address().port}`;
}

async function setup(t, handler, options = {}) {
  const requests = [];
  const backend = await listen(http.createServer((req, res) => {
    requests.push({ url: req.url, headers: req.headers });
    if (handler) return handler(req, res);
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ temp_today: 19, history_days: [] }));
  }), t);
  const gateway = await listen(createWeatherGateway({ backendOrigin: backend, ...options }), t);
  const get = (path = endpoint, extra = {}) => fetch(gateway + path, extra);
  return { get, gateway, requests };
}

test('天气请求返回真实上游 JSON，并丢弃外部凭据', async (t) => {
  const { get, requests } = await setup(t);
  const result = await get(endpoint, { headers: { 'X-LeafQuery-Weather-Key': 'obsolete-test-key', Authorization: 'Bearer ignored', Cookie: 'ignored=value' } });
  assert.equal(result.status, 200);
  assert.equal((await result.json()).temp_today, 19);
  assert.equal(result.headers.get('cache-control'), 'no-store');
  assert.equal(requests[0].url, endpoint);
  for (const header of ['x-leafquery-weather-key', 'authorization', 'cookie']) assert.equal(requests[0].headers[header], undefined);
});

test('无需请求头和本地密钥即可查询天气', async (t) => {
  const { gateway, requests } = await setup(t);
  const result = await fetch(gateway + endpoint);
  assert.equal(result.status, 200);
  assert.equal((await result.json()).temp_today, 19);
  assert.equal(requests.length, 1);
});

test('其他 API、变体路径与非 GET 请求不能进入上游', async (t) => {
  const { get, requests } = await setup(t);
  for (const path of ['/api/admin/users', '/api/user/1', '/api/weather/features/', '/api/weather/%66eatures', '/']) {
    assert.equal((await get(path)).status, 404);
  }
  for (const method of ['POST', 'PUT', 'DELETE', 'HEAD']) assert.equal((await get(endpoint, { method })).status, 405);
  assert.equal(requests.length, 0);
});

test('拒绝缺失、重复、多余及可注入的参数', async (t) => {
  const { get, requests } = await setup(t);
  for (const query of ['', '?locationId=', '?locationId=101010100&locationId=101020100', '?locationId=101010100&url=http://other', '?locationId=101010100%26date%3D20260101']) {
    assert.equal((await get('/api/weather/features' + query)).status, 400);
  }
  assert.equal(requests.length, 0);
});

test('上游不可连接时返回 502', async (t) => {
  const closed = http.createServer();
  await new Promise((done) => closed.listen(0, '127.0.0.1', done));
  const origin = `http://127.0.0.1:${closed.address().port}`;
  await new Promise((done) => closed.close(done));
  const gateway = await listen(createWeatherGateway({ backendOrigin: origin }), t);
  assert.equal((await fetch(gateway + endpoint)).status, 502);
});

test('上游迟迟不响应时返回 504', async (t) => {
  const { get } = await setup(t, () => {}, { timeoutMs: 60 });
  const result = await get();
  assert.equal(result.status, 504);
  assert.match((await result.json()).error, /timed out/);
});

test('上游响应体停滞也受总等待时间限制', async (t) => {
  const { get } = await setup(t, (_req, res) => { res.writeHead(200, { 'Content-Type': 'application/json' }); res.write('{'); }, { timeoutMs: 60 });
  assert.equal((await get()).status, 504);
});

test('拒绝 HTML 和重定向，保留上游 JSON 错误状态', async (t) => {
  for (const mode of ['html', 'redirect', 'json-error']) {
    const { get } = await setup(t, (_req, res) => {
      if (mode === 'redirect') { res.writeHead(302, { Location: '/api/admin/users' }); return res.end(); }
      res.writeHead(mode === 'json-error' ? 503 : 200, { 'Content-Type': mode === 'html' ? 'text/html' : 'application/json' });
      res.end(mode === 'html' ? '<html>error</html>' : '{"error":"unavailable"}');
    });
    assert.equal((await get()).status, mode === 'json-error' ? 503 : 502);
  }
});

test('并发限制防止天气查询堆积', async (t) => {
  let entered;
  const started = new Promise((done) => { entered = done; });
  const { get } = await setup(t, () => entered(), { maxConcurrent: 1, timeoutMs: 200 });
  const first = get();
  await started;
  assert.equal((await get()).status, 429);
  assert.equal((await first).status, 504);
});
