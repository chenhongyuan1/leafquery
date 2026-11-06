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

test('数据中心首次进入及尺寸变化时图表保持在所属卡片内', { timeout: 120000 }, async t => {
  assert.ok(browserPath, '请安装 Chrome/Edge，或通过 BROWSER_EXECUTABLE_PATH 指定 Chromium 浏览器路径')
  const server = await createServer({ root, server: { port: 0, open: false }, logLevel: 'error' })
  let browser
  try {
    await server.listen()
    browser = await puppeteer.launch({ headless: true, executablePath: browserPath })
    const page = await browser.newPage()
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.setViewport({ width: 1600, height: 1000 })
    await page.evaluateOnNewDocument(() => {
      localStorage.setItem('pc-theme', 'light')
      localStorage.setItem('farm_data_local', JSON.stringify({
        crops: [{ id: 1, name: '水稻', stageMode: 'MANUAL', stage: '分蘖期', locationId: '101010100', city: '北京', isActive: true }],
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
          ...input, supported: true, todayRiskScore: 0.5,
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
      // vue-echarts autoresize is throttled; let it process the latest container resize.
      await pause(250)
      const sizes = await page.evaluate(() => {
        const card = title => [...document.querySelectorAll('h3')].find(h => h.textContent === title).closest('section')
        const confidence = card('置信度分析')
        const forecast = card('7 天风险对比预测')
        const rect = element => {
          const { x, y, width, height, right, bottom } = element.getBoundingClientRect()
          return { x, y, width, height, right, bottom }
        }
        return {
          viewport: innerWidth, parent: rect(confidence.parentElement), confidence: rect(confidence), forecast: rect(forecast),
          gap: parseFloat(getComputedStyle(confidence.parentElement).columnGap),
          charts: [...document.querySelectorAll('.pc-data-page x-vue-echarts')].map(chart => ({
            chart: rect(chart), canvas: rect(chart.querySelector('canvas')),
            card: rect(chart.closest('.pc-data-soft') || chart.closest('.pc-data-chart'))
          }))
        }
      })
      assert.ok(sizes.forecast.right <= sizes.parent.right + 1, `${label}: 风险预测卡片被挤出网格 ${JSON.stringify(sizes)}`)
      assert.ok(sizes.confidence.x >= sizes.parent.x - 1, `${label}: 置信度卡片超出网格`)
      if (sizes.viewport >= 1280) {
        const expected = (sizes.parent.width - sizes.gap) * 0.44
        assert.ok(Math.abs(sizes.confidence.width - expected) <= 1, `${label}: 置信度卡片撑大了左列`)
        assert.ok(Math.abs(sizes.confidence.y - sizes.forecast.y) <= 1, `${label}: 双列应位于同一行`)
        assert.ok(sizes.forecast.x >= sizes.confidence.right + sizes.gap - 1, `${label}: 两侧卡片重叠`)
      } else {
        assert.ok(Math.abs(sizes.confidence.width - sizes.parent.width) <= 1, `${label}: 窄桌面应切换单列`)
        assert.ok(sizes.forecast.y >= sizes.confidence.bottom, `${label}: 单列卡片重叠`)
      }
      assert.ok(sizes.charts.length >= 4, `${label}: 应实际渲染 ECharts 图表`)
      for (const { chart, canvas, card } of sizes.charts) {
        assert.ok(canvas.width > 0 && canvas.height > 0, `${label}: 画布必须有有效尺寸`)
        assert.ok(chart.x >= card.x && chart.right <= card.right + 1, `${label}: 图表容器溢出卡片`)
        assert.ok(Math.abs(canvas.width - chart.width) <= 1, `${label}: 画布未随容器调整`)
      }
      return sizes
    }
    async function waitForRefresh() {
      await page.waitForFunction(() => [...document.querySelectorAll('button')].some(button => button.textContent.trim() === '刷新看板' && !button.disabled))
    }

    await page.goto(server.resolvedUrls.local[0], { waitUntil: 'networkidle0' })
    await clickButton('进入工作台')
    await enterDataCenter()
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
        await page.evaluate(dark => document.documentElement.classList.toggle('dark', dark), dark)
        for (const width of [1024, 1280, 1366, 1920, 1600]) {
          await page.setViewport({ width, height: 1000 })
          await assertLayout(`${dark ? 'dark' : 'light'}-${width}`)
        }
      }
      if (process.env.LAYOUT_ARTIFACT_DIR) {
        await mkdir(process.env.LAYOUT_ARTIFACT_DIR, { recursive: true })
        // Capture the exact pair, independent of the page's scroll position.
        const target = await page.evaluateHandle(() => [...document.querySelectorAll('h3')].find(h => h.textContent === '置信度分析').closest('section').parentElement)
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
    assert.deepEqual(errors, [], '页面不应发生未捕获的运行错误')
  } finally {
    await browser?.close()
    await server.close()
  }
})
