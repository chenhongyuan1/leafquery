<script setup>
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import PromptIcon from './PromptIcon.vue'

const props = defineProps({
  recognitionRecords: { type: Array, default: () => [] },
  conversationRecords: { type: Array, default: () => [] },
  activeId: { type: Number, default: null }
})
const emit = defineEmits(['select', 'toggle', 'new', 'remove'])
const isOpen = ref(true)
const toggleButton = ref(null)
const openBranches = ref({ recognition: true, conversation: true })
const motions = new Map()
const branches = computed(() => [
  { key: 'recognition', label: '识别记录', icon: 'scan', empty: '暂无识别记录', hint: '识别过的图片会收录在这里', records: props.recognitionRecords },
  { key: 'conversation', label: '对话记录', icon: 'chat', empty: '暂无对话记录', hint: '在这里找回之前的询问', records: props.conversationRecords }
])

function recognitionImage(record) {
  return record.turns?.find(turn => turn.mode === 'identify')?.images?.[0]?.url || ''
}

const isProcessing = record => record.deleting || record.turns?.some(turn => turn.running || turn.status === 'pending' || turn.reportStatus === 'pending')

async function togglePanel() {
  isOpen.value = !isOpen.value
  emit('toggle', isOpen.value)
  await nextTick()
  toggleButton.value?.focus({ preventScroll: true })
}

function animateHeight(element, opening, done) {
  const previous = motions.get(element)
  const fromHeight = previous ? element.getBoundingClientRect().height : opening ? 0 : element.getBoundingClientRect().height
  const fromOpacity = previous ? getComputedStyle(element).opacity : opening ? 0 : 1
  previous?.cancel()
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) { motions.delete(element); done(); return }
  const toHeight = opening ? element.scrollHeight : 0
  const motion = element.animate([
    { height: `${fromHeight}px`, opacity: fromOpacity },
    { height: `${toHeight}px`, opacity: opening ? 1 : 0 }
  ], { duration: 280, easing: 'cubic-bezier(0.22, 1, 0.36, 1)', fill: 'both' })
  motions.set(element, motion)
  motion.finished.then(() => {
    if (motions.get(element) !== motion) return
    motions.delete(element)
    done()
    motion.cancel()
  }).catch(() => {})
}
const enter = (element, done) => animateHeight(element, true, done)
const leave = (element, done) => animateHeight(element, false, done)
onBeforeUnmount(() => { motions.forEach(motion => motion.cancel()); motions.clear() })
</script>

