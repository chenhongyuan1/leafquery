<script setup>
import { onBeforeUnmount, ref } from 'vue'
const props = defineProps({
  isDark: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['enter-workspace'])

const isLaunching = ref(false)
let launchTimer = null

const handleEnterWorkspace = () => {
  if (isLaunching.value) return

  isLaunching.value = true
  launchTimer = window.setTimeout(() => {
    emit('enter-workspace')
  }, 720)
}

onBeforeUnmount(() => {
  if (launchTimer) {
    window.clearTimeout(launchTimer)
  }
})

const capabilityCards = [
  {
    eyebrow: 'AI 识别',
    title: '病虫害图像诊断',
    description: '结合视觉识别引擎与诊断流程，快速定位叶片异常、病害类型与处理方向。',
    accent: 'from-emerald-400/70 to-teal-300/70'
  },
  {
    eyebrow: 'Data Center',
    title: '风险趋势洞察',
    description: '把识别记录、风险评分和趋势图聚合成可持续追踪的数据工作台。',
    accent: 'from-sky-400/70 to-cyan-300/70'
  },
  {
    eyebrow: 'Knowledge Assist',
    title: '知识与问答辅助',
    description: '把病虫害知识库、问答内容与诊断结果串起来，降低诊断后的决策成本。',
    accent: 'from-fuchsia-400/70 to-violet-300/70'
  }
]

const signalItems = [
  { label: 'CV + 大模型协同', value: '01' },
  { label: '桌面工作台入口', value: '02' },
  { label: '知识与趋势联动', value: '03' }
]
</script>

<template>
  <section
    class="intro-root relative min-h-full overflow-hidden"
    :class="[
      props.isDark ? 'intro-root--dark text-white' : 'intro-root--light text-slate-900',
      isLaunching ? 'intro-root--launching' : ''
    ]"
  >
    <div class="intro-blob intro-blob--left"></div>
    <div class="intro-blob intro-blob--right"></div>
    <div class="intro-blob intro-blob--center"></div>

    <div class="intro-transition-layer" :class="{ 'is-active': isLaunching }" aria-hidden="true">
      <div class="intro-transition-halo"></div>
      <div class="intro-transition-core">
        <div class="intro-transition-logo">🍃</div>
        <div class="intro-transition-text">正在进入工作台</div>
      </div>
    </div>

    <div class="intro-content relative z-10 flex min-h-full items-center px-6 py-10 lg:px-10 xl:px-14">
      <div class="mx-auto flex w-full max-w-7xl flex-col gap-8">
        <div class="intro-surface intro-sheen intro-fade intro-hero-card rounded-[40px] p-8 lg:p-10 xl:p-14">
          <div class="mb-6 flex flex-wrap justify-center gap-3 text-xs font-semibold tracking-[0.2em]">
            <span class="intro-pill">LeafQuery</span>
            <span class="intro-pill">PC Workspace</span>
            <span class="intro-pill">Liquid Glass</span>
          </div>

          <p
            class="intro-display-caption"
            :class="props.isDark ? 'text-emerald-200/80' : 'text-emerald-700/80'"
          >
            Plant Diagnosis · Data Insight · Knowledge Assist
          </p>

          <div class="intro-display-shell">
            <h1 class="intro-display-title">LeafQuery</h1>
          </div>

          <p
            class="intro-hero-kicker"
            :class="props.isDark ? 'text-white/80' : 'text-slate-800/84'"
          >
            病虫害诊断桌面入口
          </p>

          <p
            class="mx-auto mt-6 max-w-3xl text-center text-base leading-8 md:text-lg"
            :class="props.isDark ? 'text-white/72' : 'text-slate-700/78'"
          >
            LeafQuery 将图像识别、风险洞察与知识辅助串成一条桌面工作流，让病虫害诊断不再只是一次识别，而是一个持续跟踪与决策的过程。
          </p>

          <div class="mt-10 flex flex-wrap items-center justify-center gap-4">
            <button
              class="intro-primary-btn inline-flex items-center gap-3 rounded-full px-7 py-4 text-base font-bold text-white"
              :class="{ 'is-launching': isLaunching }"
              :disabled="isLaunching"
              @click="handleEnterWorkspace"
            >
              <span>进入工作台</span>
              <span class="inline-flex h-8 w-8 items-center justify-center rounded-full bg-white/20 text-lg">→</span>
            </button>
            <div
              class="rounded-full px-5 py-3 text-sm font-medium"
              :class="props.isDark ? 'bg-white/8 text-white/70' : 'bg-white/60 text-slate-600'"
            >
              桌面端每次进入先展示介绍页
            </div>
          </div>

          <div class="mt-10 flex flex-wrap justify-center gap-3">
            <div
              v-for="item in signalItems"
              :key="item.label"
              class="intro-signal-card rounded-[22px] px-4 py-3"
            >
              <div class="text-[11px] font-semibold tracking-[0.22em]" :class="props.isDark ? 'text-white/45' : 'text-slate-500'">
                {{ item.value }}
              </div>
              <div class="mt-2 text-sm font-semibold" :class="props.isDark ? 'text-white/80' : 'text-slate-700'">
                {{ item.label }}
              </div>
            </div>
          </div>
        </div>

        <div class="intro-surface intro-sheen intro-fade rounded-[32px] p-6 lg:p-7" style="animation-delay: 0.1s;">
          <div class="flex items-start justify-between gap-4">
            <div>
              <p class="text-xs font-semibold uppercase tracking-[0.28em]" :class="props.isDark ? 'text-cyan-200/75' : 'text-cyan-700/80'">
                工作流预览
              </p>
              <h2 class="mt-3 text-2xl font-black md:text-3xl">一屏进入诊断工作站</h2>
            </div>
            <div
              class="rounded-full px-3 py-1 text-xs font-semibold"
              :class="props.isDark ? 'bg-emerald-400/16 text-emerald-200' : 'bg-emerald-500/12 text-emerald-700'"
            >
              Ready
            </div>
          </div>

          <div class="mt-8 space-y-4">
            <div class="intro-mini-panel rounded-[26px] p-5">
              <div class="flex items-center justify-between">
                <span class="text-sm font-semibold" :class="props.isDark ? 'text-white/76' : 'text-slate-700'">识别链路</span>
                <span class="text-xs uppercase tracking-[0.2em]" :class="props.isDark ? 'text-white/42' : 'text-slate-500'">Stage 01</span>
              </div>
              <div class="mt-4 grid grid-cols-3 gap-3 text-center">
                <div class="intro-mini-step rounded-2xl px-3 py-4">
                  <div class="text-lg">叶片</div>
                  <div class="mt-2 text-xs" :class="props.isDark ? 'text-white/54' : 'text-slate-500'">上传图像</div>
                </div>
                <div class="intro-mini-step rounded-2xl px-3 py-4">
                  <div class="text-lg">AI</div>
                  <div class="mt-2 text-xs" :class="props.isDark ? 'text-white/54' : 'text-slate-500'">智能诊断</div>
                </div>
                <div class="intro-mini-step rounded-2xl px-3 py-4">
                  <div class="text-lg">趋势</div>
                  <div class="mt-2 text-xs" :class="props.isDark ? 'text-white/54' : 'text-slate-500'">持续追踪</div>
                </div>
              </div>
            </div>

            <div class="grid gap-4 sm:grid-cols-2">
              <div class="intro-mini-panel rounded-[24px] p-5">
                <div class="text-xs uppercase tracking-[0.24em]" :class="props.isDark ? 'text-white/42' : 'text-slate-500'">核心模块</div>
                <div class="mt-4 space-y-3 text-sm font-medium">
                  <div class="flex items-center justify-between">
                    <span :class="props.isDark ? 'text-white/78' : 'text-slate-700'">智能工作台</span>
                    <span class="h-2.5 w-2.5 rounded-full bg-emerald-400"></span>
                  </div>
                  <div class="flex items-center justify-between">
                    <span :class="props.isDark ? 'text-white/78' : 'text-slate-700'">数据中心</span>
                    <span class="h-2.5 w-2.5 rounded-full bg-sky-400"></span>
                  </div>
                  <div class="flex items-center justify-between">
                    <span :class="props.isDark ? 'text-white/78' : 'text-slate-700'">知识图谱</span>
                    <span class="h-2.5 w-2.5 rounded-full bg-fuchsia-400"></span>
                  </div>
                </div>
              </div>

              <div class="intro-mini-panel rounded-[24px] p-5">
                <div class="text-xs uppercase tracking-[0.24em]" :class="props.isDark ? 'text-white/42' : 'text-slate-500'">工作台价值</div>
                <div class="mt-4 space-y-3">
                  <div class="flex items-baseline justify-between">
                    <span class="text-sm" :class="props.isDark ? 'text-white/78' : 'text-slate-700'">识别到决策</span>
                    <span class="text-xl font-black">1 Step</span>
                  </div>
                  <div class="flex items-baseline justify-between">
                    <span class="text-sm" :class="props.isDark ? 'text-white/78' : 'text-slate-700'">数据视角</span>
                    <span class="text-xl font-black">Live</span>
                  </div>
                  <div class="flex items-baseline justify-between">
                    <span class="text-sm" :class="props.isDark ? 'text-white/78' : 'text-slate-700'">知识辅助</span>
                    <span class="text-xl font-black">Assist</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <div class="grid gap-5 lg:grid-cols-3">
          <article
            v-for="(card, index) in capabilityCards"
            :key="card.title"
            class="intro-surface intro-sheen intro-fade rounded-[30px] p-6 lg:p-7"
            :style="{ animationDelay: `${0.16 + index * 0.08}s` }"
          >
            <div class="mb-5 flex items-center justify-between gap-3">
              <span
                class="rounded-full px-3 py-1 text-[11px] font-semibold uppercase tracking-[0.22em]"
                :class="props.isDark ? 'bg-white/10 text-white/70' : 'bg-white/65 text-slate-600'"
              >
                {{ card.eyebrow }}
              </span>
              <span class="h-10 w-10 rounded-2xl bg-gradient-to-br" :class="card.accent"></span>
            </div>
            <h3 class="text-2xl font-black">{{ card.title }}</h3>
            <p class="mt-4 text-sm leading-7" :class="props.isDark ? 'text-white/68' : 'text-slate-700/80'">
              {{ card.description }}
            </p>
          </article>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.intro-root {
  isolation: isolate;
}

