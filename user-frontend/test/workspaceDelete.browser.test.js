import assert from 'node:assert/strict'
import test from 'node:test'
import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import puppeteer from 'puppeteer-core'

const root = fileURLToPath(new URL('../', import.meta.url))
const executablePath = [process.env.BROWSER_EXECUTABLE_PATH, `${process.env.PROGRAMFILES}/Google/Chrome/Application/chrome.exe`].find(path => path && existsSync(path))
test('工作台记录删除、失败保留与本机持久化', { timeout: 90000 }, async t => {
  const server = await createServer({ root, server: { port: 0, open: false }, logLevel: 'error' })
  let browser
  try {
    await server.listen(); browser = await puppeteer.launch({ headless: true, executablePath })
    const page = await browser.newPage(); await page.setViewport({ width: 1600, height: 1000 })
    await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }])
    await page.evaluateOnNewDocument(() => localStorage.setItem('user', JSON.stringify({ userId: 42, username: 'fixture' })))
    let accept = false, failDelete = true, failIdentify = false, deleteCalls = 0
    let rows = [11, 12].map(id => ({ id, pestName: `病害${id}`, cropName: '水稻', confidence: .8, imageUrl: '/vite.svg', createTime: '2026-11-08T10:00:00' }))
    page.on('dialog', dialog => accept ? dialog.accept() : dialog.dismiss())
    const errors = []; page.on('pageerror', error => errors.push(error.message))
    await page.setRequestInterception(true)
    page.on('request', request => {
      const path = new URL(request.url()).pathname
      if (!path.startsWith('/api/')) return request.continue()
      let body = { code: 200, data: [] }, status = 200, delay = 0
      if (path === '/api/record/list') body.data = rows
      if (request.method() === 'DELETE' && path.startsWith('/api/record/')) {
        deleteCalls++; delay = 150
        if (failDelete) { status = 500; body = { code: 500, message: '删除服务暂不可用' } }
        else rows = rows.filter(row => String(row.id) !== path.split('/').at(-1))
      }
      if (path === '/api/ai/chat') { delay = 700; body = { reply: '这是一条回答。' } }
      if (path === '/api/pest/identify') { delay = 400; status = failIdentify ? 503 : 200; body = failIdentify ? { error: '推理服务不可用' } : { prediction: { pest_name: '稻瘟病', confidence: .8 }, image_url: '/vite.svg', yolo_used: true } }
      setTimeout(() => request.respond({ status, contentType: 'application/json', body: JSON.stringify(body) }).catch(() => {}), delay)
    })
    await page.goto(server.resolvedUrls.local[0], { waitUntil: 'networkidle0' })
    const enter = async () => { await page.evaluate(() => [...document.querySelectorAll('button')].find(button => button.textContent.trim() === '进入工作台')?.click()); await page.waitForSelector('.workspace-records') }
    await enter(); await page.waitForFunction(() => document.querySelectorAll('#workspace-records-recognition .records-delete').length === 2)
    const cloudDelete = '#workspace-records-recognition li:first-child .records-delete'
    const enabled = () => page.waitForFunction(() => !document.querySelector('.prompt-action').disabled)
    const fill = value => page.$eval('.prompt-textarea', (el, value) => { el.value = value; el.dispatchEvent(new Event('input', { bubbles: true })) }, value)
    await t.test('取消确认不调用后端', async () => {
      await page.$eval(cloudDelete, el => el.click())
      assert.equal(deleteCalls, 0)
      assert.equal(await page.$$eval('#workspace-records-recognition .records-delete', els => els.length), 2)
    })
    await t.test('删除失败保留当前记录，成功后清空当前上下文且刷新不恢复', async () => {
      accept = true
      await page.click('#workspace-records-recognition li:first-child button')
      await page.waitForSelector('.turn-detection-card')
      await page.$eval(cloudDelete, el => el.click())
      await page.waitForFunction(() => document.querySelector('.workspace-notice')?.textContent.includes('删除失败'))
      assert.equal(await page.$$eval('#workspace-records-recognition .records-delete', els => els.length), 2)
      assert.ok((await page.$eval('.turn-detection-card', el => el.textContent)).includes('病害11'))
      failDelete = false
      await page.$eval(cloudDelete, el => el.click())
      await page.waitForSelector('.workspace-welcome')
      await page.waitForFunction(() => document.querySelectorAll('#workspace-records-recognition .records-delete').length === 1)
      assert.equal(deleteCalls, 2)
      await page.reload({ waitUntil: 'networkidle0' }); await enter(); await enabled()
      assert.equal(await page.$$eval('#workspace-records-recognition .records-delete', els => els.length), 1)
      assert.equal(await page.$eval('#workspace-records-recognition', el => el.textContent.includes('病害11')), false)
    })
    await t.test('处理中禁用删除，对话删除只作用于本机', async () => {
      if (await page.$('.prompt-collapsed')) await page.click('.prompt-collapsed')
      await fill('删除测试'); await page.click('.prompt-action'); await page.waitForSelector('.turn-pending')
      assert.equal(await page.$eval('#workspace-records-conversation .records-delete', el => el.disabled), true)
      await page.waitForSelector('.turn-answer'); await enabled()
      await page.$eval('#workspace-records-conversation .records-delete', el => el.click())
      await page.waitForSelector('.workspace-welcome')
      assert.equal(deleteCalls, 2)
      assert.equal(await page.$eval('[aria-controls="workspace-records-conversation"] .records-count', el => el.textContent), '0')
      await page.reload({ waitUntil: 'networkidle0' }); await enter(); await enabled()
      assert.equal(await page.$eval('[aria-controls="workspace-records-conversation"] .records-count', el => el.textContent), '0')
    })
    await t.test('删除其他记录保留当前草稿，未云保存的失败识别可直接删除', async () => {
      if (await page.$('.prompt-collapsed')) await page.click('.prompt-collapsed')
      await fill('保留草稿')
      await page.$eval(cloudDelete, el => el.click())
      await page.waitForFunction(() => document.querySelector('[aria-controls="workspace-records-recognition"] .records-count').textContent === '0')
      assert.equal(await page.$eval('.prompt-textarea', el => el.value), '保留草稿')
      failIdentify = true
      await page.click('.prompt-mode-button:first-of-type'); await page.click('.prompt-choose-categories'); await page.click('[role="menuitemcheckbox"]'); await page.click('.prompt-category-footer button')
      await (await page.$('.prompt-file-input')).uploadFile(fileURLToPath(new URL('../public/images/library/rice_blast.png', import.meta.url)))
      await page.click('.prompt-action'); await page.waitForSelector('.turn-error'); await enabled()
      await page.$eval(cloudDelete, el => el.click())
      await page.waitForSelector('.workspace-welcome')
      assert.equal(deleteCalls, 3, '失败识别没有服务器记录，不能误发 DELETE')
      assert.equal(await page.$eval('[aria-controls="workspace-records-recognition"] .records-count', el => el.textContent), '0')
    })
    assert.deepEqual(errors, [])
  } finally { await browser?.close(); await server.close() }
})
