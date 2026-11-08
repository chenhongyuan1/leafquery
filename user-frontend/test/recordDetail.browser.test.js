import assert from 'node:assert/strict'
import { existsSync } from 'node:fs'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'
import { createServer } from 'vite'
import puppeteer from 'puppeteer-core'

const root = fileURLToPath(new URL('../', import.meta.url))
const evidence = resolve(root, '..', '.leafquery-runtime', 'record-detail')
const browserPath = [process.env.BROWSER_EXECUTABLE_PATH,
  `${process.env.PROGRAMFILES}/Google/Chrome/Application/chrome.exe`,
  `${process.env['PROGRAMFILES(X86)']}/Microsoft/Edge/Application/msedge.exe`,
  '/usr/bin/google-chrome', '/usr/bin/chromium'
].find(path => path && existsSync(path))
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))
const rows = [
  { id: 101, pestName: '水稻稻瘟病', confidence: .9234, yoloUsed: true, reviewRequired: true, reviewResult: '【确诊结果】：水稻稻瘟病\n【建议】：先观察叶片。', createTime: '2026-11-06T08:12:33+08:00', imageUrl: '/record-fixture.svg', cropId: 1, cropName: '水稻', city: '杭州', region: '长江中下游' },
  { id: 102, pestName: '健康叶片', confidence: 0, createTime: 'invalid', imageUrl: '' },
  { id: 103, pestName: '图片失效记录', confidence: 0, yoloUsed: false, reviewRequired: true, reviewError: '复核服务暂不可用', createTime: '2026-11-05T08:00:00+08:00', imageUrl: '/missing-record.jpg' },
  { id: 104, pestName: '无需复核记录', confidence: .98, yoloUsed: true, reviewRequired: false }
]
const trigger = '[data-testid="record-detail-trigger"]'
const dialog = '[role="dialog"][aria-labelledby="record-detail-title"]'

