import assert from 'node:assert/strict'
import { existsSync } from 'node:fs'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'
import { createServer } from 'vite'
import puppeteer from 'puppeteer-core'

const root = fileURLToPath(new URL('../', import.meta.url))
const evidence = resolve(root, '..', '.leafquery-runtime', 'page-navigation')
const runningBase = process.env.NAVIGATION_TEST_BASE_URL
const browserPath = [process.env.BROWSER_EXECUTABLE_PATH,
  `${process.env.PROGRAMFILES}/Google/Chrome/Application/chrome.exe`,
  `${process.env['PROGRAMFILES(X86)']}/Microsoft/Edge/Application/msedge.exe`,
  '/usr/bin/google-chrome', '/usr/bin/chromium'
].find(path => path && existsSync(path))
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))

test('离开数据中心后页面继续显示，动画及未完成请求不阻塞导航', { timeout: 90000 }, async t => {
  assert.ok(browserPath)
  const server = runningBase ? null : await createServer({ root, server: { port: 0, open: false }, logLevel: 'error' })
  let browser
  try {
    await mkdir(evidence, { recursive: true })
    await server?.listen()
    const base = runningBase || server.resolvedUrls.local[0]
    browser = await puppeteer.launch({ headless: true, executablePath: browserPath })
    for (const scenario of [
      { name: '工作台', selector: '.pc-workspace', key: 'workspace' },
      { name: '知识图谱', selector: '.pc-knowledge-page', key: 'knowledge' },
      { name: '系统设置', selector: '.pc-settings-page', key: 'settings' },
      { name: '工作台', selector: '.pc-workspace', key: 'pending-requests', pending: true }
    ]) {
      await t.test(scenario.key, async () => {
        const context = await browser.createBrowserContext()
        const page = await context.newPage()
        const errors = []
        const warnings = []
        try {
          await page.setViewport({ width: 1600, height: 1000 })
          await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'no-preference' }])
          page.on('pageerror', error => errors.push(error.message))
          page.on('console', message => {
            if (/non-element root|Unhandled error.*transition/i.test(message.text())) warnings.push(message.text().slice(0, 200))
          })
          await page.evaluateOnNewDocument(() => {
            localStorage.setItem('pc-theme', 'light')
            localStorage.setItem('farm_data_local', JSON.stringify({
              crops: [{ id: 1, name: '水稻', stageMode: 'MANUAL', stage: '分蘖期', locationId: '101010100', city: '北京', isActive: true }],
              activeCropId: 1,
              identificationHistory: [{ id: 1, cropId: 1, cropName: '水稻', pestName: '稻瘟病', confidence: .9, time: '2026-11-06' }]
            }))
          })
          await page.setRequestInterception(true)
          page.on('request', async request => {
            const url = new URL(request.url())
            if (!url.pathname.startsWith('/api/')) return request.continue()
            let body = { code: 200, data: [] }
            if (url.pathname === '/api/trend/targets') body = { diseaseTargets: ['稻瘟病'], pestTargets: ['稻飞虱'] }
            if (url.pathname === '/api/trend/forecast') {
              if (scenario.pending) await pause(900)
              body = { ...JSON.parse(request.postData()), supported: true, todayRiskScore: .5, dailySeries: [] }
            }
            if (url.pathname === '/api/weather/features') body = { temp_mean_3d: 24, humidity_mean_3d: 80, rain_sum_7d: 20 }
            await request.respond({ status: 200, contentType: 'application/json', body: JSON.stringify(body) }).catch(() => {})
          })
          await page.goto(base, { waitUntil: 'networkidle0' })
          await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.trim() === '进入工作台'))
          await page.evaluate(() => [...document.querySelectorAll('button')].find(b => b.textContent.trim() === '进入工作台').click())
          await page.waitForSelector('.pc-workspace', { visible: true })
          async function select(name) { await page.click(`.pc-sidebar-item[aria-label="${name}"]`) }
          await select('数据中心')
          await page.waitForSelector('.pc-data-page', { visible: true })
          if (!scenario.pending) await page.waitForFunction(() => document.querySelectorAll('.pc-data-page canvas').length >= 4)
          await select(scenario.name)
          const visible = await page.waitForSelector(scenario.selector, { visible: true, timeout: 3000 }).then(() => true, () => false)
          if (!visible) await page.screenshot({ path: resolve(evidence, `${scenario.key}-blank.png`) })
          assert.ok(visible, JSON.stringify({ target: scenario.name, symptom: '切换后目标页面未显示', warnings, errors }))
          assert.equal(await page.$eval(`.pc-sidebar-item[aria-label="${scenario.name}"]`, el => el.getAttribute('aria-current')), 'page')
          await pause(1100)
          assert.deepEqual(errors, [])
          assert.deepEqual(warnings, [])
          // A second round catches a transition left permanently waiting for afterLeave.
          await select('数据中心')
          await page.waitForSelector('.pc-data-page', { visible: true })
          await select(scenario.name)
          await page.waitForSelector(scenario.selector, { visible: true, timeout: 3000 })
          await page.screenshot({ path: resolve(evidence, `${scenario.key}-visible.png`) })
        } finally { await context.close() }
      })
    }
  } finally { await browser?.close(); await server?.close() }
})
