import assert from 'node:assert/strict'
import { existsSync } from 'node:fs'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import puppeteer from 'puppeteer-core'

const root = fileURLToPath(new URL('../', import.meta.url))
const browserPath = [process.env.BROWSER_EXECUTABLE_PATH,
  `${process.env.PROGRAMFILES}/Google/Chrome/Application/chrome.exe`,
  `${process.env['PROGRAMFILES(X86)']}/Microsoft/Edge/Application/msedge.exe`,
  '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
].find(path => path && existsSync(path))
const imageBytes = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jE1QAAAAASUVORK5CYII='
const review = 'DIFY_REVIEW_ONLY：请补充近照核验。'
const failure = 'Dify 复核失败：Dify 模型端点已关闭或暂不可用，请在 Dify 中恢复端点或更换可用模型。'

// PC 工作台已改为仅前端输入预览，其交互由 promptInput.browser.test.js 验证。
test('移动端识别复核独立于问答模型，失败原因和报告锁定正确显示', { timeout: 120000 }, async t => {
  assert.ok(browserPath, '需要 Chrome/Edge，或设置 BROWSER_EXECUTABLE_PATH')
  const server = await createServer({ root, server: { port: 0, open: false }, logLevel: 'error' })
  let browser
  try {
    await server.listen()
    browser = await puppeteer.launch({ headless: true, executablePath: browserPath })
    for (const device of ['mobile']) {
      for (const reviewState of ['failed', 'valid', 'meaningless']) {
        const reviewSucceeded = reviewState !== 'failed'
        const meaningless = reviewState === 'meaningless'
        const conclusion = meaningless ? '【确诊结果】：无意义\n【说明】：当前图片不是作物或病虫害。' : review
        const label = meaningless ? '无意义确诊隐藏并锁定诊断报告' : (reviewSucceeded ? '正常复核可生成报告且独立于问答' : 'Dify 失败保留初判并显示具体原因')
        await t.test(`${device}: ${label}`, async () => {
          const page = await browser.newPage()
          try {
            await page.setViewport({ width: device === 'pc' ? 1600 : 390, height: device === 'pc' ? 1000 : 844 })
            const calls = []
            const errors = []
            page.on('pageerror', error => errors.push(error.message))
            await page.setRequestInterception(true)
            page.on('request', request => {
              const url = new URL(request.url())
              if (!url.pathname.startsWith('/api/')) return request.continue()
              let body = { code: 200, data: [] }
              let status = 200
              if (['/api/pest/identify', '/api/pest/diagnose', '/api/ai/chat'].includes(url.pathname)) calls.push(url.pathname)
              if (url.pathname === '/api/pest/identify') body = {
                prediction: { pest_name: '稻瘟病', confidence: 0.4, scene_type: 'uncertain', primary_target_zh: '稻瘟病', primary_confidence: 0.4, class_names_zh: ['稻瘟病'], detected_summary: [] },
                image_url: '/test-image.png', yolo_used: true, review_required: true,
                review_result: reviewSucceeded ? conclusion : null, report: '', report_error: reviewSucceeded ? null : failure
              }
              if (url.pathname === '/api/pest/diagnose') body = { report: 'DIFY_REPORT_ONLY：有效诊断报告' }
              if (url.pathname === '/api/ai/chat') { status = 503; body = { error: '独立问答服务暂不可用' } }
              return request.respond({ status, contentType: 'application/json', body: JSON.stringify(body) })
            })
            await page.goto(server.resolvedUrls.local[0], { waitUntil: 'networkidle0' })
            if (device === 'pc') {
              await page.evaluate(() => [...document.querySelectorAll('button')].find(b => b.textContent.trim() === '进入工作台').click())
              await page.waitForFunction(() => document.body.textContent.includes('选择检测范围'))
            }
            await page.evaluate(() => [...document.querySelectorAll('button')].find(b => b.textContent.includes('水稻')).click())
            // Use the actual file-input handler, with an in-memory image fixture.
            await page.$eval('input[type="file"][accept="image/*"]', (input, base64) => {
              const bytes = Uint8Array.from(atob(base64), char => char.charCodeAt(0))
              const files = new DataTransfer()
              files.items.add(new File([bytes], 'test.png', { type: 'image/png' }))
              input.files = files.files
              input.dispatchEvent(new Event('change', { bubbles: true }))
            }, imageBytes)
            const expected = reviewSucceeded ? conclusion : '模型端点已关闭'
            await page.waitForFunction(text => document.body.textContent.includes(text), {}, expected)
            assert.deepEqual(calls, ['/api/pest/identify'], '自动复核不得请求报告或问答')
            if (!meaningless) assert.ok(await page.evaluate(() => document.body.textContent.includes('稻瘟病')), '仍可查看 YOLO 初判')
            const reportButtons = await page.$$eval('button', buttons => buttons.filter(button => button.textContent.includes('生成 AI 诊断')).length)
            if (meaningless) {
              assert.equal(reportButtons, 0, '确诊无意义时不能呈现报告生成入口')
              assert.ok(await page.evaluate(() => document.body.textContent.includes('不能生成诊断报告')))
              assert.deepEqual(calls, ['/api/pest/identify'])
            } else if (reviewSucceeded) {
              assert.equal(reportButtons, 1, '正常结论保留报告入口')
              await page.evaluate(() => [...document.querySelectorAll('button')].find(button => button.textContent.includes('生成 AI 诊断')).click())
              await page.waitForFunction(() => document.body.textContent.includes('DIFY_REPORT_ONLY'))
              assert.deepEqual(calls, ['/api/pest/identify', '/api/pest/diagnose'])
            }
            if (device === 'pc' && reviewState === 'valid') {
              await page.$eval('input[placeholder="围绕本次识别结果继续追问..."]', input => {
                input.value = '请解释结果'
                input.dispatchEvent(new Event('input', { bubbles: true }))
                input.form.requestSubmit()
              })
              await page.waitForFunction(() => document.body.textContent.includes('发送失败'))
              assert.deepEqual(calls, ['/api/pest/identify', '/api/pest/diagnose', '/api/ai/chat'])
              assert.ok(await page.evaluate(text => document.body.textContent.includes(text), review), '追问失败不能抹掉 Dify 复核结论')
            }
            assert.deepEqual(errors, [])
          } finally { await page.close() }
        })
      }
    }
  } finally {
    await browser?.close()
    await server.close()
  }
})
