<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps({ record: { type: Object, default: null } })
const emit = defineEmits(['close'])
const panel = ref(null)
const closeButton = ref(null)
const imageFailed = ref(false)
const name = computed(() => props.record?.pestName || props.record?.name || '未知结果')
const imageUrl = computed(() => props.record?.imageUrl?.trim() || '')
const confidence = computed(() => {
  if (props.record?.yoloUsed === false) return '未使用'
  const value = props.record?.confidence
  const numeric = Number(value)
  return value == null || value === '' || !Number.isFinite(numeric) ? '未记录' : `${(numeric * 100).toFixed(1)}%`
})
const confidenceLabel = computed(() => props.record?.yoloUsed == null ? '模型置信度' : 'YOLO 置信度')
const reviewText = computed(() => props.record?.reviewResult?.trim() || '')
const reviewStatus = computed(() => {
  if (reviewText.value) return '已复核'
  if (props.record?.reviewError || props.record?.reviewRequired === true) return '复核未完成'
  if (props.record?.reviewRequired === false) return '无需复核'
  return '未保存'
})
const reviewMessage = computed(() => {
  if (reviewText.value) return reviewText.value
  if (props.record?.reviewError) return props.record.reviewError
  if (props.record?.reviewRequired === true) return '本次 AI 复核未返回有效结论。'
  if (props.record?.reviewRequired === false) return '本次识别无需 AI 视觉复核。'
  return '这条历史记录未保存 AI 复核结果。'
})
const time = computed(() => {
  const value = props.record?.createTime || props.record?.time
  if (!value) return '未记录'
  const date = new Date(typeof value === 'string' && /^\d+$/.test(value) ? Number(value) : value)
  if (Number.isNaN(date.getTime())) return '未记录'
  const pad = value => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
})
const location = computed(() => [...new Set([props.record?.city, props.record?.region].filter(Boolean))].join(' · ') || '未记录')
let previousFocus
let previousOverflow
let appRoot
let previousInert
let locked = false

const unlock = () => {
  if (!locked) return
  document.body.style.overflow = previousOverflow
  if (appRoot) appRoot.inert = previousInert
  if (previousFocus?.isConnected) previousFocus.focus({ preventScroll: true })
  locked = false
}

watch(() => props.record, async record => {
  imageFailed.value = false
  if (!record) { unlock(); return }
  if (!locked) {
    previousFocus = document.activeElement
    previousOverflow = document.body.style.overflow
    appRoot = document.getElementById('app')
    previousInert = appRoot?.inert || false
    document.body.style.overflow = 'hidden'
    if (appRoot) appRoot.inert = true
    locked = true
  }
  await nextTick()
  if (props.record) closeButton.value?.focus({ preventScroll: true })
})

const handleKeydown = event => {
  if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); emit('close'); return }
  if (event.key !== 'Tab') return
  const buttons = panel.value?.querySelectorAll('button:not([disabled]), [href], [tabindex="0"]')
  if (!buttons?.length) return
  const first = buttons[0]
  const last = buttons[buttons.length - 1]
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
}

onBeforeUnmount(unlock)
</script>