.intro-root--dark {
  background:
    radial-gradient(circle at top left, rgba(16, 185, 129, 0.18), transparent 28%),
    radial-gradient(circle at top right, rgba(14, 165, 233, 0.14), transparent 30%),
    linear-gradient(145deg, #020617 0%, #0f172a 42%, #111827 100%);
}

.intro-root--light {
  background:
    radial-gradient(circle at top left, rgba(16, 185, 129, 0.12), transparent 26%),
    radial-gradient(circle at top right, rgba(14, 165, 233, 0.1), transparent 30%),
    linear-gradient(145deg, #e2f4ee 0%, #edf4ff 46%, #f8fafc 100%);
}

.intro-content {
  transition:
    transform 560ms cubic-bezier(0.22, 1, 0.36, 1),
    opacity 420ms ease,
    filter 420ms ease;
}

.intro-root--launching .intro-content {
  transform: scale(1.04);
  opacity: 0.14;
  filter: blur(10px);
}

.intro-transition-layer {
  position: absolute;
  inset: 0;
  z-index: 30;
  display: grid;
  place-items: center;
  pointer-events: none;
  opacity: 0;
  transition: opacity 280ms ease;
}

.intro-transition-layer.is-active {
  opacity: 1;
}

.intro-transition-layer::before {
  content: '';
  position: absolute;
  inset: 0;
  background: rgba(2, 6, 23, 0.32);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

.intro-root--light .intro-transition-layer::before {
  background: rgba(226, 232, 240, 0.26);
}

.intro-transition-halo {
  position: absolute;
  width: 16rem;
  height: 16rem;
  border-radius: 9999px;
  background: radial-gradient(circle, rgba(16, 185, 129, 0.45), rgba(14, 165, 233, 0.08) 55%, transparent 72%);
  transform: scale(0.45);
  opacity: 0;
}

.intro-transition-layer.is-active .intro-transition-halo {
  animation: intro-halo-burst 720ms cubic-bezier(0.19, 1, 0.22, 1) forwards;
}

.intro-transition-core {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.9rem;
  transform: translateY(18px) scale(0.9);
  opacity: 0;
}

.intro-transition-layer.is-active .intro-transition-core {
  animation: intro-core-rise 520ms cubic-bezier(0.22, 1, 0.36, 1) 80ms forwards;
}

.intro-transition-logo {
  display: flex;
  width: 5rem;
  height: 5rem;
  align-items: center;
  justify-content: center;
  border-radius: 1.6rem;
  font-size: 2rem;
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.92), rgba(14, 165, 233, 0.92));
  box-shadow: 0 24px 60px rgba(16, 185, 129, 0.28);
}

.intro-transition-text {
  font-size: 0.95rem;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: rgba(255, 255, 255, 0.88);
}

.intro-root--light .intro-transition-text {
  color: rgba(15, 23, 42, 0.78);
}

.intro-blob {
  position: absolute;
  border-radius: 9999px;
  filter: blur(80px);
  opacity: 0.52;
  pointer-events: none;
  animation: blob-drift 18s ease-in-out infinite alternate;
}

.intro-blob--left {
  top: -8%;
  left: -4%;
  width: 24rem;
  height: 24rem;
  background: radial-gradient(circle, rgba(52, 211, 153, 0.8), rgba(16, 185, 129, 0.15));
}

.intro-blob--right {
  right: -8%;
  top: 14%;
  width: 26rem;
  height: 26rem;
  background: radial-gradient(circle, rgba(56, 189, 248, 0.7), rgba(59, 130, 246, 0.12));
  animation-delay: -6s;
}

.intro-blob--center {
  bottom: -8%;
  left: 36%;
  width: 22rem;
  height: 22rem;
  background: radial-gradient(circle, rgba(217, 70, 239, 0.42), rgba(168, 85, 247, 0.1));
  animation-delay: -12s;
}

.intro-surface {
  position: relative;
  overflow: hidden;
  backdrop-filter: blur(22px);
  -webkit-backdrop-filter: blur(22px);
}

.intro-root--dark .intro-surface {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.12);
  box-shadow: 0 24px 80px rgba(0, 0, 0, 0.35);
}

