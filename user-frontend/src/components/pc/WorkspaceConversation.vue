<script setup>
import MarkdownContent from '../common/MarkdownContent.vue'
import ThoughtLine from '../common/ThoughtLine.vue'
import { isMeaninglessReview } from '../../utils/reviewConclusion.js'
defineProps({ turns: { type: Array, default: () => [] }, selectedId: { type: Number, default: null } })
defineEmits(['report', 'collapse', 'retry'])
</script>

<template>
  <div class="workspace-conversation">
    <article v-for="turn in turns" :key="turn.id" class="workspace-turn" :class="{ 'is-selected': selectedId === turn.id, 'turn-is-identify': turn.mode === 'identify' }" :data-turn-id="turn.id">
      <div class="turn-user" :class="{ 'turn-user--identify': turn.mode === 'identify', 'is-summary-collapsed': turn.summaryCollapsed }">
        <div v-if="turn.mode === 'identify'" class="turn-identify-meta">
          <span class="turn-identify-title">图片识别</span>
          <span class="turn-identify-status">{{ turn.status === 'pending' ? '识别与复核中…' : turn.status === 'error' ? '识别失败' : '识别完成' }}</span>
          <div class="turn-categories"><span v-for="category in turn.categories" :key="category">{{ category }}</span></div>
          <time>{{ turn.time }}</time>
        </div>
        <div v-else class="turn-user-meta"><span>询问</span><time>{{ turn.time }}</time></div>
        <p v-if="turn.message || turn.mode !== 'identify'">{{ turn.message || '请帮我看看这些图片' }}</p>
        <div v-if="turn.images.length" class="turn-images"><img v-for="(image, index) in turn.images" :key="index" :src="image.url" :alt="turn.mode === 'identify' ? '识别图片' : image.name" /></div>
        <section v-if="turn.mode === 'identify'" v-show="!turn.summaryCollapsed" :id="`turn-details-${turn.id}`" class="turn-assistant turn-assistant--identify" :aria-busy="turn.status === 'pending'" aria-label="识别结果">
          <ThoughtLine v-if="turn.startedAt" :class="{ 'turn-pending': turn.status === 'pending' }" :working="turn.status === 'pending'" kind="identify" :started-at="turn.startedAt" :finished-at="turn.finishedAt" :failed="turn.status === 'error'" />
          <div v-if="turn.status === 'error'" class="turn-error" role="alert">{{ turn.error }}<button type="button" @click="$emit('retry', turn.id)">重新识别</button></div>
          <div v-else-if="turn.status !== 'pending'" class="turn-detection-card">
            <h2>{{ turn.pestName || '未能识别出具体病虫害' }}</h2>
            <template v-if="turn.yoloUsed !== false"><div class="turn-confidence"><span>{{ turn.yoloUsed === true ? 'YOLO 置信度' : '识别置信度' }}</span><strong>{{ Math.round((turn.confidence || 0) * 100) }}%</strong></div><div class="turn-confidence-track"><span :style="{ width: `${Math.round((turn.confidence || 0) * 100)}%` }" /></div></template>
            <ul v-if="turn.detectedSummary?.length > 1" class="turn-targets"><li v-for="target in turn.detectedSummary" :key="target.id">{{ target.nameZh }} · {{ target.count }} 个</li></ul>
            <MarkdownContent :content="turn.reply" />
            <p v-if="turn.saveError" class="turn-warning">{{ turn.saveError }}</p>
            <p v-if="turn.reviewError" class="turn-warning" role="status">Dify 视觉复核未完成：{{ turn.reviewError }}</p>
            <p v-else-if="turn.reviewRequired && !turn.reviewResult" class="turn-warning">尚未取得视觉复核结论。</p>
            <p v-else-if="turn.yoloUsed && !turn.reviewRequired" class="turn-result-label">本次检测无需视觉复核。</p>
            <p v-if="isMeaninglessReview(turn.reviewResult)" class="turn-warning">确诊结果为“无意义”，无法生成诊断报告。</p>
          </div>
        </section>
        <div v-if="turn.mode === 'identify'" class="turn-identify-actions">
          <button v-if="turn.status === 'complete' && !isMeaninglessReview(turn.reviewResult)" type="button" class="turn-report-button" :disabled="turn.reportStatus === 'pending'" :aria-busy="turn.reportStatus === 'pending'" @click="$emit('report', turn.id)">{{ turn.reportStatus === 'pending' ? '正在生成报告…' : turn.report ? '查看诊断报告' : '生成诊断报告' }}<span v-if="turn.reportStatus !== 'pending'" aria-hidden="true">↗</span></button>
          <button type="button" class="turn-collapse-button" :aria-expanded="!turn.summaryCollapsed" :aria-controls="`turn-details-${turn.id}`" :aria-label="turn.summaryCollapsed ? '展开识别卡片' : '收起识别卡片'" @click="$emit('collapse', turn.id)">{{ turn.summaryCollapsed ? '展开' : '收起' }}<svg viewBox="0 0 24 24" aria-hidden="true" :class="{ 'is-flipped': turn.summaryCollapsed }"><path d="m6 14 6-6 6 6" /></svg></button>
        </div>
      </div>
      <section v-if="turn.mode === 'identify' && (turn.reportStatus === 'pending' || turn.report || turn.reportError) && !isMeaninglessReview(turn.reviewResult)" class="turn-assistant turn-report-message" :aria-busy="turn.reportStatus === 'pending'" aria-label="诊断报告">
        <header><img src="/vite.svg" alt="" width="25" height="25" /><span>LeafQuery</span></header>
        <ThoughtLine v-if="turn.reportStartedAt" :class="{ 'turn-pending': turn.reportStatus === 'pending' }" :working="turn.reportStatus === 'pending'" kind="report" :started-at="turn.reportStartedAt" :finished-at="turn.reportFinishedAt" :failed="turn.reportStatus === 'error'" />
        <div v-if="turn.reportError" class="turn-error" role="alert">{{ turn.reportError }}<button type="button" @click="$emit('report', turn.id)">重新生成报告</button></div>
        <div v-else-if="turn.report" class="turn-report"><h3>诊断报告</h3><MarkdownContent :content="turn.report" /></div>
      </section>
      <section v-if="turn.mode !== 'identify'" class="turn-assistant" :aria-busy="turn.status === 'pending'" aria-label="AI 回答">
        <header><img src="/vite.svg" alt="" width="25" height="25" /><span>LeafQuery</span></header>
        <ThoughtLine v-if="turn.startedAt" :class="{ 'turn-pending': turn.status === 'pending' }" :working="turn.status === 'pending'" kind="chat" :started-at="turn.startedAt" :finished-at="turn.finishedAt" :failed="turn.status === 'error'" />
        <div v-if="turn.status === 'error'" class="turn-error" role="alert">{{ turn.error }}<button v-if="turn === turns.at(-1)" type="button" @click="$emit('retry', turn.id)">重新发送</button></div>
        <div v-else-if="turn.status === 'complete'" class="turn-answer"><span class="turn-result-label">AI 回答</span><MarkdownContent :content="turn.reply" /></div>
      </section>
    </article>
  </div>
