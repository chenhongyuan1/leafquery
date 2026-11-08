import assert from 'node:assert/strict'
import { existsSync } from 'node:fs'
import { mkdir } from 'node:fs/promises'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import test from 'node:test'
import { createServer } from 'vite'
import puppeteer from 'puppeteer-core'
import { respondWorkspaceApi } from './helpers/workspaceApiFixture.js'

const root = fileURLToPath(new URL('../', import.meta.url))
const evidence = resolve(root, '..', '.leafquery-runtime', 'pc-prompt-input')
// Set this to an existing local Vite server to catch stale style-module caches.
const runningBase = process.env.PROMPT_TEST_BASE_URL
const browserPath = [process.env.BROWSER_EXECUTABLE_PATH,
  `${process.env.PROGRAMFILES}/Google/Chrome/Application/chrome.exe`,
  `${process.env['PROGRAMFILES(X86)']}/Microsoft/Edge/Application/msedge.exe`,
  '/usr/bin/google-chrome', '/usr/bin/chromium'
].find(path => path && existsSync(path))
const pause = ms => new Promise(resolve => setTimeout(resolve, ms))

test('PC 工作台仅呈现工作台输入组件', { timeout: 120000 }, async t => {
  assert.ok(browserPath, '需要 Chrome/Edge 或 BROWSER_EXECUTABLE_PATH')
  const server = runningBase ? null : await createServer({ root, server: { port: 0, open: false }, logLevel: 'error' })
  let browser
  try {
    await mkdir(evidence, { recursive: true })
    await server?.listen()
    const base = runningBase || server.resolvedUrls.local[0]
    const moduleText = await (await fetch(new URL('/src/views/pc/WorkspaceView.vue', base))).text()
    const stylePath = moduleText.match(/import "([^"]*type=style[^"]*)"/)?.[1]
    assert.ok(stylePath, '工作台模块应引用定位样式')
    const styleText = await (await fetch(new URL(stylePath, base))).text()
    assert.ok(styleText.includes('.pc-workspace['), '运行服务必须返回新定位样式，不能残留旧工作台 CSS')
    browser = await puppeteer.launch({ headless: true, executablePath: browserPath })
    const page = await browser.newPage()
    const errors = []
    const businessCalls = []
    page.on('pageerror', error => errors.push(error.message))
    await page.setViewport({ width: 1600, height: 1000 })
    await page.evaluateOnNewDocument(() => {
      localStorage.setItem('pc-theme', 'light')
      window.previewURLs = { created: [], revoked: [] }
      const create = URL.createObjectURL.bind(URL)
      const revoke = URL.revokeObjectURL.bind(URL)
      URL.createObjectURL = file => { const url = create(file); window.previewURLs.created.push(url); return url }
      URL.revokeObjectURL = url => { window.previewURLs.revoked.push(url); revoke(url) }
      navigator.mediaDevices.getUserMedia = () => { window.micRequested = true; throw new Error('前端预览不应访问麦克风') }
    })
    await page.setRequestInterception(true)
    page.on('request', request => respondWorkspaceApi(request, businessCalls))
    await page.goto(base, { waitUntil: 'networkidle0' })
    await page.evaluate(() => [...document.querySelectorAll('button')].find(button => button.textContent.trim() === '进入工作台').click())
    await page.waitForSelector('.prompt-input')

    async function enterIdentifyAndOpenTypes() {
      await page.click('.prompt-mode-button:first-of-type')
      await page.waitForFunction(() => document.querySelector('.prompt-input').classList.contains('is-identify'))
      assert.equal(await page.$('.prompt-category-menu'), null, '切换识别模式不自动弹出类型')
      await page.click('.prompt-choose-categories')
    }
    const fill = async value => {
      await page.$eval('.prompt-textarea', (el, value) => { el.value = value; el.dispatchEvent(new Event('input', { bubbles: true })) }, value)
      await pause(100)
    }
    const clickBackground = async () => {
      const point = await page.$eval('[data-prompt-collapse-area]', el => { const r = el.getBoundingClientRect(); return { x: r.x + 8, y: r.y + 8 } })
      await page.mouse.click(point.x, point.y)
    }
    const attach = async count => {
      await page.$eval('.prompt-file-input', (input, count) => {
        const files = new DataTransfer()
        for (let i = 0; i < count; i++) files.items.add(new File(['<svg xmlns="http://www.w3.org/2000/svg" width="600" height="400"><rect width="600" height="400" fill="#7fb8a0"/><path d="M300 320Q100 110 430 60Q500 240 300 320" fill="#17614c"/></svg>'], `leaf-${i}.svg`, { type: 'image/svg+xml' }))
        input.files = files.files
        input.dispatchEvent(new Event('change', { bubbles: true }))
      }, count)
      await pause(350)
    }

    await t.test('旧布局移除，输入框在内容区域底部居中', async () => {
      const facts = await page.$eval('.pc-workspace', page => {
        const frame = page.getBoundingClientRect()
        const input = page.querySelector('.prompt-card').getBoundingClientRect()
        return { text: page.textContent, children: page.children.length, x: input.x, width: input.width, y: input.y, bottom: input.bottom, frameX: frame.x, frameWidth: frame.width, frameBottom: frame.bottom }
      })
      assert.equal(facts.children, 3)
      for (const oldText of ['智能诊断工作台', '选择检测范围', '工作台等待接入', '生成 AI 诊断']) assert.ok(!facts.text.includes(oldText))
      assert.ok(Math.abs(facts.x + facts.width / 2 - facts.frameX - facts.frameWidth / 2) < 1)
      assert.ok(facts.y > 700 && facts.frameBottom - facts.bottom >= 70)
      assert.equal(await page.$eval('.workspace-welcome h1', el => el.textContent), 'LeafQuery')
      assert.equal(await page.$eval('.workspace-welcome p', el => el.textContent), '准备好识别询问了吗？')
      assert.equal(await page.evaluate(() => document.querySelector('.workspace-welcome img').src === document.querySelector('#pc-sidebar .brand-icon img').src), true, '欢迎区复用侧栏同一个 Logo')
      await page.waitForFunction(() => document.querySelector('.workspace-welcome img').naturalWidth > 0)
      await page.screenshot({ path: resolve(evidence, 'workspace-welcome-light.png') })
      await page.screenshot({ path: resolve(evidence, 'light-collapsed.png') })
      await page.click('.prompt-collapsed')
      await page.waitForFunction(() => document.activeElement === document.querySelector('.prompt-textarea'))
      await pause(450)
      assert.ok(await page.$eval('.prompt-card', el => el.getBoundingClientRect().height >= 140))
      assert.equal(await page.$$eval('.prompt-mode-button', els => els.map(el => el.textContent.trim()).join('/')), '识别/询问')
      assert.ok(await page.evaluate(() => document.querySelector('.prompt-mode-row').getBoundingClientRect().bottom < document.querySelector('.prompt-card').getBoundingClientRect().top))
    })

    await t.test('欢迎内容在输入和无效识别时保留', async () => {
      await fill('欢迎内容不能在输入时消失')
      assert.ok(await page.$('.workspace-welcome'))
      await fill('')
      await enterIdentifyAndOpenTypes()
      await page.waitForSelector('.prompt-category-menu')
      await page.keyboard.press('Escape')
      await page.waitForFunction(() => !document.querySelector('.prompt-category-menu'))
      await page.click('.prompt-action')
      assert.ok((await page.$eval('.prompt-status', el => el.textContent)).includes('请先选择识别类别'))
      assert.equal(await page.$('.prompt-category-menu'), null, '发送校验也不自动打开类型菜单')
      assert.ok(await page.$('.workspace-welcome'))
      await page.click('.prompt-choose-categories')
      await page.waitForSelector('.prompt-category-menu')
      await page.click('[role="menuitemcheckbox"]')
      await page.click('.prompt-category-footer button')
      await page.waitForFunction(() => !document.querySelector('.prompt-category-menu'))
      await page.click('.prompt-action')
      assert.ok((await page.$eval('.prompt-status', el => el.textContent)).includes('请添加图片'))
      assert.ok(await page.$('.workspace-welcome'))
      await page.click('.prompt-category-pill button')
      await page.waitForFunction(() => !document.querySelector('.prompt-category-pill'))
      await pause(100)
      assert.equal(await page.$eval('.prompt-input', el => el.classList.contains('is-expanded')), true, '取消最后一个类别不能收缩输入框')
      await page.click('.prompt-mode-button:last-of-type')
      await page.waitForSelector('.prompt-model-button')
    })

    await t.test('失焦、Escape 和记录按钮不收缩，仅点击空白背景收缩', async () => {
      await page.focus('.prompt-textarea')
      await page.keyboard.press('Escape')
      assert.equal(await page.$('.prompt-collapsed'), null)
      await page.focus('[aria-label="收起记录侧栏"]')
      await pause(100)
      assert.equal(await page.$('.prompt-collapsed'), null, '焦点离开输入框不能收缩')
      await page.click('[aria-label="收起记录侧栏"]')
      await page.waitForSelector('[aria-label="展开记录侧栏"]')
      assert.equal(await page.$('.prompt-collapsed'), null, '点击其他控件不能收缩')
      await page.click('[aria-label="展开记录侧栏"]')
      await page.waitForSelector('#workspace-records-content')
      await clickBackground()
      await page.waitForSelector('.prompt-collapsed')
      await page.click('.prompt-collapsed')
      await page.waitForFunction(() => document.activeElement === document.querySelector('.prompt-textarea'))
    })

    await t.test('右上记录侧栏提供两个独立分支，收起不挤动输入框', async () => {
      await fill('侧栏布局检查草稿')
      await page.waitForFunction(() => document.querySelector('.prompt-input').getAnimations({ subtree: true }).every(animation => animation.playState !== 'running'))
      const geometry = () => page.evaluate(() => {
        const rect = selector => { const r = document.querySelector(selector).getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, right: r.right, bottom: r.bottom } }
        return { frame: rect('.pc-workspace'), panel: rect('.workspace-records'), input: rect('.prompt-card') }
      })
      const before = await geometry()
      assert.ok(Math.abs(before.frame.right - before.panel.right - 24) < 1)
      assert.ok(Math.abs(before.panel.y - before.frame.y - 24) < 1)
      assert.equal(await page.$$eval('.records-branch-toggle', els => els.map(el => el.textContent.trim()).join('/')), '识别记录0/对话记录0')
      assert.equal(await page.$$eval('.records-empty', els => els.length), 2)
      await page.click('[aria-controls="workspace-records-recognition"]')
      await page.waitForFunction(() => !document.querySelector('#workspace-records-recognition'))
      assert.ok(await page.$('#workspace-records-conversation'))
      await page.click('[aria-label="收起记录侧栏"]')
      await page.waitForFunction(() => !document.querySelector('#workspace-records-content'))
      assert.ok(await page.$('[aria-label="展开记录侧栏"]'))
      const after = await geometry()
      assert.ok(Math.abs(before.input.x - after.input.x) < 1 && Math.abs(before.input.y - after.input.y) < 1 && Math.abs(before.input.width - after.input.width) < 1, '记录侧栏不能挤动输入框')
      await page.focus('[aria-label="展开记录侧栏"]')
      await page.keyboard.press('Enter')
      await page.waitForSelector('#workspace-records-content')
      await page.waitForFunction(() => document.querySelector('.workspace-records').getAnimations({ subtree: true }).every(animation => animation.playState !== 'running'))
      assert.equal(await page.$eval('[aria-controls="workspace-records-recognition"]', el => el.getAttribute('aria-expanded')), 'false')
      await page.click('[aria-controls="workspace-records-recognition"]')
      await page.waitForSelector('#workspace-records-recognition')
      await page.click('[aria-controls="workspace-records-conversation"]')
      await page.waitForFunction(() => !document.querySelector('#workspace-records-conversation'))
      await page.click('[aria-controls="workspace-records-conversation"]')
      await page.waitForSelector('#workspace-records-conversation')
      await pause(250)
      await page.screenshot({ path: resolve(evidence, 'workspace-records-light.png') })
      await fill('')
    })

    await t.test('模型菜单键盘导航与思考强度切换', async () => {
      await page.click('.prompt-model-button')
      await page.waitForSelector('[role="menu"]')
      await pause(280)
      assert.equal(await page.evaluate(() => {
        const menu = document.querySelector('.prompt-model-menu'), modes = document.querySelector('.prompt-mode-switch')
        const a = menu.getBoundingClientRect(), b = modes.getBoundingClientRect()
        const left = Math.max(a.left, b.left), right = Math.min(a.right, b.right), top = Math.max(a.top, b.top), bottom = Math.min(a.bottom, b.bottom)
        return right > left && bottom > top && menu.contains(document.elementFromPoint((left + right) / 2, (top + bottom) / 2))
      }), true, '模型菜单必须覆盖模式按钮并能接收点击')
      assert.equal(await page.$$eval('[role="menuitemradio"]', els => els.length), 5)
      await page.keyboard.press('ArrowDown')
      await page.keyboard.press('Enter')
      await page.waitForFunction(() => document.querySelector('.prompt-model-button').textContent.includes('Opus 4.8'))
      await page.click('.prompt-effort')
      assert.ok((await page.$eval('.prompt-effort', el => el.getAttribute('aria-label'))).includes('高'))
      await page.click('.prompt-model-button')
      await page.keyboard.press('Escape')
      assert.equal(await page.$eval('.prompt-model-button', el => el.getAttribute('aria-expanded')), 'false')
    })

    await t.test('中文合成不误发送，Shift+Enter 换行，发送保留草稿并调用问答接口', async () => {
      await fill('水稻叶片有斑点')
      await page.$eval('.prompt-textarea', el => {
        el.dispatchEvent(new CompositionEvent('compositionstart', { bubbles: true }))
        el.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', isComposing: true, bubbles: true }))
      })
      assert.equal(await page.$eval('.prompt-status', el => el.textContent), '')
      await page.$eval('.prompt-textarea', el => el.dispatchEvent(new CompositionEvent('compositionend', { bubbles: true })))
      await page.focus('.prompt-textarea')
      await page.keyboard.down('Shift'); await page.keyboard.press('Enter'); await page.keyboard.up('Shift')
      assert.ok((await page.$eval('.prompt-textarea', el => el.value)).includes('\n'))
      await page.keyboard.press('Enter')
      await page.waitForFunction(() => document.querySelector('.turn-answer'))
      await page.waitForFunction(() => !document.querySelector('.workspace-welcome'))
      assert.ok((await page.$eval('.prompt-textarea', el => el.value)).includes('水稻叶片'))
      assert.ok(businessCalls.every(path => ['/api/pest/identify', '/api/ai/chat'].includes(path)))
      await fill('')
      assert.equal(await page.$('.workspace-welcome'), null, '清空输入不能重新显示欢迎内容')
      await page.click('.prompt-action')
      await page.waitForFunction(() => document.querySelector('.workspace-notice')?.textContent.includes('前端预览不应访问麦克风'))
      assert.equal(await page.evaluate(() => Boolean(window.micRequested)), true)
    })

    await t.test('识别模式显示移动端五项类别，隐藏模型、强度及语音', async () => {
      await fill('保留这段询问草稿')
      await enterIdentifyAndOpenTypes()
      await page.waitForSelector('.prompt-category-menu')
      assert.deepEqual(await page.$$eval('[role="menuitemcheckbox"]', els => els.map(el => el.textContent.trim())), ['🌾水稻', '🌽玉米', '🌾小麦', '📋其他', '🐛虫害'])
      assert.equal(await page.$eval('.prompt-textarea', el => el.placeholder), '请添加图片')
      assert.equal(await page.$('.prompt-model-button'), null)
      assert.equal(await page.$('.prompt-effort'), null)
      assert.equal(await page.$('button[aria-label="语音输入"]'), null)
      assert.equal(await page.$eval('.prompt-action', el => el.getAttribute('aria-label')), '开始识别')
      await page.keyboard.press('Space')
      await page.keyboard.press('ArrowRight')
      await page.keyboard.press('Space')
      await page.keyboard.press('End')
      await page.keyboard.press('Space')
      await pause(300)
      assert.equal(await page.$$eval('.prompt-category-pill', els => els.length), 3)
      await page.screenshot({ path: resolve(evidence, 'identify-category-menu.png') })
      await page.keyboard.press('Escape')
      await page.waitForFunction(() => !document.querySelector('.prompt-category-menu'))
      await page.click('.prompt-action')
      assert.ok((await page.$eval('.prompt-status', el => el.textContent)).includes('请添加图片'))
      assert.ok(businessCalls.every(path => ['/api/pest/identify', '/api/ai/chat'].includes(path)))
    })

    await t.test('类别胶囊悬停取消、重新多选和返回询问恢复原输入', async () => {
      await page.hover('.prompt-category-pill')
      await pause(200)
      assert.equal(await page.$eval('.prompt-category-pill button', el => getComputedStyle(el).opacity), '1')
      await page.click('.prompt-category-pill button')
      await page.waitForFunction(() => document.querySelectorAll('.prompt-category-pill').length === 2)
      await enterIdentifyAndOpenTypes()
      await page.waitForSelector('.prompt-category-menu')
      await page.$$eval('[role="menuitemcheckbox"]', buttons => buttons.filter(button => button.getAttribute('aria-checked') === 'false').forEach(button => button.click()))
      await page.click('.prompt-category-footer button')
      await page.waitForFunction(() => !document.querySelector('.prompt-category-menu'))
      assert.equal(await page.$$eval('.prompt-category-pill', els => els.length), 5)
      await page.click('.prompt-mode-button:last-of-type')
      await page.waitForSelector('.prompt-model-button')
      assert.equal(await page.$eval('.prompt-textarea', el => el.value), '保留这段询问草稿')
      assert.equal(await page.$eval('.prompt-textarea', el => el.readOnly), false)
      assert.ok((await page.$eval('.prompt-model-button', el => el.textContent)).includes('Opus 4.8'))
      assert.ok((await page.$eval('.prompt-effort', el => el.getAttribute('aria-label'))).includes('高'))
      await fill('')
      assert.equal(await page.$eval('.prompt-action', el => el.getAttribute('aria-label')), '语音输入')
      assert.equal(await page.$('.prompt-category-pill'), null)
    })

    await t.test('附件数量限制、图片放大、焦点恢复及地址释放', async () => {
      await attach(7)
      assert.equal(await page.$$eval('.prompt-thumbnail', els => els.length), 6)
      assert.equal(await page.$eval('.prompt-add', el => el.disabled), true)
      assert.ok((await page.$eval('.prompt-status', el => el.textContent)).includes('最多添加 6 张'))
      await page.click('.prompt-thumbnail-open')
      await page.waitForSelector('[role="dialog"]')
      await page.waitForFunction(() => document.querySelector('.prompt-gallery img').naturalWidth === 600)
      await page.keyboard.press('Tab')
      assert.equal(await page.$eval('.prompt-gallery-close', el => el === document.activeElement), true)
      await page.keyboard.press('Escape')
      await page.waitForFunction(() => !document.querySelector('[role="dialog"]'))
      assert.equal(await page.$eval('.prompt-thumbnail-open', el => el === document.activeElement), true)
      await page.click('.prompt-thumbnail-remove')
      await page.waitForFunction(() => document.querySelectorAll('.prompt-thumbnail').length === 5)
      assert.equal(await page.$$eval('.prompt-thumbnail', els => els.length), 5)
      assert.equal(await page.evaluate(() => window.previewURLs.revoked.length), 1)
      assert.equal(await page.$$eval('.prompt-thumbnail img', images => images.every(img => img.naturalWidth === 600)), true)
      await page.click('.prompt-action')
      assert.equal(await page.$$eval('.prompt-thumbnail', els => els.length), 5, '前端发送不能丢弃草稿附件')
      await page.screenshot({ path: resolve(evidence, 'light-expanded.png') })
      await fill('收起后保留的草稿')
      await clickBackground()
      await page.waitForSelector('.prompt-collapsed')
      await page.waitForFunction(() => !document.querySelector('.prompt-attachments'))
      assert.equal(await page.evaluate(() => window.previewURLs.revoked.length), 1, '收缩不能释放仍在草稿中的图片')
      await page.click('.prompt-collapsed')
      await page.waitForSelector('.prompt-attachments')
      assert.equal(await page.$$eval('.prompt-thumbnail', els => els.length), 5)
      assert.equal(await page.$eval('.prompt-textarea', el => el.value), '收起后保留的草稿')
      await fill('')
    })

    await t.test('识别提交清空图片并保留类别，切回询问后不丢失附件', async () => {
      await enterIdentifyAndOpenTypes()
      await page.waitForSelector('.prompt-category-menu')
      await page.click('.prompt-category-footer button')
      assert.equal(await page.$$eval('.prompt-category-pill', els => els.length), 5)
      assert.equal(await page.$$eval('.prompt-thumbnail', els => els.length), 0, '识别不能混入询问的多图草稿')
      await attach(2)
      assert.equal(await page.$$eval('.prompt-thumbnail', els => els.length), 1, '识别只接收一张图片')
      assert.equal(await page.$('.prompt-add'), null, '选图后加号消失')
      assert.equal(await page.$eval('.prompt-file-input', el => el.multiple), false)
      assert.equal(await page.evaluate(() => {
        const card = document.querySelector('.prompt-card'), tray = document.querySelector('.prompt-inline-attachments'), image = tray.querySelector('.prompt-thumbnail')
        const c = card.getBoundingClientRect(), i = image.getBoundingClientRect(), toolbar = document.querySelector('.prompt-toolbar').getBoundingClientRect()
        return card.contains(tray) && i.top >= c.top && i.bottom < toolbar.top && getComputedStyle(tray).backgroundColor === 'rgba(0, 0, 0, 0)' && getComputedStyle(tray).boxShadow === 'none'
      }), true, '图片应在白色卡片内部，去除外部托盘灰底和阴影')
      assert.equal(await page.$eval('.prompt-textarea', el => getComputedStyle(el).display), 'none')
      await page.click('.prompt-thumbnail-open')
      await page.waitForSelector('.prompt-gallery')
      await page.keyboard.press('Escape')
      await page.waitForFunction(() => !document.querySelector('.prompt-gallery'))
      await page.click('.prompt-thumbnail-remove')
      await page.waitForFunction(() => !document.querySelector('.prompt-inline-attachments'))
      await page.waitForSelector('.prompt-add')
      assert.equal(await page.$eval('.prompt-textarea', el => el.placeholder), '请添加图片')
      await attach(1)
      await page.click('.prompt-action')
      await page.waitForSelector('.turn-detection-card')
      assert.equal(await page.$$eval('.prompt-thumbnail', els => els.length), 0)
      assert.equal(await page.$$eval('.prompt-category-pill', els => els.length), 5)
      await page.screenshot({ path: resolve(evidence, 'identify-selected.png') })
      await page.click('.prompt-mode-button:last-of-type')
      await page.waitForSelector('.prompt-model-button')
      assert.equal(await page.$$eval('.prompt-thumbnail', els => els.length), 5)
      assert.equal(await page.$eval('.prompt-file-input', el => el.multiple), true)
      assert.ok(businessCalls.every(path => ['/api/pest/identify', '/api/ai/chat'].includes(path)))
    })

    await t.test('现有主题切换、侧栏收起及桌面尺寸均无溢出', async () => {
      await page.click('button[aria-label="切换到暗色主题"]')
      await page.waitForFunction(() => document.documentElement.classList.contains('dark'))
      await pause(650)
      assert.equal(await page.$eval('.prompt-card', el => getComputedStyle(el).backgroundColor), 'rgb(27, 37, 37)')
      assert.equal(await page.$eval('.workspace-records', el => getComputedStyle(el).backgroundColor), 'rgb(27, 37, 37)')
      await page.screenshot({ path: resolve(evidence, 'dark-expanded.png') })
      await page.click('.pc-sidebar-toggle')
      await pause(350)
      for (const [width, height] of [[2549, 1403], [1280, 720], [1024, 600]]) {
        await page.setViewport({ width, height })
        await fill('水稻叶片问题\n'.repeat(20))
        await page.click('.prompt-model-button')
        await pause(450)
        const geometry = await page.evaluate(() => {
          const rect = selector => { const r = document.querySelector(selector).getBoundingClientRect(); return { x: r.x, right: r.right, top: r.top, bottom: r.bottom } }
          return { card: rect('.prompt-card'), menu: rect('.prompt-model-menu'), scrollWidth: document.documentElement.scrollWidth }
        })
        assert.ok(geometry.card.x >= 0 && geometry.card.right <= width && geometry.card.bottom < height)
        assert.ok(geometry.menu.top >= 64 && geometry.menu.right < width, '菜单应完整显示在顶部栏以下')
        assert.ok(geometry.scrollWidth <= width)
        const recordsBounds = await page.$eval('.workspace-records', el => { const r = el.getBoundingClientRect(); return { top: r.top, right: r.right, bottom: r.bottom } })
        assert.ok(recordsBounds.top >= 64 && recordsBounds.right <= width && recordsBounds.bottom < geometry.card.top, '右上记录侧栏不能遮住输入卡片')
        await page.keyboard.press('Escape')
        await enterIdentifyAndOpenTypes()
        await page.waitForSelector('.prompt-category-menu')
        await pause(300)
        const categoryBounds = await page.$eval('.prompt-category-menu', el => { const r = el.getBoundingClientRect(); return { top: r.top, bottom: r.bottom, right: r.right } })
        assert.ok(categoryBounds.top >= 64 && categoryBounds.bottom <= height && categoryBounds.right <= width, '类别小窗不能被顶部栏或屏幕裁切')
        assert.equal(await page.$('.prompt-model-button'), null)
        assert.equal(await page.$$eval('.prompt-category-pill', els => els.length), 5)
        if (width === 1280) await page.screenshot({ path: resolve(evidence, 'dark-identify.png') })
        await page.keyboard.press('Escape')
        await page.waitForFunction(() => !document.querySelector('.prompt-category-menu'))
        await page.click('.prompt-mode-button:last-of-type')
        await page.waitForSelector('.prompt-model-button')
      }
      await page.emulateMediaFeatures([{ name: 'prefers-reduced-motion', value: 'reduce' }])
      assert.equal(await page.$eval('.prompt-input', el => getComputedStyle(el).transitionDuration), '0s')
    })

    await t.test('离开页面释放所有附件并保留原导航', async () => {
      await page.click('#pc-sidebar nav button[aria-label="系统设置"]')
      await page.waitForFunction(() => !document.querySelector('.prompt-input'))
      assert.equal(await page.$('.workspace-records'), null, '记录侧栏只属于识别工作台')
      const urls = await page.evaluate(() => window.previewURLs)
      assert.deepEqual([...urls.created].sort(), [...urls.revoked].sort())
      await page.click('#pc-sidebar nav button[aria-label="工作台"]')
      await page.waitForSelector('.prompt-collapsed')
      await page.waitForSelector('.workspace-records')
      await page.waitForSelector('.workspace-welcome')
      for (const [width, height] of [[2549, 1403], [1280, 720], [1024, 600]]) {
        await page.setViewport({ width, height })
        const layout = await page.evaluate(() => {
          const rect = selector => { const r = document.querySelector(selector).getBoundingClientRect(); return { top: r.top, bottom: r.bottom, x: r.x, right: r.right, width: r.width } }
          return { welcome: rect('.workspace-welcome'), input: rect('.prompt-card'), frame: rect('.pc-workspace') }
        })
        assert.ok(layout.welcome.bottom < layout.input.top, '欢迎内容不能覆盖输入框')
        assert.ok(Math.abs(layout.welcome.x + layout.welcome.width / 2 - layout.frame.x - layout.frame.width / 2) < 1, '欢迎内容与工作区中心对齐')
      }
      await page.setViewport({ width: 1600, height: 1000 })
      await page.screenshot({ path: resolve(evidence, 'workspace-welcome-dark.png') })
      await page.click('.prompt-collapsed')
      await fill('检查点击发送后隐藏欢迎内容')
      await page.click('.prompt-action')
      await page.waitForFunction(() => !document.querySelector('.workspace-welcome'))
      assert.ok(businessCalls.every(path => ['/api/pest/identify', '/api/ai/chat'].includes(path)))
      assert.deepEqual(errors, [])
    })
  } finally {
    await browser?.close()
    await server?.close()
  }
})
