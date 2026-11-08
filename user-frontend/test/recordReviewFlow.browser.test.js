import assert from 'node:assert/strict'
import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import test from 'node:test'
import { createServer } from 'vite'
import puppeteer from 'puppeteer-core'

const root = fileURLToPath(new URL('../', import.meta.url))
const browserPath = [process.env.BROWSER_EXECUTABLE_PATH,
  `${process.env.PROGRAMFILES}/Google/Chrome/Application/chrome.exe`,
  `${process.env['PROGRAMFILES(X86)']}/Microsoft/Edge/Application/msedge.exe`,
  '/usr/bin/google-chrome', '/usr/bin/chromium'
].find(path => path && existsSync(path))
const image = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jE1QAAAAASUVORK5CYII='
const review = '【确诊结果】：稻瘟病\n【建议】：保留现场照片并观察叶片。'

test('新识别的复核结果和置信度在保存与刷新后仍能查看', { timeout: 90000 }, async t => {
  assert.ok(browserPath)
  const server = await createServer({ root, server: { port: 0, open: false }, logLevel: 'error' })
  let browser
  try {
    await server.listen()
    browser = await puppeteer.launch({ headless: true, executablePath: browserPath })
    for (const loggedIn of [false, true]) {
      await t.test(loggedIn ? '登录用户通过保存请求和列表读回复核快照' : '未登录用户在本地记录中保留复核快照', async () => {
        const context = await browser.createBrowserContext()
        const page = await context.newPage()
        const saved = []
        const calls = []
        const errors = []
        page.on('pageerror', error => errors.push(error.message))
        try {
          await page.setViewport({ width: 390, height: 844 })
          await page.evaluateOnNewDocument(loggedIn => {
            localStorage.setItem('app-theme', 'light')
            if (loggedIn) localStorage.setItem('user', JSON.stringify({ userId: 7, username: 'review-test' }))
          }, loggedIn)
          await page.setRequestInterception(true)
          page.on('request', request => {
            const url = new URL(request.url())
            if (url.pathname === '/review-image.png') return request.respond({ status: 200, contentType: 'image/png', body: Buffer.from(image, 'base64') })
            if (!url.pathname.startsWith('/api/')) return request.continue()
            let body = { code: 200, data: [] }
            if (/^\/api\/(pest|ai)\//.test(url.pathname)) calls.push(url.pathname)
            if (url.pathname === '/api/pest/identify') body = {
              prediction: { pest_name: '稻瘟病', confidence: .74, scene_type: 'uncertain', primary_target_zh: '稻瘟病', primary_confidence: .74, detected_summary: [] },
              image_url: '/review-image.png', yolo_used: true, review_required: true,
              review_result: review, report_error: null
            }
            if (url.pathname === '/api/record/add') {
              const record = { ...JSON.parse(request.postData()), id: 501, createTime: '2026-11-06T10:00:00+08:00' }
              saved.push(record); body.data = record
            }
            if (url.pathname === '/api/record/list') body.data = saved
            return request.respond({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
          })
          await page.goto(server.resolvedUrls.local[0], { waitUntil: 'networkidle0' })
          await page.evaluate(() => [...document.querySelectorAll('button')].find(b => b.textContent.includes('水稻')).click())
          await page.$eval('input[type="file"][accept="image/*"]', (input, image) => {
            const files = new DataTransfer()
            files.items.add(new File([Uint8Array.from(atob(image), c => c.charCodeAt(0))], 'review.png', { type: 'image/png' }))
            input.files = files.files; input.dispatchEvent(new Event('change', { bubbles: true }))
          }, image)
          const key = loggedIn ? 'farm_data_cloud' : 'farm_data_local'
          await page.waitForFunction((key, review) => JSON.parse(localStorage.getItem(key) || '{}').identificationHistory?.some(row => row.reviewResult === review), {}, key, review)
          const snapshot = await page.evaluate(key => JSON.parse(localStorage.getItem(key)).identificationHistory[0], key)
          assert.equal(snapshot.confidence, .74)
          assert.equal(snapshot.yoloUsed, true)
          assert.equal(snapshot.reviewRequired, true)
          assert.equal(snapshot.reviewResult, review)
          assert.equal(saved.length, loggedIn ? 1 : 0)
          if (loggedIn) assert.equal(saved[0].reviewResult, review)
          for (const load of ['navigate', 'reload']) {
            if (load === 'navigate') await page.goto(new URL('/records', server.resolvedUrls.local[0]).href, { waitUntil: 'networkidle0' })
            else await page.reload({ waitUntil: 'networkidle0' })
            await page.waitForSelector('[data-testid="record-detail-trigger"]')
            await page.click('[data-testid="record-detail-trigger"]')
            await page.waitForSelector('[role="dialog"]')
            const text = await page.$eval('[role="dialog"]', el => el.textContent)
            assert.ok(text.includes('74.0%'))
            assert.ok(text.includes('YOLO 置信度'))
            assert.ok(text.includes('保留现场照片并观察叶片。'))
          }
          assert.deepEqual(calls, ['/api/pest/identify'], '重看记录不得重新请求模型')
          assert.deepEqual(errors, [])
        } finally { await context.close() }
      })
    }
  } finally { await browser?.close(); await server.close() }
})