.intro-root--light .intro-surface {
  background: rgba(255, 255, 255, 0.62);
  border: 1px solid rgba(15, 23, 42, 0.1);
  box-shadow: 0 24px 80px rgba(148, 163, 184, 0.18);
}

.intro-hero-card {
  text-align: center;
}

.intro-display-caption {
  margin: 0 auto;
  font-size: 0.8rem;
  font-weight: 700;
  letter-spacing: 0.44em;
  text-transform: uppercase;
}

.intro-display-shell {
  margin-top: 1.5rem;
}

.intro-display-title {
  margin: 0;
  font-family: 'Iowan Old Style', 'Palatino Linotype', 'Book Antiqua', serif;
  font-size: clamp(4.5rem, 12vw, 10rem);
  line-height: 0.9;
  letter-spacing: -0.08em;
  font-weight: 700;
  word-break: break-word;
  color: transparent;
  background-size: 100% 100%;
  -webkit-background-clip: text;
  background-clip: text;
}

.intro-root--dark .intro-display-title {
  background-image: linear-gradient(135deg, #f8fafc 0%, #67e8f9 36%, #38bdf8 68%, #818cf8 100%);
  text-shadow: 0 14px 40px rgba(56, 189, 248, 0.2);
}

.intro-root--light .intro-display-title {
  background-image: linear-gradient(135deg, #0f172a 0%, #115e59 32%, #0369a1 68%, #4f46e5 100%);
  text-shadow: 0 16px 32px rgba(59, 130, 246, 0.08);
}

.intro-hero-kicker {
  margin-top: 1rem;
  font-size: clamp(1.15rem, 2vw, 1.5rem);
  font-weight: 800;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.intro-sheen::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.28), transparent 42%);
  pointer-events: none;
}

