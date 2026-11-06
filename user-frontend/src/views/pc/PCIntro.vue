<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import PlantHealthScene from '../../components/pc/PlantHealthScene.vue'
import LeafQueryBrand from '../../components/pc/LeafQueryBrand.vue'
import { shouldLaunchIntro } from '../../utils/introInteraction.js'

const props = defineProps({ isDark: { type: Boolean, default: false } })
const emit = defineEmits(['enter-workspace'])
const isLaunching = ref(false)
let launchTimer = null

const capabilities = [
  { number: '01', title: '病虫害识别', detail: '图像检测与智能复核', paths: ['M19 4C11 3 4 7 5 16c8 3 15-2 14-12Z', 'M5 20 15 9M10 14l-1-4M10 14h5'] },
  { number: '02', title: '风险预测', detail: '气象信息与趋势分析', paths: ['M4 4v16h16', 'm7 15 4-5 4 2 5-7'] },
  { number: '03', title: '植保问答', detail: '知识查阅与防治参考', paths: ['M5 4h14v13H9l-4 4V4Z', 'M9 8h6M9 12h4'] }
]

function enterWorkspace() {
  if (isLaunching.value) return
  isLaunching.value = true
  // Preserve the existing introduction policy and 720ms launch transition.
  launchTimer = window.setTimeout(() => emit('enter-workspace'), 720)
}

function handleKeydown(event) {
  if (!isLaunching.value && shouldLaunchIntro(event)) {
    event.preventDefault()
    enterWorkspace()
  }
}

onMounted(() => window.addEventListener('keydown', handleKeydown))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  if (launchTimer != null) window.clearTimeout(launchTimer)
})
</script>

<template>
  <section class="pc-intro" :class="{ 'pc-intro--dark': props.isDark, 'pc-intro--launching': isLaunching }"
           :aria-busy="isLaunching">
    <div class="intro-grid" aria-hidden="true"></div>

    <header class="intro-header intro-container">
      <LeafQueryBrand :isDark="props.isDark" />
      <div class="intro-header-actions">
        <span class="intro-header-description">观察 · 分析 · 辅助决策</span>
        <div class="intro-theme-slot"><slot name="theme-toggle"></slot></div>
      </div>
    </header>

    <main class="intro-main intro-container">
      <div class="intro-copy">
        <p class="intro-eyebrow"><span aria-hidden="true"></span>图像识别 × 智能复核</p>
        <h1 class="intro-title">
          <span>识别作物病虫害，</span>
          <span class="intro-title-accent">让防治更有依据。</span>
        </h1>
        <p class="intro-description">
          上传作物图片，识别病害与虫害。结合智能复核与气象信息，分析病虫害发生风险，提供防治参考。
        </p>

        <div class="intro-action-row">
          <button type="button" class="intro-launch" :disabled="isLaunching" @click="enterWorkspace">
            <span>{{ isLaunching ? '正在进入' : '进入工作台' }}</span>
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M5 12h14m-6-6 6 6-6 6" />
            </svg>
          </button>
          <span class="intro-keyboard-hint"><kbd>Enter</kbd> 进入工作台</span>
        </div>

        <ul class="intro-capabilities" aria-label="核心能力">
          <li v-for="item in capabilities" :key="item.number" class="intro-capability">
            <div class="intro-capability-heading">
              <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path v-for="path in item.paths" :key="path" :d="path" />
              </svg>
              <span>{{ item.number }}</span>
            </div>
            <h2>{{ item.title }}</h2>
            <p>{{ item.detail }}</p>
          </li>
        </ul>
      </div>

      <figure class="intro-visual" aria-label="随时间自然变化的植物概念视觉">
        <div class="intro-scene-stage">
          <PlantHealthScene :isDark="props.isDark" :isLaunching="isLaunching" />
        </div>
      </figure>
    </main>

    <footer class="intro-footer intro-container">
      <span>图像识别 <b aria-hidden="true">→</b> 智能复核 <b aria-hidden="true">→</b> 防治参考</span>
      <span class="intro-footer-caption">植保决策，始于观察</span>
    </footer>

    <div v-if="isLaunching" class="intro-transition" role="status" aria-live="polite">
      <div class="intro-transition-halo" aria-hidden="true"></div>
      <img src="/vite.svg" alt="" width="60" height="60" />
      <p>正在进入工作台</p>
    </div>
  </section>
</template>

