import assert from 'node:assert/strict'
import test from 'node:test'
import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import puppeteer from 'puppeteer-core'

const root = fileURLToPath(new URL('../', import.meta.url))
const executablePath = [process.env.BROWSER_EXECUTABLE_PATH, `${process.env.PROGRAMFILES}/Google/Chrome/Application/chrome.exe`, `${process.env['PROGRAMFILES(X86)']}/Microsoft/Edge/Application/msedge.exe`].find(path => path && existsSync(path))
const image = fileURLToPath(new URL('../public/images/library/rice_blast.png', import.meta.url))

test('PC 工作台后端契约、异常重试与历史隔离', { timeout: 120000 }, async t => {
  const server = await createServer({ root, server: { port: 0, open: false }, logLevel: 'error' })
  let browser
  try {
    await server.listen()
    browser = await puppeteer.launch({ headless: true, executablePath })
    const page = await browser.newPage()
    await page.setViewport({ width: 1600, height: 1000 })
    await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }])
    await page.evaluateOnNewDocument(() => {
      if (!localStorage.getItem('user')) localStorage.setItem('user', JSON.stringify({ userId: 42, username: 'fixture' }))
      localStorage.setItem('pc-theme', 'light')
      window.micTracksStopped = 0
      navigator.mediaDevices.getUserMedia = async () => ({ getTracks: () => [{ stop() { window.micTracksStopped++ } }] })
      window.MediaRecorder = class {
        state = 'inactive'; mimeType = 'audio/webm'
        start() { this.state = 'recording' }
        stop() { this.state = 'inactive'; this.ondataavailable?.({ data: new Blob(['voice'], { type: this.mimeType }) }); this.onstop?.() }
      }
    })
    let failure = '', meaningless = false, reviewFailure = false
    let rows = [{ id: 100, pestName: '历史水稻病害', confidence: .7, cropName: '水稻', imageUrl: '/vite.svg', createTime: '2026-11-08T10:00:00', yoloUsed: true, reviewRequired: true, reviewResult: '【确诊结果】：历史水稻病害' }]
    const calls = [], errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.setRequestInterception(true)
    page.on('request', async request => {
      const url = new URL(request.url()), path = url.pathname
      if (!path.startsWith('/api/')) return request.continue()
      let payload
      const body = request.postData() || (request.hasPostData() ? await request.fetchPostData() : '') || ''
      try { payload = JSON.parse(body || '{}') } catch { payload = body }
      calls.push({ path, payload })
      let data = { code: 200, data: [] }, status = 200, delay = 0
      if (path === '/api/farm/crops') data.data = [{ id: 9, cropId: 9, cropName: '水稻', isActive: true, locationId: '101020100', city: '上海', region: '市区' }]
      if (path === '/api/record/list') data.data = url.searchParams.get('userId') === '42' ? rows : []
      if (path === '/api/record/add') {
        const row = { ...payload, id: 101 + rows.length, createTime: '2026-11-08T10:30:00' }
        rows = [row, ...rows]; data.data = row
      }
      if (path === '/api/pest/identify') {
        delay = 350
        data = { prediction: meaningless ? null : { pest_name: '水稻褐斑病', confidence: .91, scene_type: 'single' }, image_url: '/vite.svg?new=1', yolo_used: !meaningless, review_required: true, review_result: meaningless ? '【确诊结果】：无意义' : reviewFailure ? '' : '【确诊结果】：水稻褐斑病', report_error: reviewFailure ? 'Dify 暂不可用' : null }
        if (failure === 'identify') { failure = ''; status = 503; data = { error: '推理服务不可用' } }
      }
      if (path === '/api/pest/diagnose') {
        delay = 350; data = { report: '诊断报告：水稻褐斑病。\n建议补充近景并检查田间湿度。' }
        if (failure === 'report') { failure = ''; status = 500; data = { error: '报告生成失败' } }
      }
      if (path === '/api/ai/chat') {
        delay = 350; data = { reply: '请结合本次识别和报告检查叶片。' }
        if (failure === 'chat') { failure = ''; data = { reply: 'AI 对话服务调用失败: 接入点关闭' } }
      }
      if (path === '/api/ai/speech-to-text') data = { text: '请问水稻如何防治' }
      setTimeout(() => request.respond({ status, contentType: 'application/json', body: JSON.stringify(data) }).catch(() => {}), delay)
    })
    await page.goto(server.resolvedUrls.local[0], { waitUntil: 'networkidle0' })
    await page.evaluate(() => [...document.querySelectorAll('button')].find(button => button.textContent.trim() === '进入工作台').click())
    const enabled = () => page.waitForFunction(() => !document.querySelector('.prompt-action').disabled)
    const fill = value => page.$eval('.prompt-textarea', (el, value) => { el.value = value; el.dispatchEvent(new Event('input', { bubbles: true })) }, value)
    const identify = async () => {
      await page.click('[aria-label="新建识别"]')
      if (await page.$('.prompt-collapsed')) await page.click('.prompt-collapsed')
      await enabled()
      await page.click('.prompt-choose-categories')
      await page.click('[role="menuitemcheckbox"]')
      await page.click('.prompt-category-footer button')
      const input = await page.$('.prompt-file-input'); await input.uploadFile(image)
      await page.click('.prompt-action')
    }
    await t.test('读取真实历史并显示对应缩略图，保留原模型名称', async () => {
      await page.waitForSelector('#workspace-records-recognition .records-thumbnail img')
      await page.click('#workspace-records-recognition .records-list button')
      await page.waitForFunction(() => document.querySelector('.turn-detection-card h2')?.textContent === '历史水稻病害')
      assert.equal(calls.filter(call => /\/api\/(ai|pest)\//.test(call.path)).length, 0)
      if (await page.$('.prompt-collapsed')) await page.click('.prompt-collapsed')
      await page.click('.prompt-mode-button:last-of-type')
      assert.ok((await page.$eval('.prompt-model-button', el => el.textContent)).includes('GPT 5.5'))
    })
    await t.test('识别发送类别位置与图片，仅调用识别并保存复核快照', async () => {
      await identify(); await page.waitForSelector('.turn-detection-card'); await enabled()
      const body = calls.find(call => call.path === '/api/pest/identify').payload
      assert.ok(body.includes('水稻') && body.includes('101020100'))
      const saved = calls.find(call => call.path === '/api/record/add').payload
      assert.equal(saved.reviewResult, '【确诊结果】：水稻褐斑病'); assert.equal(saved.yoloUsed, true)
      assert.equal(saved.userId, 42); assert.equal(saved.cropId, 9)
      assert.equal(calls.filter(call => call.path === '/api/ai/chat').length, 0)
      assert.ok((await page.$eval('.turn-confidence', el => el.textContent)).includes('91%'))
    })
    await t.test('报告真实参数、失败重试与下方对话', async () => {
      failure = 'report'
      await page.click('.turn-report-button'); await page.waitForSelector('.turn-report-message .turn-error')
      assert.ok((await page.$eval('.turn-report-message', el => el.textContent)).includes('报告生成失败'))
      await page.click('.turn-report-message .turn-error button'); await page.waitForSelector('.turn-report')
      const params = new URLSearchParams(calls.find(call => call.path === '/api/pest/diagnose').payload)
      assert.equal(params.get('reviewResult'), '【确诊结果】：水稻褐斑病'); assert.equal(params.get('locationId'), '101020100')
      assert.equal(JSON.parse(params.get('predictionJson')).confidence, .91)
      assert.equal(await page.$eval('.turn-user--identify', el => Boolean(el.querySelector('.turn-report'))), false)
    })
    await t.test('追问携带图片复核和报告上下文，失败不伪装成功并可重试', async () => {
      await enabled(); await page.click('.prompt-mode-button:last-of-type'); await fill('如何处理？')
      failure = 'chat'; await page.click('.prompt-action'); await page.waitForSelector('.turn-error button')
      assert.equal(await page.$('.turn-answer'), null)
      await page.click('.turn-error button'); await page.waitForSelector('.turn-answer'); await enabled()
      const chat = calls.find(call => call.path === '/api/ai/chat').payload
      assert.equal(chat.pestName, '水稻褐斑病')
      assert.ok(chat.messages.some(message => message.content.includes('诊断报告')))
      assert.ok(chat.messages.some(message => message.content.includes('确诊结果')))
      assert.ok(chat.messages.some(message => message.imageBase64?.startsWith('data:image/')))
      assert.equal(await page.$$eval('.workspace-turn', els => els.length), 2)
      await page.reload({ waitUntil: 'networkidle0' })
      await page.evaluate(() => [...document.querySelectorAll('button')].find(button => button.textContent.trim() === '进入工作台')?.click())
      await page.waitForSelector('#workspace-records-recognition .records-list li:nth-child(2) button')
      await page.$$eval('#workspace-records-recognition .records-list button', els => els.find(el => el.textContent.includes('水稻褐斑病')).click())
      await page.waitForSelector('.turn-answer'); assert.ok(await page.$('.turn-report'))
    })
    await t.test('识别失败可重试，Dify 失败仍保留 YOLO 结果', async () => {
      failure = 'identify'; reviewFailure = true
      await identify(); await page.waitForSelector('.turn-assistant--identify .turn-error button')
      await page.click('.turn-assistant--identify .turn-error button'); await page.waitForSelector('.turn-detection-card'); await enabled()
      assert.ok((await page.$eval('.turn-warning', el => el.textContent)).includes('Dify'))
      assert.ok(await page.$('.turn-report-button'))
      reviewFailure = false
    })
    await t.test('无意义确诊锁报告，无 YOLO 时不显示假置信度', async () => {
      meaningless = true; await identify(); await page.waitForSelector('.turn-detection-card'); await enabled()
      assert.equal(await page.$('.turn-report-button'), null)
      assert.equal(await page.$('.turn-confidence'), null)
      assert.ok((await page.$eval('.turn-detection-card', el => el.textContent)).includes('无意义'))
      meaningless = false
    })
    await t.test('语音按钮停止录音后提交现有 ASR 并回填文字', async () => {
      await page.click('[aria-label="新建对话"]'); await page.click('.prompt-mode-button:last-of-type')
      await enabled(); await page.click('[aria-label="语音输入"]')
      await page.waitForSelector('[aria-label="停止录音"]'); await page.click('[aria-label="停止录音"]')
      await page.waitForFunction(() => document.querySelector('.prompt-textarea').value.includes('请问水稻'))
      assert.ok(calls.some(call => call.path === '/api/ai/speech-to-text'))
      assert.ok(await page.evaluate(() => window.micTracksStopped > 0))
    })
    await t.test('切换账号取消迟到请求且历史不混用', async () => {
      await enabled(); await fill('账号切换期间的问题'); await page.click('.prompt-action')
      await page.waitForSelector('.turn-pending')
      await page.evaluate(() => { localStorage.setItem('user', JSON.stringify({ userId: 43, username: 'other' })); window.dispatchEvent(new CustomEvent('leafquery-auth-changed')) })
      await page.waitForSelector('.workspace-welcome'); await enabled()
      await new Promise(resolve => setTimeout(resolve, 500))
      assert.equal(await page.$('.workspace-output'), null)
      assert.equal(await page.$eval('[aria-controls="workspace-records-recognition"] .records-count', el => el.textContent), '0')
    })
    assert.deepEqual(errors, [])
  } finally { await browser?.close(); await server.close() }
})