</template>

<style scoped>
.workspace-conversation { --turn-text: #263d35; --turn-muted: #7f9288; --turn-line: #e0e9e3; --turn-card: #fff; --turn-user: #eaf3ee; --turn-accent: #168669; color: var(--turn-text); }
.dark .workspace-conversation { --turn-text: #dce9e1; --turn-muted: #8fa89a; --turn-line: #304039; --turn-card: #15221d; --turn-user: #20342a; --turn-accent: #a5e4cd; }
.workspace-turn { padding: 8px 4px 28px; border-radius: 18px; transition: background .3s; animation: turn-arrive .28s ease-out; }
.workspace-turn.turn-is-identify { display: contents; }
.workspace-turn.is-selected { background: color-mix(in srgb, var(--turn-accent) 9%, transparent); }
.turn-user { width: fit-content; max-width: 90%; margin-left: auto; padding: 16px 19px; border: 1px solid var(--turn-line); border-radius: 20px 20px 5px 20px; background: var(--turn-user); }
.turn-user-meta { display: flex; justify-content: space-between; gap: 30px; color: var(--turn-muted); font-size: 11px; }
.turn-user p { margin: 9px 0 0; white-space: pre-wrap; overflow-wrap: anywhere; font-size: 16px; line-height: 1.8; }
.turn-images { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 13px; }
.turn-images img { width: 132px; height: 110px; max-width: 100%; object-fit: cover; border-radius: 11px; }
.turn-categories { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 11px; }
.turn-categories span { padding: 3px 10px; border: 1px solid var(--turn-line); border-radius: 999px; color: var(--turn-accent); font-size: 12px; }
.turn-user--identify { box-sizing: border-box; position: sticky; top: 0; z-index: 2; display: grid; grid-template-columns: 160px minmax(0, 1fr); grid-template-areas: 'image meta' 'image details' 'image action'; gap: 12px 24px; width: 100%; max-width: 100%; padding: 24px; border-radius: 0 0 20px 20px; background: var(--turn-card); }
.turn-identify-meta { grid-area: meta; display: flex; align-items: center; flex-wrap: nowrap; gap: 10px; min-width: 0; overflow-x: auto; white-space: nowrap; scrollbar-width: none; color: var(--turn-muted); font-size: 12px; }
.turn-identify-meta > span, .turn-identify-meta time { flex-shrink: 0; }
.turn-identify-title { color: var(--turn-text); font-weight: 550; }
.turn-identify-meta time { margin-left: auto; font-size: 11px; }
.turn-user--identify .turn-images { grid-area: image; align-self: start; margin: 0; }
.turn-user--identify .turn-images img { width: 160px; height: 160px; border-radius: 13px; }
.turn-user--identify .turn-categories { flex-shrink: 0; flex-wrap: nowrap; margin: 0; gap: 5px; }
.turn-user--identify .turn-categories span { padding: 2px 8px; font-size: 11px; }
.turn-identify-actions { grid-area: action; display: flex; align-items: center; justify-content: flex-end; gap: 8px; }
.turn-identify-actions .turn-report-button { margin-top: 0; }
.turn-assistant { margin-top: 24px; }
.turn-assistant header { display: flex; align-items: center; gap: 8px; font-size: 14px; font-weight: 650; }
.turn-assistant header img { width: 25px; height: 25px; }
.turn-assistant--identify { grid-area: details; min-width: 0; max-height: clamp(80px, calc(100dvh - 64px - var(--composer-height, 58px) - var(--workspace-bottom-gap, 80px) - 240px), 260px); margin: 0; padding: 0; overflow-y: auto; overscroll-behavior: contain; scrollbar-width: thin; border: 0; border-radius: 0; background: transparent; }
.turn-assistant--identify .turn-detection-card { margin: 0; padding: 0; border: 0; border-radius: 0; background: transparent; }
.turn-assistant--identify .turn-pending { padding-left: 0; }
.turn-assistant--identify .markdown-content { margin-top: 14px; font-size: 14px; color: var(--turn-muted); }
.turn-simulation-label { margin-left: 2px; padding: 2px 7px; border: 1px solid var(--turn-line); border-radius: 6px; color: var(--turn-muted); font-size: 10px; font-weight: 500; }
.turn-pending { display: flex; align-items: flex-start; gap: 0; padding: 0; color: var(--turn-muted); font-size: 14px; }
.turn-loading-dots { display: flex; gap: 3px; }
.turn-loading-dots i { width: 4px; height: 4px; border-radius: 50%; background: var(--turn-accent); animation: turn-pulse 1s infinite ease-in-out; }
.turn-loading-dots i:nth-child(2) { animation-delay: .15s; }
.turn-loading-dots i:nth-child(3) { animation-delay: .3s; }
.turn-detection-card, .turn-answer { margin: 14px 0 0 33px; }
.turn-detection-card { padding: 20px; border: 1px solid var(--turn-line); border-radius: 17px; background: var(--turn-card); }
.turn-result-label { color: var(--turn-muted); font-size: 12px; }
.turn-detection-card h2 { margin: 10px 0 19px; font-size: 20px; font-weight: 650; }
.turn-confidence { display: flex; justify-content: space-between; color: var(--turn-muted); font-size: 12px; }
.turn-confidence strong { color: var(--turn-accent); }
.turn-confidence-track { height: 5px; border-radius: 5px; margin-top: 9px; background: var(--turn-line); overflow: hidden; }
.turn-confidence-track span { display: block; width: 86%; height: 100%; border-radius: inherit; background: var(--turn-accent); }
.turn-detection-card p, .turn-answer p { margin: 15px 0 0; color: var(--turn-muted); font-size: 14px; line-height: 1.9; white-space: pre-wrap; overflow-wrap: anywhere; }
.turn-answer > .markdown-content { margin-top: 12px; color: var(--turn-text); font-size: 16px; }
.turn-report-button { display: inline-flex; align-items: center; gap: 13px; min-height: 35px; margin-top: 17px; padding: 7px 12px; border: 1px solid var(--turn-line); border-radius: 9px; color: var(--turn-accent); background: color-mix(in srgb, var(--turn-accent) 6%, var(--turn-card)); font: inherit; font-size: 13px; font-weight: 550; line-height: 1.5; cursor: pointer; transition: background .2s, opacity .2s; }
.turn-report-button:hover { background: color-mix(in srgb, var(--turn-accent) 12%, var(--turn-card)); }
.turn-report-button:disabled { opacity: .6; cursor: wait; }
.turn-report-button:focus-visible { outline: 2px solid var(--turn-accent); outline-offset: 3px; }
.turn-collapse-button { display: inline-flex; align-items: center; gap: 5px; padding: 7px 10px; min-height: 35px; border: 1px solid var(--turn-line); border-radius: 9px; background: transparent; color: var(--turn-muted); font: inherit; font-size: 13px; cursor: pointer; }
.turn-collapse-button:hover { background: var(--turn-user); color: var(--turn-text); }
.turn-collapse-button:focus-visible { outline: 2px solid var(--turn-accent); outline-offset: 3px; }
.turn-collapse-button svg { width: 14px; height: 14px; fill: none; stroke: currentColor; stroke-width: 1.7; stroke-linecap: round; stroke-linejoin: round; transition: transform .2s; }
.turn-collapse-button svg.is-flipped { transform: rotate(180deg); }
.turn-user--identify.is-summary-collapsed { grid-template-columns: 48px minmax(0, 1fr) auto; grid-template-areas: 'image meta action'; gap: 12px; padding: 12px 16px; }
.is-summary-collapsed .turn-images img { width: 48px; height: 48px; border-radius: 8px; }
.turn-error { padding: 14px 0; color: #bd5151; font-size: 14px; line-height: 1.8; }
.turn-error button { display: block; margin-top: 10px; padding: 5px 12px; border: 1px solid var(--turn-line); border-radius: 8px; color: var(--turn-accent); background: var(--turn-card); cursor: pointer; }
.turn-warning { color: #b87524 !important; }
.turn-targets { padding-left: 18px; font-size: 13px; color: var(--turn-muted); }
.turn-report-message { padding: 0 8px 28px; }
.turn-report { margin: 14px 0 0 33px; }
.turn-report h3 { display: flex; align-items: center; gap: 9px; margin: 0; font-size: 15px; font-weight: 600; }
.turn-report > .markdown-content { margin: 12px 0 0; overflow-wrap: anywhere; color: var(--turn-text); font-size: 15px; line-height: 1.9; }
@keyframes turn-pulse { 0%,80%,100% { opacity: .3; transform: translateY(0); } 40% { opacity: 1; transform: translateY(-3px); } }
@keyframes turn-arrive { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }
@media (max-width: 1280px) {
  .turn-user--identify { grid-template-columns: 112px minmax(0, 1fr); gap: 10px 16px; padding: 16px; }
  .turn-user--identify .turn-images img { width: 112px; height: 136px; }
  .turn-user--identify.is-summary-collapsed { grid-template-columns: 48px minmax(0, 1fr); grid-template-areas: 'image meta' 'image action'; gap: 8px 16px; }
  .turn-user--identify.is-summary-collapsed .turn-images img { width: 48px; height: 48px; }
}
@media (prefers-reduced-motion: reduce) { .workspace-conversation *, .workspace-turn { animation: none !important; transition: none !important; } }
</style>