<style scoped>
.pc-intro {
  --intro-bg: #f5faf8;
  --intro-text: #173b3a;
  --intro-muted: #526e6c;
  --intro-line: rgba(28, 100, 84, 0.13);
  --intro-panel: rgba(255, 255, 255, 0.65);
  --intro-accent: #008c70;
  --intro-soft: rgba(0, 151, 113, 0.07);
  position: relative;
  isolation: isolate;
  display: flex;
  flex-direction: column;
  min-height: 100%;
  overflow: hidden;
  color: var(--intro-text);
  font-family: 'Segoe UI', 'Microsoft YaHei', 'PingFang SC', sans-serif;
  background:
    radial-gradient(ellipse at 85% 35%, rgba(18, 163, 146, 0.075), transparent 45%),
    radial-gradient(ellipse at 4% 12%, rgba(85, 193, 151, 0.07), transparent 40%),
    var(--intro-bg);
}
.pc-intro--dark {
  --intro-bg: #07131c;
  --intro-text: #e9f4ef;
  --intro-muted: #a4bdb8;
  --intro-line: rgba(141, 207, 187, 0.13);
  --intro-panel: rgba(17, 35, 43, 0.68);
  --intro-accent: #65dec0;
  --intro-soft: rgba(65, 206, 170, 0.07);
  background:
    radial-gradient(ellipse at 85% 35%, rgba(28, 144, 143, 0.12), transparent 43%),
    radial-gradient(ellipse at 3% 12%, rgba(30, 146, 114, 0.09), transparent 45%),
    var(--intro-bg);
}
.intro-grid {
  position: absolute;
  inset: 0;
  z-index: -1;
  pointer-events: none;
  background-image: linear-gradient(var(--intro-line) 1px, transparent 1px), linear-gradient(90deg, var(--intro-line) 1px, transparent 1px);
  background-size: 64px 64px;
  opacity: 0.23;
  mask-image: linear-gradient(90deg, transparent, #000 70%);
}
.intro-container { width: 100%; max-width: 1440px; padding-inline: clamp(28px, 4vw, 72px); margin-inline: auto; box-sizing: border-box; }
.intro-header { display: flex; align-items: center; justify-content: space-between; gap: 24px; padding-block: 27px; }
.intro-header-actions { display: flex; align-items: center; gap: 30px; min-width: 0; }
.intro-header-description { color: var(--intro-muted); font-size: 14px; letter-spacing: 1px; white-space: nowrap; }
.intro-theme-slot { display: flex; align-items: center; flex-shrink: 0; }
.intro-main { position: relative; flex: 1; display: grid; grid-template-columns: minmax(0, 1.06fr) minmax(0, 1fr); align-items: center; gap: clamp(28px, 4vw, 64px); padding-block: 28px 38px; }
.intro-copy { min-width: 0; padding-block: 12px; }
.intro-eyebrow { display: inline-flex; align-items: center; gap: 9px; margin: 0 0 22px; color: var(--intro-accent); font-size: 14px; font-weight: 600; letter-spacing: 1.4px; }
.intro-eyebrow > span { width: 6px; height: 6px; background: currentColor; border-radius: 50%; }
.intro-title { margin: 0; color: var(--intro-text); font-size: clamp(38px, 4.1vw, 61px); font-weight: 800; line-height: 1.32; letter-spacing: -1.8px; }
.intro-title > span { display: block; }
.intro-title-accent { background: linear-gradient(110deg, #008e70 8%, #1789ab 95%); background-clip: text; -webkit-background-clip: text; color: transparent; }
.pc-intro--dark .intro-title-accent { background-image: linear-gradient(110deg, #86e7be 8%, #53bce2 95%); }
.intro-description { margin: 22px 0 0; max-width: 29em; color: var(--intro-muted); font-size: 18px; line-height: 1.85; }
.intro-action-row { display: flex; align-items: center; flex-wrap: wrap; gap: 22px; margin-top: 30px; }
.intro-launch { display: inline-flex; align-items: center; justify-content: center; gap: 28px; min-height: 56px; padding: 0 24px; border: 1px solid transparent; border-radius: 12px; background: linear-gradient(110deg, #009d78 0%, #008f92 100%); color: #fff; font-size: 16px; font-weight: 750; cursor: pointer; box-shadow: 0 9px 28px -13px rgba(0, 133, 104, 0.6); transition: transform 0.2s, box-shadow 0.2s; }
.intro-launch svg { width: 21px; height: 21px; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; transition: transform 0.2s; }
.intro-launch:hover:not(:disabled) { transform: translateY(-2px); box-shadow: 0 12px 32px -12px rgba(0, 133, 104, 0.55); }
.intro-launch:hover svg { transform: translateX(3px); }
.intro-launch:disabled { cursor: default; opacity: 0.75; }
.intro-launch:focus-visible { outline: 2px solid var(--intro-accent); outline-offset: 5px; }
.intro-keyboard-hint { display: flex; align-items: center; gap: 8px; color: var(--intro-muted); font-size: 14px; }
.intro-keyboard-hint kbd { border: 1px solid var(--intro-line); border-radius: 5px; background: var(--intro-panel); padding: 5px 7px; font: 12px/1.2 ui-monospace, monospace; }
.intro-capabilities { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); list-style: none; gap: 12px; margin: 39px 0 0; padding: 0; }
.intro-capability { min-width: 0; padding: 15px 14px 14px; border: 1px solid var(--intro-line); border-radius: 13px; background: var(--intro-panel); }
.intro-capability-heading { display: flex; justify-content: space-between; align-items: center; color: var(--intro-accent); }
.intro-capability-heading svg { width: 21px; height: 21px; stroke: currentColor; stroke-width: 1.45; stroke-linecap: round; stroke-linejoin: round; }
.intro-capability-heading > span { opacity: 0.75; font: 12px/1 ui-monospace, monospace; }
.intro-capability h2 { margin: 12px 0 0; color: var(--intro-text); font-size: 18px; font-weight: 700; }
.intro-capability p { margin: 7px 0 0; color: var(--intro-muted); font-size: 14px; line-height: 1.7; }
.intro-visual { min-width: 0; margin: 0; }
.intro-scene-stage { position: relative; height: clamp(380px, 54vh, 620px); overflow: hidden; }
.intro-scene-stage::before { content: ''; position: absolute; inset: 8%; border-radius: 50%; pointer-events: none; background: radial-gradient(circle, var(--intro-soft), transparent 70%); }
.intro-footer { display: flex; align-items: center; justify-content: space-between; gap: 20px; padding-block: 17px; border-top: 1px solid var(--intro-line); color: var(--intro-muted); font-size: 13px; letter-spacing: 0.7px; }
.intro-footer b { margin-inline: 12px; font-weight: normal; color: var(--intro-accent); opacity: 0.6; }
.intro-footer-caption { opacity: 0.75; }
.intro-transition { position: absolute; inset: 0; z-index: 10; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 20px; background: var(--intro-panel); backdrop-filter: blur(15px); color: var(--intro-text); animation: intro-overlay 0.3s both; }
.intro-transition > img { position: relative; z-index: 1; }
.intro-transition > p { position: relative; z-index: 1; font-size: 14px; letter-spacing: 2px; }
.intro-transition-halo { position: absolute; width: 200px; height: 200px; border: 1px solid var(--intro-accent); border-radius: 50%; opacity: 0; animation: intro-halo 0.72s ease-out both; }
.pc-intro--launching .intro-copy, .pc-intro--launching .intro-header, .pc-intro--launching .intro-footer { pointer-events: none; }
@keyframes intro-overlay { from { opacity: 0; } to { opacity: 1; } }
@keyframes intro-halo { from { transform: scale(0.5); opacity: 0.55; } to { transform: scale(3); opacity: 0; } }

@media (max-width: 1150px) {
  .intro-container { padding-inline: 28px; }
  .intro-main { gap: 24px; }
  .intro-title { font-size: 39px; letter-spacing: -1px; }
  .intro-description { font-size: 17px; }
  .intro-capabilities { gap: 8px; }
  .intro-capability { padding-inline: 11px; }
  .intro-capability h2 { font-size: 17px; }
  .intro-capability p { font-size: 13px; }
}
@media (min-width: 1024px) and (max-height: 760px) {
  .intro-header { padding-block: 16px; }
  .intro-main { padding-block: 15px 22px; }
  .intro-title { font-size: clamp(38px, 3.7vw, 51px); }
  .intro-eyebrow { margin-bottom: 15px; }
  .intro-description { margin-top: 17px; font-size: 17px; }
  .intro-action-row { margin-top: 24px; }
  .intro-capabilities { margin-top: 28px; }
  .intro-capability { display: grid; grid-template-columns: 22px minmax(0, 1fr); align-items: center; column-gap: 8px; padding-block: 12px; }
  .intro-capability-heading { grid-column: 1; grid-row: 1; }
  .intro-capability-heading > span { display: none; }
  .intro-capability h2 { grid-column: 2; grid-row: 1; margin-top: 0; font-size: 16px; }
  .intro-capability p { grid-column: 1 / -1; }
  .intro-scene-stage { height: clamp(300px, 50vh, 440px); }
  .intro-footer { padding-block: 12px; }
}
@media (max-width: 900px) {
  .intro-main { grid-template-columns: 1fr; gap: 36px; }
  .intro-header-description { display: none; }
  .intro-title { font-size: clamp(32px, 5vw, 43px); }
  .intro-visual { max-width: 560px; width: 100%; margin-inline: auto; }
}
@media (prefers-reduced-motion: reduce) {
  .pc-intro *, .pc-intro *::before { animation: none !important; transition: none !important; }
  .intro-launch:hover, .intro-launch:hover svg { transform: none; }
}
</style>