<template>
  <Teleport to="body">
    <Transition name="record-detail">
      <div v-if="record" class="record-detail-overlay" data-testid="record-detail-overlay" @click.self="emit('close')" @keydown="handleKeydown">
        <section ref="panel" role="dialog" aria-modal="true" aria-labelledby="record-detail-title" class="record-detail-panel flex flex-col border border-slate-100 bg-white text-slate-800 shadow-2xl dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100">
          <div class="mx-auto mt-4 h-1.5 w-16 shrink-0 rounded-full bg-slate-100 dark:bg-slate-700 lg:hidden" aria-hidden="true"></div>
          <header class="flex shrink-0 items-center justify-between gap-4 px-6 pb-5 pt-6 sm:px-8">
            <div>
              <div class="mb-2 text-xs font-bold tracking-widest text-emerald-600 dark:text-emerald-400">历史足迹</div>
              <h2 id="record-detail-title" class="text-xl font-black">识别记录详情</h2>
            </div>
            <button ref="closeButton" type="button" aria-label="关闭记录详情" class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-slate-100 text-slate-500 transition hover:bg-slate-200 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700" @click="emit('close')">
              <svg viewBox="0 0 24 24" class="h-5 w-5" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true"><path d="m6 6 12 12M6 18 18 6" /></svg>
            </button>
          </header>
          <div class="record-detail-content min-h-0 flex-1 overflow-y-auto overscroll-contain px-6 pb-8 sm:px-8">
            <div class="flex h-52 items-center justify-center overflow-hidden rounded-3xl border border-slate-100 bg-slate-50 sm:h-64 dark:border-slate-800 dark:bg-slate-950/60">
              <img v-if="imageUrl && !imageFailed" :key="imageUrl" :src="imageUrl" :alt="`${name}识别图片`" class="h-full w-full object-contain" @error="imageFailed = true" />
              <div v-else class="text-center text-slate-400"><div class="mb-3 text-4xl" aria-hidden="true">🌾</div><p class="text-sm">{{ imageUrl ? '原图暂不可用' : '暂无识别图片' }}</p></div>
            </div>
            <div class="mt-6 flex flex-wrap items-start justify-between gap-3">
              <div class="min-w-0 flex-1"><p class="text-xs font-semibold text-slate-400">识别结果</p><h3 class="mt-2 break-words text-2xl font-black leading-snug">{{ name }}</h3></div>
              <div class="rounded-2xl border border-emerald-100 bg-emerald-50 px-4 py-3 text-center dark:border-emerald-500/20 dark:bg-emerald-500/10"><p class="text-xs font-semibold text-emerald-600 dark:text-emerald-400">{{ confidenceLabel }}</p><p class="mt-1 text-lg font-black text-emerald-700 dark:text-emerald-300">{{ confidence }}</p></div>
            </div>
            <section class="mt-5 rounded-2xl border border-emerald-100 bg-emerald-50/70 p-4 dark:border-emerald-500/20 dark:bg-emerald-500/10" data-testid="record-review">
              <div class="flex flex-wrap items-center justify-between gap-2"><h4 class="text-sm font-bold text-emerald-800 dark:text-emerald-300">🔍 AI 视觉复核结果</h4><span class="rounded-full bg-white/80 px-2.5 py-1 text-xs font-semibold text-emerald-700 dark:bg-slate-900/70 dark:text-emerald-400">{{ reviewStatus }}</span></div>
              <p class="mt-3 whitespace-pre-line break-words text-sm leading-relaxed text-slate-700 dark:text-slate-300" style="overflow-wrap: anywhere">{{ reviewMessage }}</p>
            </section>
            <dl class="mt-6 overflow-hidden rounded-2xl border border-slate-100 bg-slate-50/70 text-sm dark:border-slate-800 dark:bg-slate-950/40">
              <div class="flex gap-5 border-b border-slate-100 px-4 py-4 dark:border-slate-800"><dt class="shrink-0 text-slate-500 dark:text-slate-400">识别时间</dt><dd class="min-w-0 flex-1 break-words text-right font-semibold">{{ time }}</dd></div>
              <div class="flex gap-5 border-b border-slate-100 px-4 py-4 dark:border-slate-800"><dt class="shrink-0 text-slate-500 dark:text-slate-400">关联作物</dt><dd class="min-w-0 flex-1 break-words text-right font-semibold">{{ record.cropName || '未记录' }}</dd></div>
              <div class="flex gap-5 px-4 py-4"><dt class="shrink-0 text-slate-500 dark:text-slate-400">识别地区</dt><dd class="min-w-0 flex-1 break-words text-right font-semibold">{{ location }}</dd></div>
            </dl>
            <p class="mt-4 text-xs leading-relaxed text-slate-400">以上为本次识别时保存的信息。</p>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.record-detail-overlay { position: fixed; inset: 0; z-index: 300; display: flex; align-items: flex-end; justify-content: center; background: rgba(15, 23, 42, .4); backdrop-filter: blur(4px); }
.record-detail-panel { width: 100%; height: 75dvh; max-height: calc(100dvh - 16px); border-radius: 2.5rem 2.5rem 0 0; padding-bottom: env(safe-area-inset-bottom, 0px); }
.record-detail-enter-active, .record-detail-leave-active { transition: opacity .3s ease; }
.record-detail-enter-active .record-detail-panel, .record-detail-leave-active .record-detail-panel { transition: transform .35s cubic-bezier(.22, 1, .36, 1); }
.record-detail-enter-from, .record-detail-leave-to { opacity: 0; }
.record-detail-enter-from .record-detail-panel, .record-detail-leave-to .record-detail-panel { transform: translateY(100%); }
@media (min-width: 1024px) {
  .record-detail-overlay { align-items: center; padding: 24px; }
  .record-detail-panel { max-width: 680px; height: auto; max-height: 85dvh; border-radius: 2rem; }
  .record-detail-enter-from .record-detail-panel, .record-detail-leave-to .record-detail-panel { transform: translateY(20px) scale(.98); }
}
@media (prefers-reduced-motion: reduce) {
  .record-detail-enter-active, .record-detail-leave-active, .record-detail-enter-active .record-detail-panel, .record-detail-leave-active .record-detail-panel { transition-duration: .01ms; }
}
</style>