.intro-pill,
.intro-signal-card,
.intro-mini-panel,
.intro-mini-step {
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
}

.intro-root--dark .intro-pill,
.intro-root--dark .intro-signal-card,
.intro-root--dark .intro-mini-panel,
.intro-root--dark .intro-mini-step {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.12);
}

.intro-root--light .intro-pill,
.intro-root--light .intro-signal-card,
.intro-root--light .intro-mini-panel,
.intro-root--light .intro-mini-step {
  background: rgba(255, 255, 255, 0.58);
  border: 1px solid rgba(15, 23, 42, 0.08);
}

.intro-pill {
  border-radius: 9999px;
  padding: 0.7rem 1rem;
}

.intro-primary-btn + div {
  display: none;
}

.intro-primary-btn {
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.88), rgba(14, 165, 233, 0.86));
  box-shadow: 0 16px 38px rgba(16, 185, 129, 0.24);
  transition:
    transform 180ms ease,
    box-shadow 180ms ease,
    filter 180ms ease;
}

.intro-primary-btn:hover {
  transform: translateY(-2px);
  filter: saturate(1.05);
  box-shadow: 0 20px 44px rgba(16, 185, 129, 0.3);
}

.intro-primary-btn:active {
  transform: translateY(0);
}

.intro-primary-btn.is-launching {
  transform: scale(1.03);
  box-shadow: 0 22px 48px rgba(16, 185, 129, 0.32);
}

.intro-primary-btn:disabled {
  cursor: default;
}

.intro-primary-icon {
  min-width: 2rem;
}

.intro-fade {
  opacity: 0;
  transform: translateY(24px);
  animation: intro-fade-up 0.8s cubic-bezier(0.22, 1, 0.36, 1) forwards;
}

@keyframes intro-fade-up {
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes intro-halo-burst {
  0% {
    opacity: 0;
    transform: scale(0.45);
  }
  35% {
    opacity: 1;
  }
  100% {
    opacity: 0;
    transform: scale(5.8);
  }
}

@keyframes intro-core-rise {
  0% {
    opacity: 0;
    transform: translateY(18px) scale(0.9);
  }
  100% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes blob-drift {
  0% {
    transform: translate3d(0, 0, 0) scale(1);
  }
  50% {
    transform: translate3d(28px, -22px, 0) scale(1.08);
  }
  100% {
    transform: translate3d(-18px, 20px, 0) scale(0.96);
  }
}

@media (max-width: 1279px) {
  .intro-blob--left,
  .intro-blob--right,
  .intro-blob--center {
    opacity: 0.42;
  }
}

@media (max-width: 767px) {
  .intro-root {
    min-height: auto;
  }

  .intro-display-caption {
    letter-spacing: 0.28em;
  }

  .intro-hero-kicker {
    letter-spacing: 0.1em;
  }
}
</style>
