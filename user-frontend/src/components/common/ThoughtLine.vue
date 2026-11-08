<script setup>
// Vue adaptation of React Bits Thought Line; see THIRD_PARTY_NOTICES.md.
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import './ThoughtLine.css'

const props = defineProps({
  working: { type: Boolean, default: true },
  kind: { type: String, default: 'report' },
  startedAt: { type: Number, default: 0 },
  finishedAt: { type: Number, default: 0 },
  failed: { type: Boolean, default: false }
})
const phases = {
  report: ['正在查看天气与生长环境…', '正在检索病虫害知识…', '正在整合初筛与复核结果…', '正在生成诊断报告…'],
  identify: ['正在分析图片…', '正在识别病虫害特征…', '正在等待视觉复核结果…'],
  chat: ['正在阅读你的问题…', '正在结合本次对话与资料…', '正在整理回答…']
}
const labels = { report: '报告已生成', identify: '识别已完成', chat: '回答已生成' }
const now = ref(Date.now())
const open = ref(props.working)
let timer
let localStart = Date.now()
const elapsed = computed(() => Math.max(0, ((props.working ? now.value : props.finishedAt || now.value) - (props.startedAt || localStart)) / 1000))
const steps = computed(() => phases[props.kind] || phases.report)
const currentIndex = computed(() => Math.min(steps.value.length - 1, Math.floor(elapsed.value / 3.5)))
const currentLabel = computed(() => props.working ? steps.value[currentIndex.value] : props.failed ? '本次处理未完成' : labels[props.kind] || '处理已完成')
const visibleSteps = computed(() => steps.value.slice(0, currentIndex.value + 1))
const duration = computed(() => elapsed.value < 60 ? `${elapsed.value.toFixed(1)}秒` : `${Math.floor(elapsed.value / 60)}分${Math.floor(elapsed.value % 60)}秒`)

watch(() => [props.working, props.startedAt], () => {
  clearInterval(timer)
  now.value = Date.now()
  open.value = props.working
  if (props.working) {
    localStart = props.startedAt || Date.now()
    timer = setInterval(() => { now.value = Date.now() }, 100)
  }
}, { immediate: true })
onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <div class="thought-line" :data-working="working ? '' : undefined" :data-open="open ? '' : undefined" aria-label="处理进度提示">
    <button type="button" class="thought-line__head" data-toggle :aria-expanded="open" @click="open = !open">
      <span class="thought-line__glyph" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6"><path d="m12 2 2.5 7.5L22 12l-7.5 2.5L12 22l-2.5-7.5L2 12l7.5-2.5Z" /></svg></span>
      <span class="thought-line__current thought-line__breath" :data-shimmer="working ? '' : undefined" aria-live="polite">{{ currentLabel }}</span>
      <span class="thought-line__timer" aria-hidden="true">{{ duration }}</span>
      <span class="thought-line__chevron" data-on aria-hidden="true"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="m6 9 6 6 6-6" /></svg></span>
    </button>
    <div class="thought-line__trace" :data-open="open ? '' : undefined" :aria-hidden="!open" :inert="!open">
      <div class="thought-line__fold"><div class="thought-line__steps">
        <div v-for="(step, index) in visibleSteps" :key="step" class="thought-line__step" :data-done="!working ? '' : undefined">
          <span class="thought-line__mark" aria-hidden="true"><i :class="working && index === currentIndex ? 'thought-line__pulse' : 'thought-line__dot'" /></span><span class="thought-line__step-text">{{ step }}</span>
        </div>
      </div></div>
    </div>
  </div>
</template>