<template>
  <aside class="workspace-records" :class="{ 'is-collapsed': !isOpen }" aria-label="工作台记录">
    <header class="records-header">
      <Transition name="records-label"><div v-if="isOpen" class="records-title"><PromptIcon name="history" /><h2>工作台记录</h2></div></Transition>
      <button ref="toggleButton" type="button" class="records-toggle" :aria-label="isOpen ? '收起记录侧栏' : '展开记录侧栏'" :aria-expanded="isOpen" aria-controls="workspace-records-content" @click="togglePanel">
        <PromptIcon :name="isOpen ? 'panel' : 'history'" /><span v-if="!isOpen">记录</span>
      </button>
    </header>
    <Transition :css="false" @enter="enter" @leave="leave">
      <div v-if="isOpen" id="workspace-records-content" class="records-content">
        <section v-for="branch in branches" :key="branch.key" class="records-branch" :aria-label="branch.label">
          <div class="records-branch-heading">
          <button type="button" class="records-branch-toggle" :aria-expanded="openBranches[branch.key]" :aria-controls="`workspace-records-${branch.key}`" @click="openBranches[branch.key] = !openBranches[branch.key]">
            <PromptIcon :name="branch.icon" /><span>{{ branch.label }}</span><span class="records-count">{{ branch.records.length }}</span><PromptIcon name="chevron" class="records-chevron" :class="{ 'is-closed': !openBranches[branch.key] }" />
          </button>
          <button type="button" class="records-new" :aria-label="branch.key === 'recognition' ? '新建识别' : '新建对话'" :title="branch.key === 'recognition' ? '新建识别' : '新建对话'" @click="emit('new', branch.key)"><PromptIcon name="plus" /></button>
          </div>
          <Transition :css="false" @enter="enter" @leave="leave">
            <div v-if="openBranches[branch.key]" :id="`workspace-records-${branch.key}`" class="records-branch-content">
              <div v-if="!branch.records.length" class="records-empty"><span>{{ branch.empty }}</span><p>{{ branch.hint }}</p></div>
              <ul v-else class="records-list">
                <li v-for="record in branch.records" :key="record.id">
                  <button type="button" :class="{ 'is-active': activeId === record.id }" :aria-current="activeId === record.id ? 'true' : undefined" @click="emit('select', { type: branch.key, record })">
                    <span v-if="branch.key === 'recognition'" class="records-thumbnail" aria-hidden="true">
                      <img v-if="recognitionImage(record)" :src="recognitionImage(record)" alt="" width="40" height="40" loading="lazy" decoding="async" />
                      <PromptIcon v-else name="scan" />
                    </span>
                    <span class="records-item-copy"><span class="records-item-title">{{ record.title || record.pestName || '未命名记录' }}</span><small v-if="record.time">{{ record.time }}</small></span>
                  </button>
                  <button type="button" class="records-delete" :disabled="isProcessing(record)" :aria-label="`删除${branch.key === 'recognition' ? '识别' : '对话'}记录：${record.title || '未命名记录'}`" :title="isProcessing(record) ? '正在处理，完成后可删除' : '删除此条记录'" @click="emit('remove', record)"><PromptIcon name="trash" /></button>
                </li>
              </ul>
            </div>
          </Transition>
        </section>
      </div>
    </Transition>
  </aside>
</template>

