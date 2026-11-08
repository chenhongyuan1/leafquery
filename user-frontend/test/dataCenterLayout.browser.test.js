import assert from 'node:assert/strict'
import { existsSync } from 'node:fs'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'
import { createServer } from 'vite'
import puppeteer from 'puppeteer-core'

// Real Vue/ECharts rendering, isolated profile and mocked APIs; no backend required.
const root = fileURLToPath(new URL('../', import.meta.url))
const browserPath = [
  process.env.BROWSER_EXECUTABLE_PATH,
  `${process.env.PROGRAMFILES}/Google/Chrome/Application/chrome.exe`,
  `${process.env['PROGRAMFILES(X86)']}/Microsoft/Edge/Application/msedge.exe`,
  '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
].find(path => path && existsSync(path))
const pause = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds))

test('极简流线数据中心保持分栏、真实数据和交互', { timeout: 120000 }, async t => {
  assert.ok(browserPath, '请安装 Chrome/Edge，或通过 BROWSER_EXECUTABLE_PATH 指定 Chromium 浏览器路径')
  const runningBase = process.env.DATA_CENTER_TEST_BASE_URL
  const server = runningBase ? null : await createServer({ root, server: { port: 0, open: false }, logLevel: 'error' })
  let browser
  try {
    await server?.listen()
    const base = runningBase || server.resolvedUrls.local[0]
    browser = await puppeteer.launch({ headless: true, executablePath: browserPath })
    const page = await browser.newPage()
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.setViewport({ width: 1600, height: 1000 })
    await page.evaluateOnNewDocument(() => {
      localStorage.setItem('pc-theme', 'light')
      if (sessionStorage.getItem('dc-empty')) { localStorage.removeItem('farm_data_local'); return }
      localStorage.setItem('farm_data_local', JSON.stringify({
        crops: [{ id: 1, name: '水稻', stageMode: 'MANUAL', stage: '分蘖期', locationId: '101010100', city: '北京', isActive: true }, { id: 2, name: '玉米', stageMode: 'MANUAL', stage: '拔节期', locationId: '101010100', city: '北京' }],
        activeCropId: 1,
        identificationHistory: [0.9, 0.8, 0.2, 0.1].map((confidence, id) => ({ id, cropId: 1, cropName: '水稻', pestName: '稻瘟病', confidence, time: '2026-11-05' }))
      }))
    })
    let forecastDelay = 0
    let forecastFails = false
    await page.setRequestInterception(true)
    page.on('request', async request => {
      const url = new URL(request.url())
      if (!url.pathname.startsWith('/api/')) return request.continue()
      let body = { code: 200, data: [] }
      let status = 200
      if (url.pathname === '/api/trend/targets') body = { diseaseTargets: ['稻瘟病'], pestTargets: ['稻飞虱'] }
      if (url.pathname === '/api/trend/forecast') {
        await pause(forecastDelay)
        const input = JSON.parse(request.postData())
        status = forecastFails ? 503 : 200
        body = forecastFails ? { message: '模拟预测服务暂不可用' } : {
          ...input, supported: true, todayRiskScore: 0.5, warnings: ['注意巡田'], topDrivers: ['湿度偏高'], modelVersion: 'rule_v2',
          dailySeries: Array.from({ length: 7 }, (_, i) => ({ date: `11/${5 + i}`, riskScore: 0.3 + i * 0.07 }))
        }
      }
      if (url.pathname === '/api/weather/features') body = { temp_mean_3d: 24, humidity_mean_3d: 80, rain_sum_7d: 20 }
      await request.respond({ status, contentType: 'application/json', body: JSON.stringify(body) })
    })

    async function clickButton(text, exact = true) {
      await page.waitForFunction((text, exact) => [...document.querySelectorAll('button')].some(button => exact ? button.textContent.trim() === text : button.textContent.includes(text)), {}, text, exact)
      await page.evaluate((text, exact) => [...document.querySelectorAll('button')].find(button => exact ? button.textContent.trim() === text : button.textContent.includes(text)).click(), text, exact)
    }
    async function enterDataCenter() {
      await clickButton('数据中心', false)
      await page.waitForFunction(() => [...document.querySelectorAll('h3')].some(h => h.textContent === '置信度分析'))
    }
    async function assertLayout(label) {
      await page.waitForFunction(() => {
        const charts = [...document.querySelectorAll('.pc-data-page .dc-chart')]
        return charts.length === 6 && charts.every(el => el.querySelector('canvas'))
      })
      await pause(350)
      const sizes = await page.evaluate(() => {
        const rect = el => { const r = el.getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, height: r.height, right: r.right, bottom: r.bottom } }
        const root = document.querySelector('.pc-data-page')
        return {
          viewport: innerWidth, height: innerHeight, main: rect(document.querySelector('.dc-main')),
          root: { ...rect(root), scrollHeight: root.scrollHeight, clientHeight: root.clientHeight },
          columns: [...document.querySelectorAll('.dc-column')].map(rect),
          charts: [...document.querySelectorAll('.pc-data-page .dc-chart')].map(el => ({ chart: rect(el), canvas: rect(el.querySelector('canvas')), panel: rect(el.closest('.dc-panel')) })),
          panelRadius: getComputedStyle(document.querySelector('.dc-panel')).borderRadius,
          metrics: document.querySelectorAll('.dc-metric').length,
          filterBackground: getComputedStyle(document.querySelector('.dc-filter')).backgroundColor,
          titleSize: parseFloat(getComputedStyle(document.querySelector('.dc-panel h3')).fontSize),
          pills: [...document.querySelectorAll('.dc-button,.dc-filter button,.dc-crops button,.dc-targets button')].every(el => parseFloat(getComputedStyle(el).borderTopLeftRadius) >= el.clientHeight / 2),
          text: root.textContent
        }
      })
      assert.ok(sizes.titleSize >= 16 && sizes.pills, '字号和胶囊按钮需符合要求')
      assert.equal(sizes.filterBackground, 'rgba(0, 0, 0, 0)', '筛选应为独立胶囊，不应出现整条背景')
      assert.equal(sizes.metrics, 8, label + ': 应保留八项真实指标')
      assert.equal(sizes.panelRadius, '0px', label + ': 无圆角卡片面板')
      assert.ok(!sizes.text.includes('精密分栏') && !sizes.text.includes('光谱终端'), '只保留极简流线')
      assert.equal(sizes.charts.length, 6)
      if (sizes.viewport >= 1280) {
        assert.ok(sizes.columns[1].x >= sizes.columns[0].right - 1, label + ': 三列不能重叠')
        assert.ok(sizes.columns[2].x >= sizes.columns[1].right - 1)
        assert.ok(sizes.columns[2].right <= sizes.main.right + 1)
        if (sizes.height > 800) assert.ok(sizes.root.scrollHeight <= sizes.root.clientHeight + 2, label + ': 常规桌面应一屏呈现')
      } else {
        assert.ok(sizes.columns[2].y >= sizes.columns[0].bottom - 1, label + ': 窄桌面上下分栏')
      }
      for (const { chart, canvas, panel } of sizes.charts) {
        assert.ok(canvas.width > 0 && canvas.height > 40, label + ': 图表必须可见')
        assert.ok(chart.x >= panel.x - 1 && chart.right <= panel.right + 1, label + ': 图表越界')
        assert.ok(Math.abs(canvas.width - chart.width) <= 1, label + ': 图表未跟随尺寸变化 ' + JSON.stringify({ canvas, chart }))
      }
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), label + ': 页面不能横向溢出')
      return sizes
    }
    async function waitForRefresh() {
      await page.waitForFunction(() => [...document.querySelectorAll('button')].some(button => button.textContent.trim() === '刷新看板' && !button.disabled))
    }

    await page.goto(base, { waitUntil: 'networkidle0' })
    await clickButton('进入工作台')
    await enterDataCenter()
    await waitForRefresh()
    await t.test('冷启动首次进入', async () => {
      await assertLayout('first-entry')
      assert.equal(await page.$$eval('.pc-data-page canvas', canvases => canvases.length), 6)
    })
    await t.test('刷新看板及延迟返回', async () => {
      forecastDelay = 600
      await clickButton('刷新看板')
      await page.waitForFunction(() => document.querySelector('.pc-data-page')?.textContent.includes('正在生成全部对象'))
      await assertLayout('forecast-loading')
      await waitForRefresh()
      await assertLayout('forecast-loaded')
      forecastDelay = 0
    })
    await t.test('离开后重新进入', async () => {
      await clickButton('工作台', false)
      await page.waitForFunction(() => !document.querySelector('.pc-data-page'))
      await enterDataCenter()
      await assertLayout('re-entry')
    })
    await t.test('跨桌面断点缩放与深浅主题', async () => {
      for (const dark of [false, true]) {
        const currentDark = await page.evaluate(() => document.documentElement.classList.contains('dark'))
        if (dark !== currentDark) {
          await page.click(`button[aria-label="${dark ? '切换到暗色主题' : '切换到亮色主题'}"]`)
          await page.waitForFunction(dark => document.documentElement.classList.contains('dark') === dark, {}, dark)
          await pause(700)
        }
        for (const width of [1024, 1280, 1366, 1920, 1600]) {
          await page.setViewport({ width, height: 1000 })
          await assertLayout(`${dark ? 'dark' : 'light'}-${width}`)
        }
        if (process.env.LAYOUT_ARTIFACT_DIR) {
          await mkdir(process.env.LAYOUT_ARTIFACT_DIR, { recursive: true })
          await page.screenshot({ path: resolve(process.env.LAYOUT_ARTIFACT_DIR, dark ? 'minimal-dark.png' : 'minimal-light.png') })
        }
      }
      if (process.env.LAYOUT_ARTIFACT_DIR) {
        await mkdir(process.env.LAYOUT_ARTIFACT_DIR, { recursive: true })
        // Capture the exact pair, independent of the page's scroll position.
        const target = await page.$('.pc-data-page')
        await target.screenshot({ path: resolve(process.env.LAYOUT_ARTIFACT_DIR, 'data-center-after.png') })
        await target.dispose()
      }
    })
    await t.test('预测失败提示及恢复', async () => {
      forecastFails = true
      await clickButton('刷新看板')
      await waitForRefresh()
      await page.waitForFunction(() => document.querySelector('.pc-data-page')?.textContent.includes('当前作物暂无可用预测结果'))
      await assertLayout('forecast-error')
      forecastFails = false
      await clickButton('刷新看板')
      await waitForRefresh()
      await assertLayout('forecast-recovered')
    })
    await t.test('浏览器刷新后再次进入', async () => {
      await page.reload({ waitUntil: 'networkidle0' })
      await clickButton('进入工作台')
      await enterDataCenter()
      await assertLayout('page-reload')
    })
    await t.test('对象筛选与切换不重复请求预测', async () => {
      await clickButton('仅虫害')
      await page.waitForFunction(() => document.querySelectorAll('.dc-targets button').length === 1)
      assert.equal(await page.$eval('.dc-targets button', el => el.textContent.trim()), '稻飞虱')
      await page.click('.dc-targets button')
      await page.waitForFunction(() => document.querySelector('.dc-structure h3').textContent.includes('稻飞虱'))
      await clickButton('全部')
      assert.equal(await page.$$eval('.dc-targets button', els => els.length), 2)
      await clickButton('玉米')
      await waitForRefresh()
      await page.waitForFunction(() => document.querySelector('.dc-region').textContent === '玉米')
      assert.ok(await page.$eval('.dc-record-list', el => el.textContent.includes('暂无识别记录')))
      await clickButton('水稻')
      await waitForRefresh()
    })
    await t.test('农场抽屉复用原管理表单并支持关闭', async () => {
      await clickButton('农场档案管理', false)
      await page.waitForSelector('.data-center-farm-drawer[open]')
      await pause(350)
      assert.ok(await page.$eval('.data-center-farm-drawer', el => el.textContent.includes('新增作物')))
      await page.$eval('.data-center-farm-drawer', el => [...el.querySelectorAll('button')].find(b => b.textContent.trim() === '新增作物').click())
      await page.waitForSelector('.data-center-farm-drawer .pc-farm-modal')
      assert.ok(await page.$eval('.pc-farm-modal', el => { const r = el.getBoundingClientRect(); const target = document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2); return el.contains(target) }), '原管理表单应可见可操作')
      await page.$eval('.pc-farm-modal', el => [...el.querySelectorAll('button')].find(b => b.textContent.trim() === '取消').click())
      await page.keyboard.press('Escape')
      await page.waitForSelector('.data-center-farm-drawer[open]', { hidden: true })
    })
    await t.test('无作物时显示空状态而非示例数据', async () => {
      await page.evaluate(() => sessionStorage.setItem('dc-empty', '1'))
      await page.reload({ waitUntil: 'networkidle0' })
      await clickButton('进入工作台')
      await enterDataCenter()
      await waitForRefresh()
      assert.ok(await page.$eval('.dc-chart-status', el => el.textContent.includes('请先')))
      assert.equal(await page.$eval('.dc-metric strong', el => el.textContent), '—')
      assert.ok(await page.$eval('.dc-record-list', el => el.textContent.includes('暂无识别记录')))
    })
    assert.deepEqual(errors, [], '页面不应发生未捕获的运行错误')
  } finally {
    await browser?.close()
    await server?.close()
  }
})
