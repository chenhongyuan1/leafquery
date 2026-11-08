import assert from 'node:assert/strict'
import test from 'node:test'
import { existsSync } from 'node:fs'
import { mkdir } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import { resolve } from 'node:path'
import { createServer } from 'vite'
import puppeteer from 'puppeteer-core'
const root = fileURLToPath(new URL('../', import.meta.url))
const executablePath = [process.env.BROWSER_EXECUTABLE_PATH, `${process.env.PROGRAMFILES}/Google/Chrome/Application/chrome.exe`].find(path => path && existsSync(path))
const markdown = '# 诊断报告\n\n> **分类校正**：水稻\n\n### 防治建议\n\n- **观察叶片**\n- *减少积水*\n\n1. 补充近景\n2. 观察变化\n\n| 项目 | 结果 |\n| --- | --- |\n| 风险 | 中 |\n\n```text\n保持观察\n```\n\n[资料](https://example.com)\n\n<img src=x onerror="window.markdownXss=true">\n\n[危险链接](javascript:alert(1))'
const image = resolve(root, 'public/images/library/rice_blast.png')
const evidence = resolve(root, '../.leafquery-runtime/workspace-rendering')

test('两端格式、阶段提示和识别图片发送', { timeout: 90000 }, async t => {
  const server = await createServer({ root, server: { port: 0, open: false }, logLevel: 'error' })
  let browser
  try {
    await server.listen(); await mkdir(evidence, { recursive: true })
    browser = await puppeteer.launch({ headless: true, executablePath })
    const page = await browser.newPage(); await page.setViewport({ width: 1600, height: 1000 })
    const errors = []; page.on('pageerror', error => errors.push(error.message))
    await page.setRequestInterception(true)
    page.on('request', request => {
      const path = new URL(request.url()).pathname
      if (!path.startsWith('/api/')) return request.continue()
      let data = { code: 200, data: [] }, delay = 0
      if (path === '/api/pest/identify') { delay = 200; data = { prediction: { pest_name: '稻瘟病', confidence: .8 }, image_url: '/vite.svg', yolo_used: true, review_required: true, review_result: '【确诊结果】：稻瘟病' } }
      if (path === '/api/pest/diagnose') { delay = 4300; data = { report: markdown } }
      if (path === '/api/ai/chat') { delay = 200; data = { reply: markdown } }
      setTimeout(() => request.respond({ status: 200, contentType: 'application/json', body: JSON.stringify(data) }).catch(() => {}), delay)
    })
    await page.goto(server.resolvedUrls.local[0], { waitUntil: 'networkidle0' })
    await page.evaluate(() => [...document.querySelectorAll('button')].find(button => button.textContent.trim() === '进入工作台').click())
    await page.waitForSelector('.prompt-collapsed'); await page.click('.prompt-collapsed'); await page.waitForFunction(() => !document.querySelector('.prompt-action').disabled)
    await page.click('.prompt-mode-button:first-of-type'); await page.click('.prompt-choose-categories'); await page.click('[role="menuitemcheckbox"]'); await page.click('.prompt-category-footer button')
    await (await page.$('.prompt-file-input')).uploadFile(image)
    await page.click('.prompt-action'); await page.waitForSelector('.turn-detection-card')
    await t.test('发送即清空识别附件，保留类别、原图和记录缩略图', async () => {
      assert.equal(await page.$('.prompt-inline-attachments .prompt-thumbnail'), null)
      for (const selector of ['.prompt-category-pill', '.turn-images img', '#workspace-records-recognition .records-thumbnail img', '.prompt-add']) assert.ok(await page.$(selector))
    })
    await page.click('.turn-report-button')
    await t.test('Thought Line 轮换阶段与计时，完成自动收起并可展开', async () => {
      await page.waitForSelector('.turn-report-message .thought-line[data-working]', { timeout: 1500 })
      await page.waitForFunction(() => document.querySelector('.turn-report-message .thought-line__timer')?.textContent !== '0.0秒', { timeout: 1500 })
      await page.waitForFunction(() => document.querySelector('.turn-report-message .thought-line__current')?.textContent.includes('检索'), { timeout: 4200 })
      const aligned = await page.evaluate(() => document.querySelector('.turn-report-message .thought-line__head').getBoundingClientRect().left - document.querySelector('.turn-report-message').getBoundingClientRect().left < 60)
      assert.equal(aligned, true, '阶段提示应靠左对齐，不能被旧等待样式居中')
      await page.screenshot({ path: resolve(evidence, 'pc-report-pending.png') })
      await page.waitForSelector('.turn-report')
      assert.equal(await page.$eval('.turn-report-message .thought-line__head', el => el.getAttribute('aria-expanded')), 'false')
      await page.click('.turn-report-message .thought-line__head')
      assert.equal(await page.$eval('.turn-report-message .thought-line__head', el => el.getAttribute('aria-expanded')), 'true')
    })
    await page.waitForSelector('.turn-report')
    const checkMarkdown = async selector => {
      const facts = await page.$eval(selector, el => ({ h1: el.querySelector('h1')?.textContent, quote: !!el.querySelector('blockquote strong'), list: !!el.querySelector('ul li strong'), ordered: !!el.querySelector('ol li'), table: !!el.querySelector('table td'), code: !!el.querySelector('pre code'), unsafe: !!el.querySelector('script, img[onerror], a[href^="javascript:"]') }))
      assert.equal(facts.h1, '诊断报告'); assert.ok(facts.quote && facts.list && facts.ordered && facts.table && facts.code); assert.equal(facts.unsafe, false)
      assert.equal(await page.evaluate(() => Boolean(window.markdownXss)), false)
    }
    await t.test('PC 报告和回答解析 Markdown 并关闭原始 HTML', async () => {
      await checkMarkdown('.turn-report')
      await page.click('.prompt-mode-button:last-of-type')
      await page.$eval('.prompt-textarea', el => { el.value = '解释报告'; el.dispatchEvent(new Event('input', { bubbles: true })) })
      await page.click('.prompt-action'); await page.waitForSelector('.turn-answer')
      await checkMarkdown('.turn-answer')
      await page.screenshot({ path: resolve(evidence, 'pc-markdown.png') })
    })
    await t.test('移动端报告使用相同渲染器，窄屏无溢出', async () => {
      await page.setViewport({ width: 390, height: 844 })
      await page.waitForFunction(() => !document.querySelector('.pc-workspace'))
      await page.evaluate(() => [...document.querySelectorAll('button')].find(button => button.textContent.includes('水稻')).click())
      await (await page.$('input[type="file"][accept="image/*"]')).uploadFile(image)
      await page.waitForFunction(() => [...document.querySelectorAll('button')].some(button => button.textContent.includes('生成 AI 诊断')))
      await page.evaluate(() => [...document.querySelectorAll('button')].find(button => button.textContent.includes('生成 AI 诊断')).click())
      await page.waitForSelector('.markdown-body h1')
      await checkMarkdown('.markdown-body')
      assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)
      await page.screenshot({ path: resolve(evidence, 'mobile-markdown.png') })
      await page.$eval('input[placeholder="附图追问大模型..."]', el => { el.value = '继续解释'; el.dispatchEvent(new Event('input', { bubbles: true })); el.form.requestSubmit() })
      await page.waitForFunction(() => document.querySelectorAll('.markdown-content h1').length === 2)
      await checkMarkdown('.markdown-content:last-child')
    })
    assert.deepEqual(errors, [])
  } finally { await browser?.close(); await server.close() }
})