test('识别历史详情：底部滑入、正确数据、关闭恢复与桌面居中', { timeout: 120000 }, async t => {
  assert.ok(browserPath, '需要 Chrome/Edge 或 BROWSER_EXECUTABLE_PATH')
  const server = await createServer({ root, server: { port: 0, open: false }, logLevel: 'error' })
  let browser
  try {
    await mkdir(evidence, { recursive: true })
    await server.listen()
    browser = await puppeteer.launch({ headless: true, executablePath: browserPath })
    for (const scenario of [
      { label: 'mobile-list', width: 390, height: 844, path: '/records', loggedIn: true },
      { label: 'mobile-home', width: 390, height: 844, path: '/', loggedIn: false },
      { label: 'tablet-dark', width: 820, height: 1180, path: '/records', loggedIn: false, dark: true },
      { label: 'pc-data', width: 1600, height: 1000, path: '/', loggedIn: false, pc: true }
    ]) {
      await t.test(scenario.label, async () => {
        const page = await browser.newPage()
        const errors = []
        const modelCalls = []
        const deletes = []
        let records = structuredClone(rows)
        page.on('pageerror', error => errors.push(error.message))
        try {
          await page.setViewport({ width: scenario.width, height: scenario.height })
          await page.emulateTimezone('Asia/Taipei')
          await page.evaluateOnNewDocument((rows, scenario) => {
            localStorage.setItem('leafquery-theme', scenario.dark ? 'dark' : 'light')
            localStorage.setItem('app-theme', scenario.dark ? 'dark' : 'light')
            localStorage.setItem('pc-theme', scenario.dark ? 'dark' : 'light')
            if (scenario.loggedIn) localStorage.setItem('user', JSON.stringify({ userId: 7, username: 'detail-test' }))
            else localStorage.removeItem('user')
            localStorage.setItem('farm_data_local', JSON.stringify({
              crops: [{ id: 1, name: '水稻', stageMode: 'MANUAL', stage: '分蘖期', locationId: '101210101', city: '杭州', isActive: true }],
              activeCropId: 1, identificationHistory: rows
            }))
            localStorage.setItem('leafquery_history', JSON.stringify(rows.map(row => ({ ...row, name: row.pestName, time: row.createTime }))))
          }, rows, scenario)
          await page.setRequestInterception(true)
          page.on('request', request => {
            const url = new URL(request.url())
            if (url.pathname === '/record-fixture.svg') return request.respond({ status: 200, contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="640" height="480"><rect width="640" height="480" fill="#dcfce7"/><ellipse cx="320" cy="240" rx="90" ry="160" fill="#16a34a"/></svg>' })
            if (url.pathname === '/missing-record.jpg') return request.respond({ status: 404, body: '' })
            if (!url.pathname.startsWith('/api/')) return request.continue()
            if (/^\/api\/(pest|ai)\//.test(url.pathname)) modelCalls.push(url.pathname)
            let body = { code: 200, data: [] }
            if (url.pathname === '/api/record/list') body.data = records
            if (url.pathname === '/api/farm/crops') body.data = []
            if (request.method() === 'DELETE' && url.pathname.startsWith('/api/record/')) {
              const id = Number(url.pathname.split('/').at(-1)); deletes.push(id)
              records = records.filter(row => row.id !== id)
            }
            if (url.pathname === '/api/trend/targets') body = { diseaseTargets: ['稻瘟病'], pestTargets: ['稻飞虱'] }
            if (url.pathname === '/api/trend/forecast') body = { ...JSON.parse(request.postData()), supported: true, todayRiskScore: .5, dailySeries: [] }
            if (url.pathname === '/api/weather/features') body = { temp_mean_3d: 24, humidity_mean_3d: 80, rain_sum_7d: 20 }
            return request.respond({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
          })
          await page.goto(new URL(scenario.path, server.resolvedUrls.local[0]).href, { waitUntil: 'networkidle0' })
          if (scenario.dark) await page.evaluate(() => document.documentElement.classList.add('dark'))
          if (scenario.pc) {
            await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.trim() === '进入工作台'))
            await page.evaluate(() => [...document.querySelectorAll('button')].find(b => b.textContent.trim() === '进入工作台').click())
            await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.includes('数据中心')))
            await page.evaluate(() => [...document.querySelectorAll('button')].find(b => b.textContent.includes('数据中心')).click())
          }
          await page.waitForSelector(trigger)
          await page.click(`${trigger}[aria-label="查看水稻稻瘟病的识别记录"]`)
          await page.waitForSelector(dialog)
          await pause(450)
          const state = await page.$eval(dialog, el => {
            const rect = el.getBoundingClientRect()
            return { text: el.textContent, x: rect.x, y: rect.y, width: rect.width, bottom: rect.bottom, radius: getComputedStyle(el).borderTopLeftRadius, overflow: el.scrollWidth > el.clientWidth + 1 }
          })
          for (const text of ['水稻稻瘟病', 'YOLO 置信度', '92.3%', 'AI 视觉复核结果', '先观察叶片。', '2026-11-06 08:12:33', '杭州', '长江中下游']) assert.ok(state.text.includes(text), `${scenario.label}: 缺少 ${text}`)
          assert.equal(state.overflow, false)
          if (!scenario.pc) {
            assert.ok(Math.abs(state.bottom - scenario.height) < 2, '移动详情应贴合屏幕底部')
            assert.ok(state.y > 0 && state.y < scenario.height / 2)
            assert.ok(state.width >= scenario.width - 2)
          } else {
            assert.ok(state.width <= 680)
            assert.ok(Math.abs(state.x + state.width / 2 - scenario.width / 2) < 2)
          }
          assert.equal(await page.evaluate(() => document.getElementById('app').inert), true)
          await page.keyboard.press('Tab')
          assert.equal(await page.evaluate(() => document.activeElement.getAttribute('aria-label')), '关闭记录详情')
          await page.screenshot({ path: resolve(evidence, `${scenario.label}.png`) })
          const scrollBeforeClose = await page.$eval(trigger, el => ({ root: window.scrollY, container: el.closest('.records-page')?.scrollTop }))
          await page.keyboard.press('Escape')
          await page.waitForSelector(dialog, { hidden: true })
          assert.equal(await page.evaluate(() => document.getElementById('app').inert), false)
          assert.equal(await page.evaluate(() => document.activeElement.getAttribute('aria-label')), '查看水稻稻瘟病的识别记录')
          assert.deepEqual(await page.$eval(trigger, el => ({ root: window.scrollY, container: el.closest('.records-page')?.scrollTop })), scrollBeforeClose)

          if (!scenario.pc) {
            await page.focus(`${trigger}[aria-label="查看健康叶片的识别记录"]`)
            await page.keyboard.press('Enter')
            await page.waitForSelector(dialog)
            assert.ok((await page.$eval(dialog, el => el.textContent)).includes('暂无识别图片'))
            assert.ok((await page.$eval(dialog, el => el.textContent)).includes('未记录'))
            assert.ok((await page.$eval(dialog, el => el.textContent)).includes('这条历史记录未保存 AI 复核结果。'))
            await pause(450)
            await page.mouse.click(4, 4)
            await page.waitForSelector(dialog, { hidden: true })
            await page.click(`${trigger}[aria-label="查看图片失效记录的识别记录"]`)
            await page.waitForFunction(() => document.querySelector('[role="dialog"]')?.textContent.includes('原图暂不可用'))
            assert.ok((await page.$eval(dialog, el => el.textContent)).includes('复核服务暂不可用'))
            assert.ok((await page.$eval(dialog, el => el.textContent)).includes('未使用'))
            await pause(450)
            await page.click('button[aria-label="关闭记录详情"]')
            await page.waitForSelector(dialog, { hidden: true })
            await page.click(`${trigger}[aria-label="查看无需复核记录的识别记录"]`)
            await page.waitForSelector(dialog)
            assert.ok((await page.$eval(dialog, el => el.textContent)).includes('本次识别无需 AI 视觉复核。'))
            await page.keyboard.press('Escape')
            await page.waitForSelector(dialog, { hidden: true })
          }
          if (scenario.loggedIn) {
            await page.$eval(`${trigger}[aria-label="查看水稻稻瘟病的识别记录"]`, el => el.parentElement.querySelector('button[title="删除此条记录"]').click())
            await page.waitForFunction(() => !document.querySelector('[aria-label="查看水稻稻瘟病的识别记录"]'))
            assert.deepEqual(deletes, [101])
            assert.equal(await page.$(dialog), null, '删除按钮不得打开详情')
          }
          assert.deepEqual(modelCalls, [], '查看历史不得重新请求识别、报告或问答')
          assert.deepEqual(errors, [])
        } finally { await page.close() }
      })
    }
  } finally { await browser?.close(); await server.close() }
})
