import assert from 'node:assert/strict'
import { existsSync } from 'node:fs'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'
import { createServer } from 'vite'
import puppeteer from 'puppeteer-core'
import { respondWorkspaceApi } from './helpers/workspaceApiFixture.js'

const root = fileURLToPath(new URL('../', import.meta.url))
const executablePath = [process.env.BROWSER_EXECUTABLE_PATH, `${process.env.PROGRAMFILES}/Google/Chrome/Application/chrome.exe`, `${process.env['PROGRAMFILES(X86)']}/Microsoft/Edge/Application/msedge.exe`].find(path => path && existsSync(path))
const evidence = resolve(root, '../.leafquery-runtime/workspace-preview')

test('工作台记录动画与接口替身回归', { timeout: 120000 }, async t => {
  const external = process.env.PROMPT_TEST_BASE_URL
  const server = external ? null : await createServer({ root, server: { port: 0, open: false }, logLevel: 'error' })
  let browser
  try {
    await server?.listen()
    const base = external || server.resolvedUrls.local[0]
    browser = await puppeteer.launch({ headless: true, executablePath, ignoreDefaultArgs: ['--hide-scrollbars'] })
    const page = await browser.newPage()
    await mkdir(evidence, { recursive: true })
    await page.setViewport({ width: 1600, height: 1000 })
    await page.evaluateOnNewDocument(() => {
      localStorage.setItem('pc-theme', 'light')
      window.previewURLs = { created: [], revoked: [] }
      const create = URL.createObjectURL.bind(URL), revoke = URL.revokeObjectURL.bind(URL)
      URL.createObjectURL = file => { const url = create(file); window.previewURLs.created.push(url); return url }
      URL.revokeObjectURL = url => { window.previewURLs.revoked.push(url); revoke(url) }
    })
    await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'no-preference' }])
    const errors = []
    const calls = []
    page.on('pageerror', error => errors.push(error.message))
    await page.setRequestInterception(true)
    page.on('request', request => respondWorkspaceApi(request, calls))
    await page.goto(base, { waitUntil: 'networkidle0' })
    await page.evaluate(() => [...document.querySelectorAll('button')].find(button => button.textContent.trim() === '进入工作台').click())
    await page.waitForSelector('.workspace-records')
    await t.test('整体收起经过连续宽度帧而不是瞬间跳为窄按钮', async () => {
      const widths = await page.evaluate(async () => {
        const panel = document.querySelector('.workspace-records')
        const samples = [panel.getBoundingClientRect().width]
        document.querySelector('[aria-label="收起记录侧栏"]').click()
        await Promise.resolve()
        void panel.offsetWidth
        const transition = panel.getAnimations().find(animation => animation.transitionProperty === 'width')
        if (!transition) return samples
        transition.pause()
        const duration = transition.effect.getTiming().duration
        for (let time = 0; time <= duration; time += duration / 16) {
          transition.currentTime = time
          samples.push(panel.getBoundingClientRect().width)
        }
        transition.finish()
        return samples
      })
      const start = widths[0], end = widths.at(-1)
      const intermediate = new Set(widths.filter(width => width < start - 3 && width > end + 3).map(width => Math.round(width * 10) / 10))
      const maxJump = Math.max(...widths.slice(1).map((width, i) => Math.abs(width - widths[i])))
      console.log(JSON.stringify({ start, end, distinctIntermediate: intermediate.size, maxJump }))
      assert.ok(intermediate.size >= 4 && maxJump < Math.abs(start - end) * .5, '尺寸应连续变化，不能在几个固定宽度之间跳变')
      await page.click('[aria-label="展开记录侧栏"]')
      await page.waitForSelector('#workspace-records-content')
      await page.waitForFunction(() => document.querySelector('.workspace-records').getAnimations({ subtree: true }).every(animation => animation.playState !== 'running'))
    })
    await t.test('分支按内容高度立即开始收缩，不等待固定最大高度', async () => {
      const motion = await page.evaluate(async () => {
        const target = '#workspace-records-recognition'
        const initial = document.querySelector(target).getBoundingClientRect().height
        document.querySelector('[aria-controls="workspace-records-recognition"]').click()
        const start = performance.now()
        const frames = []
        while (performance.now() - start < 380) {
          await new Promise(requestAnimationFrame)
          frames.push({ t: performance.now() - start, height: document.querySelector(target)?.getBoundingClientRect().height || 0 })
        }
        return { initial, frames }
      })
      assert.ok(motion.frames.find(frame => frame.height < motion.initial - 2)?.t < 80, '分支应立即开始变化')
      assert.ok(new Set(motion.frames.filter(frame => frame.height > 1 && frame.height < motion.initial - 1).map(frame => Math.round(frame.height))).size >= 4)
      await page.click('[aria-controls="workspace-records-recognition"]')
      await page.waitForSelector('#workspace-records-recognition')
    })
    async function enterIdentifyAndOpenTypes() {
      await page.click('.prompt-mode-button:first-of-type')
      await page.waitForFunction(() => document.querySelector('.prompt-input').classList.contains('is-identify'))
      assert.equal(await page.$('.prompt-category-menu'), null, '切换识别模式不自动弹出类型')
      await page.click('.prompt-choose-categories')
    }
    const fill = value => page.$eval('.prompt-textarea', (el, value) => { el.value = value; el.dispatchEvent(new Event('input', { bubbles: true })) }, value)
    await t.test('询问发出展示用户消息、等待与模拟回答，并写入对话分支', async () => {
      await page.click('.prompt-collapsed')
      await fill('水稻叶子发黄怎么办？')
      await page.click('.prompt-action')
      await page.waitForSelector('.turn-pending')
      assert.ok((await page.$eval('.turn-user p', el => el.textContent)).includes('水稻叶子发黄'))
      await page.waitForFunction(() => document.querySelector('.turn-answer p')?.textContent.includes('模拟回答'))
      assert.equal(await page.$eval('[aria-controls="workspace-records-conversation"] .records-count', el => el.textContent), '1')
      assert.equal(await page.$('.workspace-welcome'), null)
      await fill('还需要补充哪些信息？')
      await page.click('.prompt-action')
      await page.waitForFunction(() => document.querySelectorAll('.turn-answer').length === 2)
      assert.equal(await page.$eval('[aria-controls="workspace-records-conversation"] .records-count', el => el.textContent), '1', '多轮询问属于同一条对话记录')
      await page.click('#workspace-records-conversation .records-list button')
      await page.waitForSelector('[data-turn-id="1"].is-selected')
      await page.screenshot({ path: resolve(evidence, 'ask-preview.png') })
    })
    await t.test('识别发出展示图片与类别、等待和模拟结果，记录可定位', async () => {
      await enterIdentifyAndOpenTypes()
      await page.waitForSelector('[role="menuitemcheckbox"]')
      await page.click('[role="menuitemcheckbox"]')
      await page.click('.prompt-category-footer button')
      await page.$eval('.prompt-file-input', input => {
        const files = new DataTransfer()
        files.items.add(new File(['<svg xmlns="http://www.w3.org/2000/svg" width="600" height="400"><rect width="600" height="400" fill="#bdd8bd"/><path d="M300 350Q60 100 490 40Q490 300 300 350" fill="#317653"/></svg>'], 'mock-leaf.svg', { type: 'image/svg+xml' }))
        input.files = files.files
        input.dispatchEvent(new Event('change', { bubbles: true }))
      })
      await page.waitForSelector('.prompt-thumbnail')
      await page.click('.prompt-action')
      await page.waitForSelector('[data-turn-id="3"] .turn-pending')
      assert.equal(await page.$('.prompt-follow-up-hint'), null, '识别等待时不提示追问')
      await page.waitForSelector('.turn-detection-card')
      await page.waitForSelector('.prompt-follow-up-hint')
      assert.equal(await page.$eval('[data-turn-id="3"] .turn-categories', el => el.textContent.trim()), '水稻')
      assert.equal(await page.$eval('[data-turn-id="3"] .turn-images img', el => el.alt), '识别图片')
      assert.equal(await page.$('[data-turn-id="3"] .turn-user > p'), null, '识别图片消息不添加默认指令文字')
      assert.ok((await page.$eval('.turn-detection-card', el => el.textContent)).includes('YOLO 置信度86%'))
      assert.equal(await page.$eval('[aria-controls="workspace-records-recognition"] .records-count', el => el.textContent), '1')
      assert.equal(await page.$eval('[aria-controls="workspace-records-conversation"] .records-count', el => el.textContent), '1')
      assert.equal(await page.$('.turn-answer'), null, '识别记录不显示之前询问上下文')
      const sketch = await page.evaluate(() => {
        const rect = selector => document.querySelector(selector).getBoundingClientRect()
        const card = document.querySelector('.turn-user--identify'), image = rect('.turn-user--identify .turn-images'), meta = rect('.turn-identify-meta'), button = rect('.turn-report-button'), summary = card.getBoundingClientRect(), result = document.querySelector('.turn-assistant--identify')
        const details = result.getBoundingClientRect(), frame = document.querySelector('.pc-workspace').getBoundingClientRect(), style = getComputedStyle(card)
        return { imageOnLeft: image.right < meta.left, reportInside: card.contains(document.querySelector('.turn-report-button')), reportRight: button.right > summary.left + summary.width * .8, noBrand: !result.querySelector('header') && !card.textContent.includes('LeafQuery'), detailsInside: card.contains(result) && details.left > image.right, flushTop: Math.abs(summary.top - frame.top) < 1, squareTop: style.borderTopLeftRadius === '0px' && style.borderTopRightRadius === '0px' }
      })
      assert.equal(sketch.imageOnLeft && sketch.reportInside && sketch.reportRight && sketch.noBrand, true, '识别卡为左图右信息、右下报告且无角色标题')
      assert.equal(sketch.detailsInside && sketch.flushTop && sketch.squareTop, true, '详细结果必须位于图片右侧同一卡片，上方直角且贴内容区上缘')
      const metadata = await page.evaluate(() => {
        const nodes = ['.turn-identify-title', '.turn-identify-status', '.turn-identify-meta .turn-categories'].map(selector => document.querySelector(selector).getBoundingClientRect())
        return nodes.map(rect => rect.top + rect.height / 2)
      })
      assert.ok(Math.max(...metadata) - Math.min(...metadata) < 1, '图片识别、状态、类别应在同一行')
      assert.equal(await page.$eval('.turn-user--identify', el => el.textContent.includes('mock-leaf.svg')), false)
      await page.click('#workspace-records-recognition .records-list button')
      await page.waitForSelector('[data-turn-id="3"].is-selected')
      assert.equal(await page.$('.prompt-inline-attachments'), null, '已发送的识别图片不能滞留或因点击记录重新回到输入框')
      await page.waitForFunction(() => document.querySelector('.workspace-turn').getAnimations().every(animation => animation.playState !== 'running'))
      await page.screenshot({ path: resolve(evidence, 'identify-preview.png') })
    })
    await t.test('识别报告按钮可生成模拟报告，等待与结果留在所属记录', async () => {
      await page.waitForSelector('.turn-report-button')
      assert.equal(await page.$eval('.turn-report-button', el => el.textContent.includes('生成诊断报告')), true)
      const summaryHeight = await page.$eval('.turn-user--identify', el => el.getBoundingClientRect().height)
      await page.click('.turn-report-button')
      await page.waitForSelector('.turn-report-message[aria-busy="true"]')
      assert.equal(await page.$eval('.turn-report-message', el => el.parentElement.querySelector('.turn-user--identify').contains(el)), false, '报告等待也应位于卡片外')
      assert.equal(await page.$eval('.turn-report-button', el => el.disabled), true)
      await page.click('#workspace-records-conversation .records-list button')
      await page.waitForFunction(() => document.querySelectorAll('.turn-answer').length === 2)
      await new Promise(resolve => setTimeout(resolve, 750))
      assert.equal(await page.$('.turn-report'), null, '报告不能串到询问记录')
      assert.equal(await page.$('.turn-report-button'), null)
      await page.click('#workspace-records-recognition .records-list button')
      await page.waitForSelector('.turn-report')
      assert.equal(await page.$$eval('.turn-report', els => els.length), 1)
      const reportLayout = await page.evaluate(() => {
        const card = document.querySelector('.turn-user--identify'), report = document.querySelector('.turn-report-message')
        return { outside: !card.contains(report), below: report.getBoundingClientRect().top >= card.getBoundingClientRect().bottom, height: card.getBoundingClientRect().height, role: report.querySelector('header').textContent }
      })
      assert.ok(reportLayout.outside && reportLayout.below && reportLayout.role.includes('LeafQuery'))
      assert.ok(Math.abs(reportLayout.height - summaryHeight) < 1, '报告不能撑高识别卡')
      assert.ok((await page.$eval('.turn-report', el => el.textContent)).includes('固定模拟内容'))
      assert.equal(await page.$eval('[aria-controls="workspace-records-recognition"] .records-count', el => el.textContent), '1')
      assert.ok((await page.$eval('.turn-report-button', el => el.textContent)).includes('查看诊断报告'))
      await page.waitForFunction(() => document.querySelector('.workspace-turn').getAnimations().every(animation => animation.playState !== 'running'))
      await page.screenshot({ path: resolve(evidence, 'mock-report.png') })
    })
    await t.test('诊断卡滚动时固定，收起释放空间并保留报告及记录状态', async () => {
      await page.setViewport({ width: 1280, height: 720 })
      await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
      await page.$eval('.workspace-output', el => { el.style.scrollBehavior = 'auto'; el.scrollTop = 0 })
      const before = await page.$eval('.turn-user--identify', el => el.getBoundingClientRect().top)
      await page.$eval('.workspace-output', el => { el.scrollTop = 120 })
      const pinned = await page.evaluate(() => ({ top: document.querySelector('.turn-user--identify').getBoundingClientRect().top, scroll: document.querySelector('.workspace-output').scrollTop }))
      assert.ok(pinned.scroll > 30 && Math.abs(pinned.top - before) < 1, '滚动报告时诊断卡保持在顶端')
      const expanded = await page.$eval('.turn-user--identify', el => el.getBoundingClientRect().height)
      await page.click('[aria-label="收起识别卡片"]')
      await page.waitForSelector('.is-summary-collapsed')
      const motion = await page.$eval('.turn-user--identify', card => {
        const animation = card.getAnimations().find(item => item.playState === 'running')
        if (!animation) return []
        animation.pause()
        const frames = [], duration = animation.effect.getTiming().duration
        for (let i = 0; i <= 16; i++) {
          animation.currentTime = duration * i / 16
          frames.push(card.getBoundingClientRect().height)
        }
        animation.finish()
        return frames
      })
      assert.ok(new Set(motion.filter(height => height < motion[0] - 2 && height > motion.at(-1) + 2).map(Math.round)).size >= 4, '收起必须有多个连续高度中间帧')
      const collapsed = await page.$eval('.turn-user--identify', el => el.getBoundingClientRect().height)
      assert.ok(collapsed < expanded - 50)
      assert.equal(await page.$eval('.turn-assistant--identify', el => getComputedStyle(el).display), 'none')
      assert.ok(await page.$('.turn-report'))
      await page.screenshot({ path: resolve(evidence, 'diagnosis-collapsed.png') })
      await page.click('#workspace-records-conversation .records-list button')
      await page.waitForSelector('.turn-answer')
      await page.click('#workspace-records-recognition .records-list button')
      await page.waitForSelector('.is-summary-collapsed')
      await page.click('[aria-label="展开识别卡片"]')
      assert.equal(await page.$eval('.turn-collapse-button', el => el.getAttribute('aria-expanded')), 'true')
      await page.waitForFunction(() => document.querySelector('.turn-user--identify').getAnimations().every(animation => animation.playState !== 'running'))
      assert.ok(Math.abs(await page.$eval('.turn-user--identify', el => el.getBoundingClientRect().height) - expanded) < 1)
      await page.waitForFunction(() => !document.querySelector('.workspace-turn.is-selected') && document.querySelector('.workspace-turn').getAnimations().every(animation => animation.playState !== 'running'))
      await page.$eval('.workspace-output', el => { el.style.scrollBehavior = 'auto'; el.scrollTop = 120 })
      await page.screenshot({ path: resolve(evidence, 'diagnosis-pinned-report.png') })
      await page.evaluate(async () => {
        for (let i = 0; i < 4; i++) {
          document.querySelector('.turn-collapse-button').click()
          await new Promise(resolve => setTimeout(resolve, 45))
        }
      })
      await page.waitForFunction(() => document.querySelector('.turn-user--identify').getAnimations().every(animation => animation.playState !== 'running'))
      assert.equal(await page.$('.is-summary-collapsed'), null, '快速反向点击后应展开且没有残留高度')
      assert.ok(Math.abs(await page.$eval('.turn-user--identify', el => el.getBoundingClientRect().height) - expanded) < 1)
      await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }])
      await page.click('.turn-collapse-button')
      assert.equal(await page.$eval('.turn-user--identify', el => el.getAnimations().length), 0)
      await page.click('.turn-collapse-button')
      await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'no-preference' }])
      await page.setViewport({ width: 1600, height: 1000 })
    })
    await t.test('记录切换只显示各自上下文，新建对话与原多轮对话分离', async () => {
      await page.click('#workspace-records-conversation .records-list button')
      await page.waitForFunction(() => document.querySelectorAll('.turn-answer').length === 2)
      assert.equal(await page.$('.turn-detection-card'), null)
      assert.equal(await page.$eval('.prompt-textarea', el => el.value), '')
      await page.click('[aria-label="新建对话"]')
      await page.waitForSelector('.workspace-welcome')
      assert.equal(await page.$('.workspace-output'), null)
      await fill('另一条独立上下文')
      await page.click('.prompt-action')
      await page.waitForSelector('.turn-answer')
      assert.equal(await page.$$eval('.turn-answer', els => els.length), 1)
      assert.equal(await page.$eval('.turn-user p', el => el.textContent), '另一条独立上下文')
      assert.equal(await page.$eval('[aria-controls="workspace-records-conversation"] .records-count', el => el.textContent), '2')
      await page.click('#workspace-records-conversation .records-list li:first-child button')
      await page.waitForFunction(() => document.querySelectorAll('.turn-answer').length === 2)
      assert.equal(await page.$$eval('.turn-user p', els => els.some(el => el.textContent === '另一条独立上下文')), false)
      await fill('原会话继续追问')
      await page.click('.prompt-action')
      await page.waitForFunction(() => document.querySelectorAll('.turn-answer').length === 3)
      assert.equal(await page.$eval('[aria-controls="workspace-records-conversation"] .records-count', el => el.textContent), '2')
      await page.click('#workspace-records-recognition .records-list button')
      await page.waitForSelector('.turn-detection-card')
      assert.equal(await page.$$eval('.workspace-turn', els => els.length), 1)
      assert.equal(await page.$('.turn-answer'), null)
      await page.click('[aria-label="新建对话"]')
      await page.waitForSelector('.workspace-welcome')
      await fill('等待时切换记录，结果不能串到识别中')
      await page.click('.prompt-action')
      await page.waitForSelector('.turn-pending')
      await page.click('#workspace-records-recognition .records-list button')
      await page.waitForSelector('.turn-detection-card')
      await new Promise(resolve => setTimeout(resolve, 950))
      assert.equal(await page.$('.turn-answer'), null, '后台模拟完成不能串入当前识别记录')
      await page.click('#workspace-records-conversation .records-list li:last-child button')
      await page.waitForSelector('.turn-answer')
      assert.equal(await page.$eval('.turn-user p', el => el.textContent), '等待时切换记录，结果不能串到识别中')
      assert.equal(await page.$$eval('.workspace-turn', els => els.length), 1)
      await page.click('#workspace-records-recognition .records-list button')
      await page.waitForSelector('.turn-detection-card')
      await page.screenshot({ path: resolve(evidence, 'independent-context.png') })
    })
    const waitForLayout = () => page.waitForFunction(() => [...document.querySelector('.workspace-output').getAnimations(), ...document.querySelector('.workspace-records').getAnimations({ subtree: true })].every(animation => animation.playState !== 'running'))
    const centers = () => page.evaluate(() => {
      const rect = selector => { const r = document.querySelector(selector).getBoundingClientRect(); return { center: r.left + r.width / 2, width: r.width } }
      return { content: rect('.workspace-conversation'), input: rect('.prompt-card') }
    })
    await t.test('收起记录时内容与输入框居中，展开记录后内容左移', async () => {
      await waitForLayout()
      const opened = await centers()
      assert.ok(opened.content.center < opened.input.center - 50)
      assert.ok(opened.content.width > 736 && opened.content.width <= 880, '宽屏对话正文应比旧版更宽且限制在 880px')
      await page.click('.records-toggle')
      await waitForLayout()
      const closed = await centers()
      assert.ok(Math.abs(closed.content.center - closed.input.center) < 1, '收起记录后内容与输入框中心对齐')
      assert.ok(Math.abs(closed.input.center - opened.input.center) < 1 && Math.abs(closed.input.width - opened.input.width) < 1, '输入框位置和宽度不变')
      await page.screenshot({ path: resolve(evidence, 'records-closed-centered.png') })
      await page.click('.records-toggle')
      await waitForLayout()
      const restored = await centers()
      assert.ok(Math.abs(restored.content.center - opened.content.center) < 1)
      await page.screenshot({ path: resolve(evidence, 'records-open-shifted.png') })
    })
    await t.test('连续反向切换、深浅主题、减少动态与短屏布局正常', async () => {
      await page.evaluate(async () => {
        for (let i = 0; i < 4; i++) {
          document.querySelector('.records-toggle').click()
          await new Promise(resolve => setTimeout(resolve, 40))
        }
      })
      await page.waitForFunction(() => document.querySelector('.workspace-records').getAnimations({ subtree: true }).every(animation => animation.playState !== 'running'))
      assert.equal(await page.$eval('.records-toggle', el => el.getAttribute('aria-expanded')), 'true')
      await page.click('button[aria-label="切换到暗色主题"]')
      await page.waitForFunction(() => document.documentElement.classList.contains('dark'))
      await page.screenshot({ path: resolve(evidence, 'dark-preview.png') })
      for (const [width, height] of [[1280, 720], [1024, 600]]) {
        await page.setViewport({ width, height })
        await page.waitForFunction(() => [...document.querySelector('.prompt-input').getAnimations(), ...document.querySelector('.workspace-records').getAnimations()].every(animation => animation.playState !== 'running'))
        await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))))
        assert.ok(await page.$eval('.workspace-output', el => el.clientHeight > 50 && el.scrollHeight > el.clientHeight))
        const overlaps = await page.evaluate(() => {
          const output = document.querySelector('.workspace-output').getBoundingClientRect(), input = document.querySelector('.prompt-input').getBoundingClientRect(), records = document.querySelector('.workspace-records').getBoundingClientRect()
          return output.bottom > input.top || output.right > records.left
        })
        assert.equal(overlaps, false)
        const scrollbar = await page.evaluate(() => {
          const viewport = document.querySelector('.workspace-output'), content = document.querySelector('.workspace-conversation')
          const frame = document.querySelector('.pc-workspace').getBoundingClientRect()
          const icon = document.querySelector('.turn-assistant header img').getBoundingClientRect(), label = document.querySelector('.turn-assistant header span').getBoundingClientRect()
          return { gutter: viewport.offsetWidth - viewport.clientWidth, leftGutter: viewport.clientLeft, leftGap: viewport.getBoundingClientRect().left - frame.left, direction: getComputedStyle(content).direction, correctHeaderOrder: icon.left < label.left }
        })
        assert.ok(scrollbar.gutter > 0 && Math.abs(scrollbar.leftGutter - scrollbar.gutter) < 1, '原生滚动槽应位于左边')
        assert.equal(scrollbar.leftGap, 8, '滚动条靠近工作台左侧导航')
        assert.equal(scrollbar.direction, 'ltr')
        assert.equal(scrollbar.correctHeaderOrder, true, '头像与文字顺序不能反转')
        await page.$eval('.workspace-output', el => { el.style.scrollBehavior = 'auto'; el.scrollTop = 0 })
        const wheelPoint = await page.$eval('.workspace-output', el => { const r = el.getBoundingClientRect(); return { x: r.left + 15, y: r.bottom - 20 } })
        await page.mouse.move(wheelPoint.x, wheelPoint.y)
        await page.mouse.wheel({ deltaY: 180 })
        await page.waitForFunction(() => document.querySelector('.workspace-output').scrollTop > 0)
        await page.screenshot({ path: resolve(evidence, `left-scrollbar-${width}.png`) })
        await page.click('.records-toggle')
        await waitForLayout()
        const centered = await centers()
        assert.ok(Math.abs(centered.content.center - centered.input.center) < 1)
        await page.click('.records-toggle')
        await waitForLayout()
      }
      await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }])
      await page.click('.records-toggle')
      assert.equal(await page.$eval('.workspace-records', el => getComputedStyle(el).transitionDuration), '0s')
      await page.click('.records-toggle')
    })
    await t.test('识别后手绘箭头引导询问，报告与多轮追问保持同一记录', async () => {
      await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'no-preference' }])
      await page.setViewport({ width: 1280, height: 720 })
      await page.waitForSelector('.prompt-follow-up-hint')
      const arrow = await page.evaluate(() => {
        const hint = document.querySelector('.prompt-follow-up-hint'), svg = hint.querySelector('svg').getBoundingClientRect(), button = document.querySelector('.prompt-mode-button:last-of-type').getBoundingClientRect()
        return { target: svg.left > button.right && svg.left - button.right < 20 && svg.top < button.bottom && svg.bottom > button.top, text: hint.textContent, passive: getComputedStyle(hint).pointerEvents }
      })
      assert.ok(arrow.target && arrow.text.includes('可以继续询问'))
      assert.equal(arrow.passive, 'none')
      await page.waitForFunction(() => document.querySelector('.prompt-follow-up-hint').getAnimations({ subtree: true }).every(animation => animation.playState !== 'running'))
      await page.screenshot({ path: resolve(evidence, 'follow-up-hint.png') })
      const counts = await page.$$eval('.records-count', els => els.map(el => el.textContent))
      await page.click('.prompt-mode-button:last-of-type')
      await page.waitForSelector('.prompt-follow-up-hint', { hidden: true })
      await fill('本次识别之后应该如何处理？')
      await page.click('.prompt-action')
      await page.waitForFunction(() => document.querySelector('.turn-answer p')?.textContent.includes('本次水稻识别'))
      await fill('还需要补拍哪些细节？')
      await page.click('.prompt-action')
      await page.waitForFunction(() => document.querySelectorAll('.turn-answer').length === 2)
      assert.deepEqual(await page.$$eval('.records-count', els => els.map(el => el.textContent)), counts, '追问不能新建独立对话记录')
      assert.equal(await page.$$eval('.turn-report-message', els => els.length), 1)
      assert.equal(await page.$$eval('.turn-detection-card', els => els.length), 1)
      assert.equal(await page.$$eval('.turn-user > p', els => els.map(el => el.textContent).includes('还需要补拍哪些细节？')), true)
      await page.$eval('.workspace-output', el => { el.style.scrollBehavior = 'auto'; el.scrollTop = el.scrollHeight })
      const cardTop = await page.$eval('.turn-user--identify', el => el.getBoundingClientRect().top)
      const viewportTop = await page.$eval('.workspace-output', el => el.getBoundingClientRect().top)
      assert.ok(Math.abs(cardTop - viewportTop) < 1, '追问滑动到底时诊断卡仍固定在顶端')
      await page.screenshot({ path: resolve(evidence, 'identification-follow-up.png') })
      await page.click('#workspace-records-conversation .records-list li:first-child button')
      await page.waitForFunction(() => !document.querySelector('.turn-detection-card'))
      assert.equal(await page.$$eval('.turn-user > p', els => els.some(el => el.textContent.includes('本次识别之后'))), false)
      await page.click('#workspace-records-recognition .records-list button')
      await page.waitForFunction(() => document.querySelectorAll('.turn-answer').length === 2)
      assert.ok(await page.$('.turn-report-message'))
    })
    await t.test('离开工作台清理模拟图片和等待任务，测试不访问外部模型', async () => {
      await page.click('.prompt-mode-button:last-of-type')
      await fill('离开时仍在等待的模拟询问')
      await page.click('.prompt-action')
      await page.waitForSelector('.turn-pending')
      await page.click('#pc-sidebar nav button[aria-label="系统设置"]')
      await page.waitForFunction(() => !document.querySelector('.workspace-output'))
      const urls = await page.evaluate(() => window.previewURLs)
      assert.deepEqual([...urls.created].sort(), [...urls.revoked].sort())
      await page.click('#pc-sidebar nav button[aria-label="工作台"]')
      await page.waitForSelector('.workspace-welcome')
      await new Promise(resolve => setTimeout(resolve, 950))
      assert.equal(await page.$('.workspace-output'), null)
    })
    assert.deepEqual(errors, [])
    assert.ok(calls.includes('/api/pest/identify') && calls.includes('/api/pest/diagnose') && calls.includes('/api/ai/chat'))
  } finally { await browser?.close(); await server?.close() }
})
