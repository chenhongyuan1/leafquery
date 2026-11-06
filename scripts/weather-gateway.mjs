import http from 'node:http';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const weatherPath = '/api/weather/features';

// 固定上游地址；公网请求不能指定其他业务接口或主机。
export function createWeatherGateway({
  backendOrigin = 'http://127.0.0.1:8080',
  timeoutMs = 60_000,
  maxConcurrent = 2,
} = {}) {
  let active = 0;
  const reply = (response, status, error) => {
    response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
    response.end(JSON.stringify({ error }));
  };
  return http.createServer(async (request, response) => {
    response.setHeader('Cache-Control', 'no-store');
    let url;
    try { url = new URL(request.url, 'http://localhost'); }
    catch { return reply(response, 400, 'Invalid request URL'); }
    if (url.pathname !== weatherPath) return reply(response, 404, 'Not found');
    if (request.method !== 'GET') {
      response.setHeader('Allow', 'GET');
      return reply(response, 405, 'Only GET is allowed');
    }
    const entries = [...url.searchParams];
    if (entries.length !== 1 || entries[0][0] !== 'locationId' || !/^[A-Za-z0-9]{1,32}$/.test(entries[0][1])) {
      return reply(response, 400, 'Exactly one valid locationId is required');
    }
    if (active >= maxConcurrent) return reply(response, 429, 'Too many weather requests');
    active++;
    const controller = new AbortController();
    let timedOut = false;
    const timer = setTimeout(() => { timedOut = true; controller.abort(); }, timeoutMs);
    const disconnect = () => { if (!response.writableEnded) controller.abort(); };
    response.on('close', disconnect);
    try {
      const upstream = new URL(weatherPath, backendOrigin);
      upstream.searchParams.set('locationId', entries[0][1]);
      // 仅转发天气查询，不传递调用方 Cookie、Authorization 或任意重定向。
      const result = await fetch(upstream, { headers: { Accept: 'application/json' }, signal: controller.signal, redirect: 'error' });
      if (!result.headers.get('content-type')?.includes('application/json')) {
        await result.body?.cancel();
        return reply(response, 502, 'Weather backend returned a non-JSON response');
      }
      const body = await result.text();
      response.writeHead(result.status, { 'Content-Type': 'application/json; charset=utf-8' });
      response.end(body);
    } catch {
      if (!response.destroyed) reply(response, timedOut ? 504 : 502, timedOut ? 'Weather backend timed out' : 'Weather backend unavailable');
    } finally {
      clearTimeout(timer);
      response.off('close', disconnect);
      active--;
    }
  });
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const server = createWeatherGateway();
  server.requestTimeout = 10_000;
  server.headersTimeout = 10_000;
  server.on('error', (error) => { console.error(`Weather gateway failed: ${error.code || 'UNKNOWN'}`); process.exitCode = 1; });
  server.listen(18787, '127.0.0.1', () => console.log('Weather gateway ready on 127.0.0.1:18787 (no custom auth header required).'));
  for (const signal of ['SIGINT', 'SIGTERM']) process.on(signal, () => server.close(() => process.exit(0)));
}