<style scoped>
.workspace-records {
  --records-open-width: 300px;
  --records-card: #fff; --records-text: #34443e; --records-muted: #8a9690;
  --records-border: #e6ebe8; --records-hover: #f4f7f5; --records-accent: #168669;
  position: absolute; top: 24px; right: 24px; z-index: 3;
  display: flex; flex-direction: column; width: var(--records-open-width);
  max-height: max(120px, min(440px, calc(100% - 420px)));
  overflow: hidden; border: 1px solid var(--records-border); border-radius: 22px;
  background: var(--records-card); color: var(--records-text);
  box-shadow: 0 7px 26px -18px #142e2933;
  transition: width .28s cubic-bezier(.22,1,.36,1), border-radius .28s;
}
.dark .workspace-records { --records-card: #1b2525; --records-text: #e1e9e5; --records-muted: #94a39b; --records-border: #34413e; --records-hover: #25332e; --records-accent: #a5e4cd; }
.workspace-records button { font: inherit; cursor: pointer; }
.workspace-records button:focus-visible { outline: 2px solid var(--records-accent); outline-offset: -3px; }
.workspace-records svg { display: block; width: 18px; height: 18px; flex-shrink: 0; }
.records-header { position: relative; height: 56px; flex-shrink: 0; transition: height .28s cubic-bezier(.22,1,.36,1); }
.records-title { position: absolute; top: 19px; left: 20px; display: flex; align-items: center; gap: 10px; white-space: nowrap; }
.records-title > svg { color: var(--records-muted); }
.records-title h2 { margin: 0; font-size: 15px; font-weight: 600; letter-spacing: .02em; }
.records-header .records-toggle { position: absolute; right: 14px; top: 12px; display: flex; align-items: center; justify-content: center; gap: 8px; width: 32px; height: 32px; padding: 0 8px; border: 0; border-radius: 10px; background: transparent; color: var(--records-muted); font-size: 14px; white-space: nowrap; transition: width .28s, right .28s, top .28s, background .18s, color .18s; }
.records-toggle:hover { background: var(--records-hover); color: var(--records-accent); }
.records-content { box-sizing: border-box; width: calc(var(--records-open-width) - 2px); min-height: 0; padding: 0 15px 8px; overflow-y: auto; overflow-x: hidden; scrollbar-gutter: stable; scrollbar-width: thin; scrollbar-color: var(--records-border) transparent; }
.records-branch { border-top: 1px solid var(--records-border); }
.records-branch-heading { display: flex; align-items: center; gap: 3px; }
.records-branch-toggle { display: flex; flex: 1; min-width: 0; align-items: center; gap: 9px; height: 48px; padding: 0 4px; border: 0; border-radius: 10px; color: var(--records-text); background: transparent; text-align: left; }
.records-new { display: grid; place-items: center; flex-shrink: 0; width: 26px; height: 26px; padding: 0; border: 0; border-radius: 8px; color: var(--records-muted); background: transparent; }
.records-new:hover { color: var(--records-accent); background: var(--records-hover); }
.records-new svg { width: 14px; height: 14px; }
.records-branch-toggle:hover { color: var(--records-accent); }
.records-branch-toggle > svg:first-child { width: 16px; height: 16px; color: var(--records-muted); }
.records-branch-toggle > span:first-of-type { font-size: 14px; font-weight: 550; }
.records-count { display: grid; place-items: center; margin-left: auto; min-width: 22px; height: 20px; padding: 0 5px; border-radius: 7px; background: var(--records-hover); color: var(--records-muted); font-size: 11px; }
.records-chevron { width: 14px !important; height: 14px !important; color: var(--records-muted); transition: transform .22s; }
.records-chevron.is-closed { transform: rotate(-90deg); }
.records-branch-content { box-sizing: border-box; overflow: hidden; }
.records-empty { padding: 8px 4px 24px 29px; color: var(--records-muted); font-size: 13px; }
.records-empty p { margin: 7px 0 0; color: var(--records-muted); font-size: 12px; line-height: 1.6; opacity: .8; }
.records-list { list-style: none; padding: 0 0 8px; margin: 0; }
.records-list li { position: relative; }
.records-list button { display: flex; align-items: center; gap: 10px; width: 100%; padding: 10px 12px; border: 0; border-radius: 10px; background: transparent; color: var(--records-text); text-align: left; }
.records-list button:hover { background: var(--records-hover); }
.records-list button.is-active { background: var(--records-hover); color: var(--records-accent); }
.records-thumbnail { display: grid; place-items: center; flex-shrink: 0; width: 40px; height: 40px; overflow: hidden; border-radius: 9px; background: var(--records-border); color: var(--records-muted); }
.records-thumbnail img { display: block; width: 100%; height: 100%; object-fit: cover; }
.records-item-copy { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 5px; }
.records-item-title { overflow: hidden; white-space: nowrap; text-overflow: ellipsis; font-size: 13px; }
.records-list small { color: var(--records-muted); font-size: 11px; }
.records-list button:not(.records-delete) { padding-right: 42px; }
.records-list .records-delete { position: absolute; right: 6px; top: 50%; transform: translateY(-50%); justify-content: center; width: 28px; height: 28px; padding: 0; border-radius: 7px; opacity: 0; color: var(--records-muted); transition: opacity .15s, color .15s, background .15s; }
.records-list li:hover .records-delete, .records-list li:focus-within .records-delete { opacity: 1; }
.records-list .records-delete:hover { color: #d15b5b; background: #d15b5b15; }
.records-list .records-delete:disabled { opacity: .3; cursor: wait; }
.records-delete svg { width: 15px; height: 15px; }
@media (hover: none) { .records-list .records-delete { opacity: 1; } }
.workspace-records.is-collapsed { width: 86px; border-radius: 14px; }
.is-collapsed .records-header { height: 42px; }
.is-collapsed .records-toggle { top: 5px; right: 6px; width: 72px; color: var(--records-text); }
.records-label-enter-active, .records-label-leave-active { transition: opacity .16s, transform .22s; }
.records-label-enter-from, .records-label-leave-to { opacity: 0; transform: translateX(8px); }
@media (max-width: 1280px) { .workspace-records { --records-open-width: 270px; top: 18px; right: 18px; } }
@media (prefers-reduced-motion: reduce) { .workspace-records, .workspace-records * { animation: none !important; transition: none !important; } }
</style>
