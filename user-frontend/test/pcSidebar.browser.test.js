import assert from 'node:assert/strict'
import { existsSync } from 'node:fs'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'
import { createServer } from 'vite'
import puppeteer from 'puppeteer-core'

// Isolated browser and mocked APIs: no real accounts, weather calls or user data.
const root = fileURLToPath(new URL('../', import.meta.url))
const evidence = resolve(root, '..', '.leafquery-runtime', 'pc-sidebar')
const browserPath = [
  process.env.BROWSER_EXECUTABLE_PATH,
  `${process.env.PROGRAMFILES}/Google/Chrome/Application/chrome.exe`,
  `${process.env['PROGRAMFILES(X86)']}/Microsoft/Edge/Application/msedge.exe`,
  '/usr/bin/google-chrome', '/usr/bin/chromium', '/usr/bin/chromium-browser',
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'
].find(path => path && existsSync(path))
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))

test('PC 侧栏收起时主内容仅居中移动并保留导航和账号操作', { timeout: 120000 }, async t => {
  assert.ok(browserPath, '需要 Chrome/Edge，或设置 BROWSER_EXECUTABLE_PATH')
  const server = await createServer({ root, server: { port: 0, open: false }, logLevel: 'error' })
  let browser
  try {
    await mkdir(evidence, { recursive: true })
    await server.listen()
    browser = await puppeteer.launch({ headless: true, executablePath: browserPath, ignoreDefaultArgs: ['--hide-scrollbars'] })
    const page = await browser.newPage()
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.setViewport({ width: 1600, height: 1000 })
    await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'no-preference' }])
    await page.evaluateOnNewDocument(() => {
      localStorage.setItem('pc-theme', 'light')
      localStorage.setItem('farm_data_local', JSON.stringify({
        crops: [{ id: 1, name: '水稻', stageMode: 'MANUAL', stage: '分蘖期', locationId: '101010100', city: '北京', isActive: true }],
        activeCropId: 1,
        identificationHistory: [0.9, 0.8].map((confidence, id) => ({ id, cropId: 1, cropName: '水稻', pestName: '稻瘟病', confidence, time: '2026-11-06' }))
      }))
    })
    await page.setRequestInterception(true)
    page.on('request', async request => {
      const url = new URL(request.url())
      if (!url.pathname.startsWith('/api/')) return request.continue()
      let body = { code: 200, data: [] }
      if (url.pathname === '/api/discovery/news') body = { code: 200, data: Array.from({ length: 18 }, (_, i) => ({ newsId: i + 1, title: `布局验证资讯 ${i + 1}`, summary: '模拟资讯用于验证完整页面滚动和内容居中。', createdAt: '2026-11-06T10:00:00' })) }
      if (url.pathname === '/api/trend/targets') body = { diseaseTargets: ['稻瘟病'], pestTargets: ['稻飞虱'] }
      if (url.pathname === '/api/trend/forecast') {
        body = { ...JSON.parse(request.postData()), supported: true, todayRiskScore: 0.5,
          dailySeries: Array.from({ length: 7 }, (_, i) => ({ date: `11/${6 + i}`, riskScore: 0.3 + i * 0.07 })) }
      }
      if (url.pathname === '/api/weather/features') body = { temp_mean_3d: 24, humidity_mean_3d: 80, rain_sum_7d: 20 }
      await request.respond({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
    })

    async function enterWorkspace() {
      await page.waitForFunction(() => [...document.querySelectorAll('button')].some(b => b.textContent.includes('进入工作台')))
      await page.evaluate(() => [...document.querySelectorAll('button')].find(b => b.textContent.includes('进入工作台')).click())
      await page.waitForSelector('.pc-sidebar-toggle')
    }
    async function dimensions() {
      return page.evaluate(() => {
        const rect = target => { const r = (typeof target === 'string' ? document.querySelector(target) : target).getBoundingClientRect(); return { width: r.width, left: r.left, right: r.right } }
        const surface = document.querySelector('.pc-page-scroll') || document.querySelector('.pc-page-host')
        const content = document.querySelector('.pc-main .pc-page-scroll .pc-content-frame') || document.querySelector('.pc-main .pc-content-frame')
        return { sidebar: rect('#pc-sidebar'), main: rect(content), chrome: rect('.pc-main'), gutter: surface.offsetWidth - surface.clientWidth, viewport: window.innerWidth, scrollWidth: document.documentElement.scrollWidth }
      })
    }
    async function setCollapsed(collapsed) {
      const expanded = await page.$eval('.pc-sidebar-toggle', el => el.getAttribute('aria-expanded') === 'true')
      if (expanded === collapsed) await page.click('.pc-sidebar-toggle')
      await page.waitForFunction(width => Math.abs(document.querySelector('#pc-sidebar').getBoundingClientRect().width - width) < 0.5, {}, collapsed ? 80 : 256)
      await pause(80)
    }
    function assertCentered(before, after) {
      assert.ok(Math.abs(after.main.width - before.main.width) < 1, '收起不能拉宽主内容')
      assert.ok(Math.abs(after.main.left - (before.main.left - 88)) < 1, '主内容应向左居中移动 88px')
      assert.ok(Math.abs(after.main.width + after.gutter - (after.viewport - 256)) < 1, '保持展开侧栏时的基准内容宽度')
      assert.ok(Math.abs(after.chrome.left - after.sidebar.right) < 1 && Math.abs(after.chrome.right - after.viewport) < 1, '页面表面应铺满侧栏以外区域')
      assert.ok(after.scrollWidth <= after.viewport, '不能出现页面横向溢出')
    }

    await page.goto(server.resolvedUrls.local[0], { waitUntil: 'networkidle0' })
    await enterWorkspace()
    await t.test('点击动画与键盘控制保持页面实例及内容宽度', async () => {
      const before = await dimensions()
      const content = await page.$('.pc-main > div > *')
      assert.ok(content)
      await page.screenshot({ path: resolve(evidence, 'expanded.png') })
      await setCollapsed(true)
      assertCentered(before, await dimensions())
      assert.equal(await content.evaluate(el => el.isConnected), true, '收起不得重建当前页面')
      assert.equal(await page.$eval('.pc-sidebar-toggle', el => el.getAttribute('aria-label')), '展开侧边栏')
      assert.equal(await page.$eval('.pc-sidebar-toggle', el => el.getAttribute('aria-controls')), 'pc-sidebar')
      await page.focus('.pc-sidebar-toggle')
      await page.keyboard.press('Enter')
      await page.waitForFunction(() => Math.abs(document.querySelector('#pc-sidebar').getBoundingClientRect().width - 256) < 0.5)
      assert.ok(Math.abs((await dimensions()).main.left - before.main.left) < 1)
      await page.keyboard.press('Space')
      await page.waitForFunction(() => Math.abs(document.querySelector('#pc-sidebar').getBoundingClientRect().width - 80) < 0.5)
    })
    await t.test('图标导航可用，侧栏展开不拉宽数据卡片和图表', async () => {
      await page.click('#pc-sidebar nav button[aria-label="数据中心"]')
      await page.waitForSelector('.pc-data-page canvas')
      await pause(350)
      const sizes = () => page.$$eval('.pc-data-page canvas', els => els.map(el => el.getBoundingClientRect().width))
      const before = await sizes()
      assert.ok(before.length >= 4)
      assert.equal(await page.$eval('#pc-sidebar nav [aria-current="page"]', el => el.getAttribute('aria-label')), '数据中心')
      assert.equal(await page.$eval('#pc-sidebar nav [aria-current="page"]', el => el.title), '数据中心')
      await setCollapsed(false)
      await pause(300)
      const after = await sizes()
      assert.equal(after.length, before.length)
      before.forEach((width, i) => assert.ok(Math.abs(width - after[i]) < 1, '收起或展开不得拉伸图表'))
      const expanded = await dimensions()
      await setCollapsed(true)
      assertCentered(expanded, await dimensions())
    })
    await t.test('深浅主题和不同桌面宽度下保持居中及无框图标', async () => {
      for (const dark of [false, true]) {
        const currentDark = await page.evaluate(() => document.documentElement.classList.contains('dark'))
        if (dark !== currentDark) {
          await page.click(`button[aria-label="${dark ? '切换到暗色主题' : '切换到亮色主题'}"]`)
          await page.waitForFunction(dark => document.documentElement.classList.contains('dark') === dark, {}, dark)
          await pause(650)
        }
        for (const width of [1024, 1280, 1920]) {
          await page.setViewport({ width, height: 1000 })
          await setCollapsed(false)
          const before = await dimensions()
          await setCollapsed(true)
          assertCentered(before, await dimensions())
          assert.equal(await page.$eval('#pc-sidebar .brand-copy', el => el.getAttribute('aria-hidden')), 'true')
          const icon = await page.$eval('#pc-sidebar .brand-icon', el => { const s = getComputedStyle(el); return { border: s.borderTopWidth, background: s.backgroundColor } })
          assert.equal(icon.border, '0px')
          assert.equal(icon.background, 'rgba(0, 0, 0, 0)')
        }
      }
      await page.setViewport({ width: 1600, height: 1000 })
      await pause(300)
      await page.screenshot({ path: resolve(evidence, 'collapsed-dark.png') })
    })
    await t.test('知识上栏铺满剩余视口，页面滚动贴浏览器右边缘', async () => {
      if (await page.evaluate(() => document.documentElement.classList.contains('dark'))) {
        await page.click('button[aria-label="切换到亮色主题"]')
        await page.waitForFunction(() => !document.documentElement.classList.contains('dark'))
        await pause(650)
      }
      await setCollapsed(true)
      await page.click('#pc-sidebar nav button[aria-label="知识图谱"]')
      await page.waitForFunction(() => [...document.querySelectorAll('.pc-main h2')].some(el => el.textContent === '知识图谱'))
      const bounds = await page.evaluate(() => {
        const heading = [...document.querySelectorAll('.pc-main h2')].find(el => el.textContent === '知识图谱')
        const header = heading.closest('div.border-b').getBoundingClientRect()
        return { header: { left: header.left, right: header.right }, sidebarRight: document.querySelector('#pc-sidebar').getBoundingClientRect().right, viewport: innerWidth }
      })
      assert.ok(Math.abs(bounds.header.left - bounds.sidebarRight) < 1, `知识上栏左边缘应贴侧栏: ${JSON.stringify(bounds)}`)
      assert.ok(Math.abs(bounds.header.right - bounds.viewport) < 1, '知识上栏右边缘应贴浏览器')
      await page.waitForFunction(() => document.querySelectorAll('.pc-knowledge-scroll article').length === 18)
      async function assertPageScroll(selector) {
        await page.waitForFunction(selector => Number(getComputedStyle(document.querySelector(selector)).opacity) === 1, {}, selector)
        const scroll = await page.$eval(selector, el => ({ right: el.getBoundingClientRect().right, viewport: innerWidth, height: el.clientHeight, scrollHeight: el.scrollHeight, direction: getComputedStyle(el).direction }))
        assert.ok(Math.abs(scroll.right - scroll.viewport) < 1, '页面滚动容器必须贴浏览器右边缘')
        assert.ok(scroll.scrollHeight > scroll.height, `模拟长内容应能真实滚动: ${JSON.stringify(scroll)}`)
        assert.equal(scroll.direction, 'ltr', '不得将文字方向反转来移动滚动条')
        await page.$eval(selector, el => { el.scrollTop = 160 })
        assert.ok(await page.$eval(selector, el => el.scrollTop > 0), '页面应能实际向下滚动')
        await page.$eval(selector, el => { el.scrollTop = 0 })
      }
      await assertPageScroll('.pc-knowledge-scroll')
      await page.screenshot({ path: resolve(evidence, 'knowledge-full-width.png') })
      await setCollapsed(false)
      const expandedKnowledge = await dimensions()
      await setCollapsed(true)
      assertCentered(expandedKnowledge, await dimensions())
      await page.click('#pc-sidebar nav button[aria-label="数据中心"]')
      await page.waitForSelector('.pc-data-page canvas')
      await pause(300)
      await assertPageScroll('.pc-data-page')
      await page.screenshot({ path: resolve(evidence, 'data-center-soft-shadows.png') })
      await page.evaluate(() => {
        localStorage.setItem('user', JSON.stringify({ userId: 123, username: '侧栏测试', phoneNumber: '18888888888' }))
        window.dispatchEvent(new CustomEvent('leafquery-auth-changed'))
      })
      await page.setViewport({ width: 1600, height: 720 })
      try {
        await page.click('#pc-sidebar nav button[aria-label="系统设置"]')
        await page.waitForSelector('.pc-settings-hero')
        await assertPageScroll('.pc-settings-page')
        const nestedScroll = await page.$eval('.pc-settings-content', el => getComputedStyle(el).overflowY)
        assert.equal(nestedScroll, 'visible', '设置内容不应保留卡片内部的页面滚动条')
        await page.screenshot({ path: resolve(evidence, 'settings-soft-shadows.png') })
        await setCollapsed(false)
        const expandedSettings = await dimensions()
        await setCollapsed(true)
        assertCentered(expandedSettings, await dimensions())
      } finally {
        await page.evaluate(() => {
          localStorage.removeItem('user')
          window.dispatchEvent(new CustomEvent('leafquery-auth-changed'))
        })
        await page.setViewport({ width: 1600, height: 1000 })
        await page.click('#pc-sidebar nav button[aria-label="工作台"]')
        await page.waitForSelector('#pc-sidebar button[aria-label="登录系统"]')
      }
    })
    await t.test('收起时游客登录和已登录用户退出仍可操作', async () => {
      await page.click('#pc-sidebar button[aria-label="登录系统"]')
      await page.waitForSelector('input[placeholder="用户名 / 手机号"]')
      await page.evaluate(() => document.querySelector('input[placeholder="用户名 / 手机号"]').closest('.fixed').querySelector('.absolute').click())
      // Wait until the closing modal stops intercepting clicks on the sidebar.
      await page.waitForFunction(() => !document.querySelector('input[placeholder="用户名 / 手机号"]'))
      await page.evaluate(() => {
        localStorage.setItem('user', JSON.stringify({ userId: 123, username: '侧栏测试' }))
        window.dispatchEvent(new CustomEvent('leafquery-auth-changed'))
      })
      await page.waitForSelector('#pc-sidebar button[aria-label="退出登录"]')
      // Account replacement changes footer geometry; wait for its click target to settle.
      await page.locator('#pc-sidebar button[aria-label="退出登录"]').click()
      await page.waitForSelector('#pc-sidebar button[aria-label="登录系统"]')
    })
    await t.test('Logo 悬停显示展开标识，收起按钮位于侧栏内部', async () => {
      await setCollapsed(true)
      await page.hover('#pc-sidebar .pc-sidebar-brand-button')
      await page.waitForFunction(() => Number(getComputedStyle(document.querySelector('.pc-sidebar-open-icon')).opacity) === 1)
      assert.equal(await page.$eval('.pc-sidebar-tooltip', el => getComputedStyle(el).visibility), 'visible')
      assert.equal(await page.$eval('.pc-sidebar-tooltip', el => el.textContent), '打开边栏')
      await page.screenshot({ path: resolve(evidence, 'collapsed-logo-hover.png') })
      await page.click('#pc-sidebar button[aria-label="展开侧边栏"]')
      await setCollapsed(false)
      assert.equal(await page.$eval('.pc-sidebar-toggle', el => Boolean(el.closest('#pc-sidebar'))), true)
      assert.equal(await page.$('.pc-main .pc-sidebar-toggle'), null)
      await page.click('#pc-sidebar button[aria-label="返回 LeafQuery 介绍页"]')
      await page.waitForFunction(() => !document.querySelector('#pc-sidebar'))
      assert.equal(await page.$eval('.pc-intro .brand-copy', el => el.getAttribute('aria-hidden')), 'false')
      await enterWorkspace()
      assert.ok(Math.abs((await dimensions()).sidebar.width - 256) < 1)
      await setCollapsed(true)
    })
    await t.test('减少动态时取消宽度过渡', async () => {
      await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }])
      assert.equal(await page.$eval('#pc-sidebar', el => getComputedStyle(el).transitionDuration), '0s')
      await setCollapsed(false)
      const before = await dimensions()
      await setCollapsed(true)
      assertCentered(before, await dimensions())
    })
    assert.deepEqual(errors, [], '页面不应发生未捕获的运行错误')
  } finally {
    await browser?.close()
    await server.close()
  }
})
